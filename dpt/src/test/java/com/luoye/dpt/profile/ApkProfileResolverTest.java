package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.apk.ApkProfileProtectionContext;
import com.luoye.dpt.profile.apk.ApkProfileResolver;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;

import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class ApkProfileResolverTest {
    private static final String APPLICATION_ID = "com.example.protected";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesManifestApplicationIdBusinessAbisAndExactActiveBundle() throws Exception {
        Fixture fixture = fixture(true);
        Path unpackedApk = unpackedApk(APPLICATION_ID);
        writeBusinessSo(unpackedApk, "arm64-v8a", "libbusiness.so");
        writeBusinessSo(unpackedApk, "x86_64", "libdesktop.so");

        ApkProfileProtectionContext context = new ApkProfileResolver(
                fixture.profilesRoot, fixture.activeProfilesRoot).resolve(unpackedApk);

        Assert.assertEquals(APPLICATION_ID, context.getApplicationId());
        Assert.assertEquals(fixture.profileId, context.getProfileId());
        Assert.assertEquals(fixture.revisionId, context.getRevisionId());
        Assert.assertEquals(new LinkedHashSet<>(Arrays.asList("arm64-v8a", "x86_64")),
                context.getBusinessNativeAbis());
        Assert.assertEquals(fixture.profileId, context.getContract().getProfileId());
        Assert.assertTrue(Files.isRegularFile(context.getShellDexPath()));
        Assert.assertTrue(Files.isRegularFile(context.getArm64ShellSoPath()));
    }

    @Test
    public void rejectsUnregisteredApplicationWithoutCreatingProfile() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        Path unpackedApk = unpackedApk("com.example.unregistered");

        try {
            new ApkProfileResolver(profilesRoot, activeProfilesRoot).resolve(unpackedApk);
            Assert.fail("Expected unregistered Profile failure");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.PROFILE_NOT_FOUND, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains("com.example.unregistered"));
        }

        Assert.assertFalse(Files.exists(profilesRoot.resolve("index.json")));
        Assert.assertEquals(0L, Files.list(activeProfilesRoot).count());
    }

    @Test
    public void rejectsRegisteredProfileWithoutActiveRevision() throws Exception {
        Fixture fixture = fixture(false);
        Path unpackedApk = unpackedApk(APPLICATION_ID);

        try {
            new ApkProfileResolver(fixture.profilesRoot, fixture.activeProfilesRoot)
                    .resolve(unpackedApk);
            Assert.fail("Expected missing active Revision failure");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.ACTIVE_REVISION_MISSING,
                    expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(fixture.profileId));
        }
    }

    @Test
    public void allowsApkWithoutBusinessNativeLibraries() throws Exception {
        Fixture fixture = fixture(true);
        Path unpackedApk = unpackedApk(APPLICATION_ID);

        ApkProfileProtectionContext context = new ApkProfileResolver(
                fixture.profilesRoot, fixture.activeProfilesRoot).resolve(unpackedApk);

        Assert.assertTrue(context.getBusinessNativeAbis().isEmpty());
    }

    @Test
    public void rejectsBusinessNativeLibrariesWithoutArm64BeforePackaging() throws Exception {
        Fixture fixture = fixture(true);
        Path unpackedApk = unpackedApk(APPLICATION_ID);
        writeBusinessSo(unpackedApk, "armeabi-v7a", "liblegacy.so");
        writeBusinessSo(unpackedApk, "x86_64", "libdesktop.so");

        try {
            new ApkProfileResolver(fixture.profilesRoot, fixture.activeProfilesRoot)
                    .resolve(unpackedApk);
            Assert.fail("Expected arm64 incompatibility");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.APK_ABI_INCOMPATIBLE,
                    expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("arm64-v8a"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("x86_64"));
        }
    }

    @Test
    public void reportsStableTargetMismatchCode() throws Exception {
        Fixture fixture = fixture(true);
        ProfileIdentityStore identities = new ProfileIdentityStore(fixture.profilesRoot);
        Path identityPath = identities.getIdentityLockPath(fixture.profileId);
        ProfileIdentityLock identity = JSON.parseObject(Files.readString(identityPath),
                ProfileIdentityLock.class);
        identity.setApplicationId("com.example.other");
        byte[] content = JSON.toJSONString(identity).getBytes(StandardCharsets.UTF_8);
        makeWritable(identityPath);
        Files.write(identityPath, content);
        Path hashPath = identities.getIdentityHashPath(fixture.profileId);
        makeWritable(hashPath);
        Files.writeString(hashPath, MessageDigestUtils.sha256(content) + "\n");

        assertResolveError(fixture, ProfileErrorCode.APK_PROFILE_TARGET_MISMATCH,
                "目标应用不匹配");
    }

    @Test
    public void reportsStableProtocolIncompatibleCode() throws Exception {
        Fixture fixture = fixture(true);
        Path contractPath = fixture.revisionRoot.resolve("profile.json");
        makeWritable(contractPath);
        String contract = Files.readString(contractPath);
        Files.writeString(contractPath, contract.replace("\"protocolVersion\":1",
                "\"protocolVersion\":999"));

        assertResolveError(fixture, ProfileErrorCode.APK_PROFILE_PROTOCOL_INCOMPATIBLE,
                "协议或生成器版本不兼容");
    }

    @Test
    public void reportsStableBundleIntegrityCodeForHashFailure() throws Exception {
        Fixture fixture = fixture(true);
        Path soPath = Files.list(fixture.revisionRoot.resolve("shell-files/libs/arm64"))
                .findFirst().orElseThrow();
        makeWritable(soPath);
        byte[] content = Files.readAllBytes(soPath);
        content[content.length - 1] ^= 1;
        Files.write(soPath, content);

        assertResolveError(fixture, ProfileErrorCode.APK_BUNDLE_INTEGRITY_FAILED,
                "Bundle 完整性校验失败");
    }

    private void assertResolveError(Fixture fixture, ProfileErrorCode errorCode,
                                    String message) throws Exception {
        try {
            new ApkProfileResolver(fixture.profilesRoot, fixture.activeProfilesRoot)
                    .resolve(unpackedApk(APPLICATION_ID));
            Assert.fail("Expected strict APK Profile failure");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(errorCode, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private static void makeWritable(Path path) {
        Assert.assertTrue("Cannot make test artifact writable: " + path,
                path.toFile().setWritable(true, true));
    }

    private Fixture fixture(boolean publish) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-" + System.nanoTime()).toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active-" + System.nanoTime()).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        if (!publish) {
            return new Fixture(profilesRoot, activeProfilesRoot, created.getProfileId(), null,
                    null);
        }

        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeProfilesRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, revision,
                temporaryFolder.newFolder("artifacts-" + System.nanoTime()).toPath());
        new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot).publishCandidate(
                APPLICATION_ID, created.getProfileId(), revision.getRevisionId());
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        revisions.markVerified(created.getProfileId(), revision.getRevisionId());
        new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot, revisions).activate(
                created.getProfileId(), revision.getRevisionId());
        Path revisionRoot = activeProfilesRoot.resolve(created.getProfileId())
                .resolve("revisions").resolve(revision.getRevisionId());
        return new Fixture(profilesRoot, activeProfilesRoot, created.getProfileId(),
                revision.getRevisionId(), revisionRoot);
    }

    private Path unpackedApk(String applicationId) throws Exception {
        Path root = temporaryFolder.newFolder("unpacked-" + System.nanoTime()).toPath();
        AxmlWriter writer = new AxmlWriter();
        writer.ns("android", "http://schemas.android.com/apk/res/android", 1);
        NodeVisitor manifest = writer.child(null, "manifest");
        manifest.attr(null, "package", -1, NodeVisitor.TYPE_STRING, applicationId);
        NodeVisitor application = manifest.child(null, "application");
        application.end();
        manifest.end();
        writer.end();
        Files.write(root.resolve("AndroidManifest.xml"), writer.toByteArray());
        return root;
    }

    private static void writeBusinessSo(Path root, String abi, String name) throws Exception {
        Path directory = root.resolve("lib").resolve(abi);
        Files.createDirectories(directory);
        Files.write(directory.resolve(name), "business".getBytes(StandardCharsets.UTF_8));
    }

    private static final class Fixture {
        private final Path profilesRoot;
        private final Path activeProfilesRoot;
        private final String profileId;
        private final String revisionId;
        private final Path revisionRoot;

        private Fixture(Path profilesRoot, Path activeProfilesRoot, String profileId,
                        String revisionId, Path revisionRoot) {
            this.profilesRoot = profilesRoot;
            this.activeProfilesRoot = activeProfilesRoot;
            this.profileId = profileId;
            this.revisionId = revisionId;
            this.revisionRoot = revisionRoot;
        }
    }
}
