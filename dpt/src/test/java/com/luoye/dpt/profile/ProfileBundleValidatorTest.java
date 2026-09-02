package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;
import com.luoye.dpt.profile.storage.ProfileBundleValidator;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileBundleValidatorTest {
    private static final String APPLICATION_ID = "com.example.bundlevalidator";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validatesCompleteCandidateWithoutChangingAnyInput() throws Exception {
        Fixture fixture = fixture();
        Map<String, byte[]> before = snapshot(fixture.bundle.getRoot());

        ProfileBundle validated = new ProfileBundleValidator(fixture.profilesRoot)
                .validateCandidate(APPLICATION_ID, fixture.identity.getProfileId(),
                        fixture.revision.getRevisionId());

        Assert.assertEquals(fixture.bundle.getRoot(), validated.getRoot());
        Assert.assertEquals(fixture.identity.getProfileId(), validated.getProfileId());
        assertSnapshotEquals(before, snapshot(fixture.bundle.getRoot()));
    }

    @Test
    public void rejectsContractOrRevisionVersionAndIdentityMismatch() throws Exception {
        Fixture fixture = fixture();
        JSONObject contract = JSON.parseObject(Files.readString(
                fixture.bundle.getProfileContractPath()));
        contract.put("generatorVersion", "unsupported-generator");
        Files.writeString(fixture.bundle.getProfileContractPath(), contract.toJSONString());
        refreshHash(fixture.bundle, "profile.json");
        assertValidationFails(fixture, "generatorVersion");

        fixture = fixture();
        JSONObject revision = JSON.parseObject(Files.readString(
                fixture.bundle.getRevisionManifestPath()));
        revision.put("applicationId", "com.example.other");
        Files.writeString(fixture.bundle.getRevisionManifestPath(), revision.toJSONString());
        refreshHash(fixture.bundle, "revision.json");
        assertValidationFails(fixture, "applicationId");
    }

    @Test
    public void rejectsMissingExtraOrHashModifiedArtifact() throws Exception {
        Fixture fixture = fixture();
        Files.delete(fixture.bundle.getShellDexPath());
        assertValidationFails(fixture, "文件集合");

        fixture = fixture();
        Files.writeString(fixture.bundle.getRoot().resolve("shell-files/dex/classes2.dex"),
                "unexpected", StandardCharsets.UTF_8);
        assertValidationFails(fixture, "文件集合");

        fixture = fixture();
        Files.write(fixture.bundle.getArm64SoPath(), new byte[]{1, 2, 3});
        assertValidationFails(fixture, "SHA-256");
    }

    @Test
    public void rejectsDexWithoutManifestEntryOrWithInvalidJniDeclaration() throws Exception {
        Fixture fixture = fixture();
        writeShellDex(fixture.bundle.getShellDexPath(), fixture.identity, false, false);
        refreshHash(fixture.bundle, "shell-files/dex/classes.dex");
        assertValidationFails(fixture, "代理 Application");

        fixture = fixture();
        writeShellDex(fixture.bundle.getShellDexPath(), fixture.identity, true, true);
        refreshHash(fixture.bundle, "shell-files/dex/classes.dex");
        assertValidationFails(fixture, "JNI");
    }

    @Test
    public void rejectsNonArm64SoOrSoWithoutNativeContract() throws Exception {
        Fixture fixture = fixture();
        byte[] wrongMachine = validArm64So(fixture.identity);
        wrongMachine[18] = 0x28;
        wrongMachine[19] = 0;
        Files.write(fixture.bundle.getArm64SoPath(), wrongMachine);
        refreshHash(fixture.bundle, "shell-files/libs/arm64/" + fixture.identity.getSoName());
        assertValidationFails(fixture, "arm64");

        fixture = fixture();
        byte[] missingContract = Arrays.copyOf(validArm64So(fixture.identity), 64);
        Files.write(fixture.bundle.getArm64SoPath(), missingContract);
        refreshHash(fixture.bundle, "shell-files/libs/arm64/" + fixture.identity.getSoName());
        assertValidationFails(fixture, "Native JNI");
    }

    @Test
    public void rejectsBuildKeyMismatchEvenWhenHashManifestWasUpdated() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.bundle.getBuildKeyPath(), "0123456789abcdef\n");
        refreshHash(fixture.bundle, "shell-files/build-key");

        assertValidationFails(fixture, "build-key");
    }

    private Fixture fixture() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-" + System.nanoTime()).toPath();
        Path activeRoot = temporaryFolder.newFolder("active-" + System.nanoTime()).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_ID);
        ProfileIdentityLock identity = new ProfileIdentityStore(profilesRoot).loadExisting(
                APPLICATION_ID, created.getProfileId());
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);

        Path inputRoot = temporaryFolder.newFolder("bundle-input-" + System.nanoTime()).toPath();
        Path dex = inputRoot.resolve("classes.dex");
        writeShellDex(dex, identity, true, false);
        Path nativeDirectory = inputRoot.resolve("native");
        Files.createDirectories(nativeDirectory);
        Files.write(nativeDirectory.resolve(identity.getSoName()), validArm64So(identity));

        ProfileBundle bundle = new ProfileBundleAssembler(profilesRoot).assemble(
                identity, revision, dex, nativeDirectory);
        return new Fixture(profilesRoot, identity, revision, bundle);
    }

    private static void writeShellDex(Path output, ProfileIdentityLock identity,
                                      boolean includeApplication, boolean wrongJni)
            throws IOException {
        List<ImmutableClassDef> classes = new ArrayList<>();
        if (includeApplication) {
            classes.add(classDef(identity.getProxyApplicationClassName(),
                    Collections.emptyList()));
        }
        classes.add(classDef(identity.getProxyComponentFactoryClassName(),
                Collections.emptyList()));

        List<ImmutableMethod> nativeMethods = new ArrayList<>();
        for (int index = 0; index < identity.getJniMethods().size(); index++) {
            JniMethodMapping mapping = identity.getJniMethods().get(index);
            String signature = wrongJni && index == 0 ? "()V" : mapping.getJvmSignature();
            nativeMethods.add(nativeMethod(identity.getJniBridgeClassName(),
                    mapping.getMethodName(), signature));
        }
        classes.add(classDef(identity.getJniBridgeClassName(), nativeMethods));
        DexFileFactory.writeDexFile(output.toString(),
                new ImmutableDexFile(Opcodes.getDefault(), classes));
    }

    private static ImmutableClassDef classDef(String className,
                                               List<ImmutableMethod> methods) {
        return new ImmutableClassDef(descriptor(className), AccessFlags.PUBLIC.getValue(),
                "Ljava/lang/Object;", Collections.emptyList(), null, null,
                Collections.emptyList(), methods);
    }

    private static ImmutableMethod nativeMethod(String className, String methodName,
                                                String signature) {
        int close = signature.indexOf(')');
        List<ImmutableMethodParameter> parameters = parseParameters(
                signature.substring(1, close));
        return new ImmutableMethod(descriptor(className), methodName, parameters,
                signature.substring(close + 1), AccessFlags.PUBLIC.getValue()
                | AccessFlags.STATIC.getValue() | AccessFlags.NATIVE.getValue(),
                null, null, null);
    }

    private static List<ImmutableMethodParameter> parseParameters(String descriptor) {
        List<ImmutableMethodParameter> parameters = new ArrayList<>();
        int offset = 0;
        while (offset < descriptor.length()) {
            int end = typeEnd(descriptor, offset);
            parameters.add(new ImmutableMethodParameter(descriptor.substring(offset, end),
                    Collections.emptySet(), null));
            offset = end;
        }
        return parameters;
    }

    private static int typeEnd(String descriptor, int offset) {
        int current = offset;
        while (descriptor.charAt(current) == '[') {
            current++;
        }
        if (descriptor.charAt(current) == 'L') {
            return descriptor.indexOf(';', current) + 1;
        }
        return current + 1;
    }

    private static byte[] validArm64So(ProfileIdentityLock identity) {
        byte[] header = new byte[64];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = 2;
        header[5] = 1;
        header[6] = 1;
        header[16] = 3;
        header[18] = (byte) 0xb7;
        header[19] = 0;

        StringBuilder contract = new StringBuilder();
        contract.append(identity.getJniBridgeClassName().replace('.', '/')).append('\0');
        for (JniMethodMapping mapping : identity.getJniMethods()) {
            contract.append(mapping.getMethodName()).append('\0')
                    .append(mapping.getJvmSignature()).append('\0');
        }
        byte[] strings = contract.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(header, header.length + strings.length);
        System.arraycopy(strings, 0, result, header.length, strings.length);
        return result;
    }

    private static String descriptor(String className) {
        return "L" + className.replace('.', '/') + ";";
    }

    private static void refreshHash(ProfileBundle bundle, String relativePath) throws IOException {
        ArtifactHashManifest manifest = JSON.parseObject(
                Files.readString(bundle.getArtifactHashManifestPath()),
                ArtifactHashManifest.class);
        manifest.getSha256().put(relativePath, MessageDigestUtils.sha256(
                Files.readAllBytes(bundle.getRoot().resolve(relativePath))));
        Files.writeString(bundle.getArtifactHashManifestPath(), JSON.toJSONString(manifest));
    }

    private static void assertValidationFails(Fixture fixture, String expectedMessage)
            throws Exception {
        try {
            new ProfileBundleValidator(fixture.profilesRoot).validateCandidate(
                    APPLICATION_ID, fixture.identity.getProfileId(),
                    fixture.revision.getRevisionId());
            Assert.fail("Expected strict Bundle validation failure");
        } catch (IOException | IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains(expectedMessage));
        }
    }

    private static Map<String, byte[]> snapshot(Path root) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .sorted().collect(Collectors.toList())) {
                result.put(root.relativize(path).toString(), Files.readAllBytes(path));
            }
        }
        return result;
    }

    private static void assertSnapshotEquals(Map<String, byte[]> expected,
                                             Map<String, byte[]> actual) {
        Assert.assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet()) {
            Assert.assertArrayEquals(path, expected.get(path), actual.get(path));
        }
    }

    private static final class Fixture {
        private final Path profilesRoot;
        private final ProfileIdentityLock identity;
        private final ProfileRevisionManifest revision;
        private final ProfileBundle bundle;

        private Fixture(Path profilesRoot, ProfileIdentityLock identity,
                        ProfileRevisionManifest revision, ProfileBundle bundle) {
            this.profilesRoot = profilesRoot;
            this.identity = identity;
            this.revision = revision;
            this.bundle = bundle;
        }
    }
}
