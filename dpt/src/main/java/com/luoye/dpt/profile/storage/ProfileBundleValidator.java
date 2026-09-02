package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Validates one candidate or published Bundle without modifying it. */
public final class ProfileBundleValidator {
    private final Path profilesRoot;
    private final ProfileRegistry registry;
    private final ProfileIdentityStore identityStore;
    private final ProfileRevisionStore revisionStore;

    public ProfileBundleValidator(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        this.profilesRoot = profilesRoot.toAbsolutePath().normalize();
        registry = new ProfileRegistry(this.profilesRoot);
        identityStore = new ProfileIdentityStore(this.profilesRoot);
        revisionStore = new ProfileRevisionStore(this.profilesRoot);
    }

    public ProfileBundle validateCandidate(String applicationId, String profileId,
                                           String revisionId) throws IOException {
        Path bundleRoot = profilesRoot.resolve(ProfileBundleAssembler.CANDIDATES_DIRECTORY_NAME)
                .resolve(profileId).resolve(revisionId)
                .resolve(ProfileBundleAssembler.BUNDLE_DIRECTORY_NAME);
        return validate(bundleRoot, applicationId, profileId, revisionId, true);
    }

    public ProfileBundle validate(Path bundleRoot, String applicationId, String profileId,
                                  String revisionId) throws IOException {
        return validate(bundleRoot, applicationId, profileId, revisionId, false);
    }

    private ProfileBundle validate(Path bundleRoot, String applicationId, String profileId,
                                   String revisionId, boolean requireCandidate) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        String registeredProfileId = registry.findProfileId(applicationId)
                .orElseThrow(() -> new IOException("Profile 未注册：applicationId="
                        + applicationId));
        if (!profileId.equals(registeredProfileId)) {
            throw new IOException("Profile 注册表不匹配：applicationId=" + applicationId
                    + "，期望 profileId=" + registeredProfileId + "，实际=" + profileId);
        }

        ProfileIdentityLock identity = identityStore.loadExisting(applicationId, profileId);
        ProfileRevisionManifest persistedRevision = revisionStore.load(profileId, revisionId);
        if (requireCandidate && persistedRevision.getStatus() != RevisionStatus.CANDIDATE) {
            throw new IOException("Bundle 本地校验只接受 CANDIDATE Revision：revisionId="
                    + revisionId + "，当前状态=" + persistedRevision.getStatus());
        }

        Path root = requireBundleRoot(bundleRoot);
        Set<String> expectedFiles = expectedFilePaths(identity.getSoName());
        validateTree(root, expectedFiles, expectedDirectoryPaths());

        ProfileRevisionManifest bundledRevision = readRevision(root.resolve(
                ProfileBundleAssembler.REVISION_MANIFEST_FILE_NAME));
        ProfileContract contract = readContract(root.resolve(
                ProfileBundleAssembler.PROFILE_CONTRACT_FILE_NAME));
        ArtifactHashManifest hashes = readHashes(root.resolve(
                ProfileBundleAssembler.ARTIFACT_HASH_MANIFEST_FILE_NAME));

        validateHashes(root, hashes, profileId, revisionId, expectedFiles);
        validateRevision(bundledRevision, persistedRevision, applicationId, profileId, revisionId);
        validateContract(contract, identity, bundledRevision);
        validateBuildKey(root.resolve(ProfileBundleAssembler.BUILD_KEY_RELATIVE_PATH), identity);
        validateDex(root.resolve(ProfileBundleAssembler.SHELL_DEX_RELATIVE_PATH), contract);
        validateArm64So(root.resolve(ProfileBundleAssembler.ARM64_SO_DIRECTORY)
                .resolve(contract.getSoName()), contract);
        return new ProfileBundle(root, contract, hashes);
    }

    private static Path requireBundleRoot(Path bundleRoot) throws IOException {
        if (bundleRoot == null) {
            throw new IOException("Profile Bundle 路径不能为空");
        }
        Path root = bundleRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Profile Bundle 必须是普通目录：" + root);
        }
        return root;
    }

    private static void validateTree(Path root, Set<String> expectedFiles,
                                     Set<String> expectedDirectories) throws IOException {
        Set<String> actualFiles = new LinkedHashSet<>();
        Set<String> actualDirectories = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList())) {
                if (path.equals(root)) {
                    continue;
                }
                String relative = unixPath(root.relativize(path));
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Profile Bundle 不得包含符号链接：" + relative);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    actualDirectories.add(relative);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    actualFiles.add(relative);
                    if (Files.size(path) == 0L) {
                        throw new IOException("Profile Bundle 文件不能为空：" + relative);
                    }
                } else {
                    throw new IOException("Profile Bundle 包含非常规文件：" + relative);
                }
            }
        }
        if (!expectedFiles.equals(actualFiles) || !expectedDirectories.equals(actualDirectories)) {
            throw new IOException("Profile Bundle 文件集合不完整或包含额外内容：期望文件="
                    + expectedFiles + "，实际文件=" + actualFiles + "，期望目录="
                    + expectedDirectories + "，实际目录=" + actualDirectories);
        }
    }

    private static ProfileRevisionManifest readRevision(Path path) throws IOException {
        try {
            ProfileRevisionManifest revision = JSON.parseObject(Files.readString(path),
                    ProfileRevisionManifest.class);
            ProfileModelValidator.validate(revision);
            return revision;
        } catch (RuntimeException exception) {
            throw new IOException("revision.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ProfileContract readContract(Path path) throws IOException {
        try {
            return ProfileContractCodec.deserialize(Files.readAllBytes(path));
        } catch (RuntimeException exception) {
            throw new IOException("profile.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ArtifactHashManifest readHashes(Path path) throws IOException {
        try {
            ArtifactHashManifest hashes = JSON.parseObject(Files.readString(path),
                    ArtifactHashManifest.class);
            ProfileModelValidator.validate(hashes);
            return hashes;
        } catch (RuntimeException exception) {
            throw new IOException("artifact-sha256.json 无效：" + exception.getMessage(),
                    exception);
        }
    }

    private static void validateHashes(Path root, ArtifactHashManifest hashes, String profileId,
                                       String revisionId, Set<String> expectedFiles)
            throws IOException {
        requireEqual("artifact-sha256.json.profileId", profileId, hashes.getProfileId());
        requireEqual("artifact-sha256.json.revisionId", revisionId, hashes.getRevisionId());
        Set<String> expectedHashedFiles = new LinkedHashSet<>(expectedFiles);
        expectedHashedFiles.remove(ProfileBundleAssembler.ARTIFACT_HASH_MANIFEST_FILE_NAME);
        if (!expectedHashedFiles.equals(hashes.getSha256().keySet())) {
            throw new IOException("artifact-sha256.json 文件集合不匹配：期望="
                    + expectedHashedFiles + "，实际=" + hashes.getSha256().keySet());
        }
        for (String relativePath : expectedHashedFiles) {
            String actual = MessageDigestUtils.sha256(
                    Files.readAllBytes(root.resolve(relativePath)));
            String expected = hashes.getSha256().get(relativePath);
            if (!actual.equals(expected)) {
                throw new IOException("Profile Bundle SHA-256 不匹配：文件=" + relativePath
                        + "，期望=" + expected + "，实际=" + actual);
            }
        }
    }

    private static void validateRevision(ProfileRevisionManifest bundled,
                                         ProfileRevisionManifest persisted,
                                         String applicationId, String profileId,
                                         String revisionId) throws IOException {
        requireEqual("revision.json.applicationId", applicationId, bundled.getApplicationId());
        requireEqual("revision.json.profileId", profileId, bundled.getProfileId());
        requireEqual("revision.json.revisionId", revisionId, bundled.getRevisionId());
        if (bundled.getStatus() != RevisionStatus.CANDIDATE) {
            throw new IOException("revision.json.status 必须保留 CANDIDATE 构建快照，实际="
                    + bundled.getStatus());
        }
        requireEqual("revision.json.recipeId", persisted.getRecipeId(), bundled.getRecipeId());
        requireEqual("revision.json.overlaySha256", persisted.getOverlaySha256(),
                bundled.getOverlaySha256());
        requireEqual("revision.json.generatorVersion", persisted.getGeneratorVersion(),
                bundled.getGeneratorVersion());
        requireEqual("revision.json.coreVersion", persisted.getCoreVersion(),
                bundled.getCoreVersion());
        requireEqual("revision.json.toolchainVersion", persisted.getToolchainVersion(),
                bundled.getToolchainVersion());
        requireEqual("revision.json.cacheKey", persisted.getCacheKey(), bundled.getCacheKey());
        requireEqual("revision.json.generatedSourceSha256", persisted.getGeneratedSourceSha256(),
                bundled.getGeneratedSourceSha256());
        if (persisted.getCreatedAtEpochMillis() != bundled.getCreatedAtEpochMillis()) {
            throw new IOException("revision.json.createdAtEpochMillis 与状态源不匹配");
        }
        requireEqual("revision.json.generatorVersion", ProfileCodeGenerator.GENERATOR_VERSION,
                bundled.getGeneratorVersion());
        requireEqual("revision.json.coreVersion", ProfileCodeGenerator.IMMUTABLE_CORE_VERSION,
                bundled.getCoreVersion());
        try {
            ProfileRecipeRegistry.builtIn().requireVerified(bundled.getRecipeId(),
                    com.luoye.dpt.profile.model.ProfileSchemaVersions.PROFILE_PROTOCOL);
        } catch (IllegalArgumentException exception) {
            throw new IOException("revision.json.recipeId 未登记或未验证："
                    + bundled.getRecipeId(), exception);
        }
    }

    private static void validateContract(ProfileContract contract, ProfileIdentityLock identity,
                                         ProfileRevisionManifest revision) throws IOException {
        requireEqual("profile.json.applicationId", identity.getApplicationId(),
                contract.getApplicationId());
        requireEqual("profile.json.profileId", identity.getProfileId(), contract.getProfileId());
        requireEqual("profile.json.revisionId", revision.getRevisionId(),
                contract.getRevisionId());
        requireEqual("profile.json.generatorVersion", revision.getGeneratorVersion(),
                contract.getGeneratorVersion());
        if (!Collections.singletonList(ProfileCodeGenerator.PROFILE_ABI).equals(contract.getAbis())) {
            throw new IOException("profile.json.abis 必须且只能包含 arm64-v8a");
        }
        requireEqual("profile.json.proxyApplicationClassName",
                identity.getProxyApplicationClassName(), contract.getProxyApplicationClassName());
        requireEqual("profile.json.proxyComponentFactoryClassName",
                identity.getProxyComponentFactoryClassName(),
                contract.getProxyComponentFactoryClassName());
        requireEqual("profile.json.jniBridgeClassName", identity.getJniBridgeClassName(),
                contract.getJniBridgeClassName());
        requireEqual("profile.json.soName", identity.getSoName(), contract.getSoName());
        requireEqual("profile.json.shellConfigAssetName", identity.getShellConfigAssetName(),
                contract.getShellConfigAssetName());
        requireEqual("profile.json.dexAssetName", identity.getDexAssetName(),
                contract.getDexAssetName());
        requireEqual("profile.json.codeItemAssetName", identity.getCodeItemAssetName(),
                contract.getCodeItemAssetName());
        requireEqual("profile.json.nativeLibAssetDirName", identity.getNativeLibAssetDirName(),
                contract.getNativeLibAssetDirName());
        requireEqual("profile.json.runtimeDirName", identity.getRuntimeDirName(),
                contract.getRuntimeDirName());
        requireEqual("profile.json.junkClassName", identity.getJunkClassName(),
                contract.getJunkClassName());
        requireEqual("profile.json.rc4KeySymbol", identity.getRc4KeySymbol(),
                contract.getRc4KeySymbol());
        requireEqual("profile.json.buildKey", identity.getBuildKey(), contract.getBuildKey());
        if (!jniMappings(identity.getJniMethods()).equals(jniMappings(contract.getJniMethods()))) {
            throw new IOException("profile.json JNI 映射与身份锁不匹配");
        }
    }

    private static void validateBuildKey(Path buildKeyPath, ProfileIdentityLock identity)
            throws IOException {
        byte[] expected = (identity.getBuildKey() + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] actual = Files.readAllBytes(buildKeyPath);
        if (!Arrays.equals(expected, actual)) {
            throw new IOException("shell-files/build-key 与 Profile 身份锁不匹配");
        }
    }

    private static void validateDex(Path dexPath, ProfileContract contract) throws IOException {
        DexBackedDexFile dex;
        try {
            dex = DexFileFactory.loadDexFile(dexPath.toFile(), Opcodes.getDefault());
        } catch (IOException | RuntimeException exception) {
            throw new IOException("壳 classes.dex 无法解析", exception);
        }

        Map<String, ClassDef> classes = new LinkedHashMap<>();
        for (ClassDef classDef : dex.getClasses()) {
            classes.put(classDef.getType(), classDef);
        }
        requireDexClass(classes, contract.getProxyApplicationClassName(), "代理 Application");
        requireDexClass(classes, contract.getProxyComponentFactoryClassName(),
                "代理 AppComponentFactory");
        ClassDef bridge = requireDexClass(classes, contract.getJniBridgeClassName(), "JNI Bridge");

        Map<String, List<Method>> nativeByName = new LinkedHashMap<>();
        for (Method method : bridge.getMethods()) {
            if (AccessFlags.NATIVE.isSet(method.getAccessFlags())) {
                nativeByName.computeIfAbsent(method.getName(), ignored -> new ArrayList<>())
                        .add(method);
            }
        }
        for (JniMethodMapping mapping : contract.getJniMethods()) {
            List<Method> methods = nativeByName.remove(mapping.getMethodName());
            if (methods == null || methods.size() != 1) {
                throw new IOException("DEX JNI 方法缺失或重复：" + mapping.getMethodName());
            }
            Method method = methods.get(0);
            if (!AccessFlags.STATIC.isSet(method.getAccessFlags())) {
                throw new IOException("DEX JNI 方法必须为 static native："
                        + mapping.getMethodName());
            }
            String actualSignature = methodDescriptor(method);
            if (!mapping.getJvmSignature().equals(actualSignature)) {
                throw new IOException("DEX JNI 签名不匹配：方法=" + mapping.getMethodName()
                        + "，期望=" + mapping.getJvmSignature() + "，实际=" + actualSignature);
            }
        }
        if (!nativeByName.isEmpty()) {
            throw new IOException("DEX JNI Bridge 包含契约外 native 方法："
                    + nativeByName.keySet().iterator().next());
        }
    }

    private static ClassDef requireDexClass(Map<String, ClassDef> classes, String className,
                                            String label) throws IOException {
        ClassDef classDef = classes.get(classDescriptor(className));
        if (classDef == null) {
            throw new IOException("壳 classes.dex 缺少" + label + "：" + className);
        }
        return classDef;
    }

    private static String methodDescriptor(Method method) {
        StringBuilder result = new StringBuilder("(");
        for (CharSequence parameter : method.getParameterTypes()) {
            result.append(parameter);
        }
        return result.append(')').append(method.getReturnType()).toString();
    }

    private static void validateArm64So(Path soPath, ProfileContract contract) throws IOException {
        byte[] content = Files.readAllBytes(soPath);
        if (content.length < 20 || content[0] != 0x7f || content[1] != 'E'
                || content[2] != 'L' || content[3] != 'F' || content[4] != 2
                || content[5] != 1 || content[6] != 1
                || Byte.toUnsignedInt(content[18]) != 0xb7
                || Byte.toUnsignedInt(content[19]) != 0) {
            throw new IOException("arm64 壳 SO 不是有效的 AArch64 64 位小端 ELF：" + soPath);
        }
        requireBinaryString(content, contract.getJniBridgeClassName().replace('.', '/'),
                "Native JNI Bridge");
        for (JniMethodMapping mapping : contract.getJniMethods()) {
            requireBinaryString(content, mapping.getMethodName(), "Native JNI 方法");
            requireBinaryString(content, mapping.getJvmSignature(), "Native JNI 签名");
        }
    }

    private static void requireBinaryString(byte[] content, String expected, String label)
            throws IOException {
        byte[] needle = expected.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int start = 0; start <= content.length - needle.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (content[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return;
        }
        throw new IOException(label + " 缺少契约字符串：" + expected);
    }

    private static Set<String> expectedFilePaths(String soName) {
        return new LinkedHashSet<>(Arrays.asList(
                ProfileBundleAssembler.REVISION_MANIFEST_FILE_NAME,
                ProfileBundleAssembler.PROFILE_CONTRACT_FILE_NAME,
                ProfileBundleAssembler.ARTIFACT_HASH_MANIFEST_FILE_NAME,
                ProfileBundleAssembler.BUILD_KEY_RELATIVE_PATH,
                ProfileBundleAssembler.SHELL_DEX_RELATIVE_PATH,
                ProfileBundleAssembler.ARM64_SO_DIRECTORY + "/" + soName));
    }

    private static Set<String> expectedDirectoryPaths() {
        return new LinkedHashSet<>(Arrays.asList(
                ProfileBundleAssembler.SHELL_FILES_DIRECTORY_NAME,
                ProfileBundleAssembler.SHELL_FILES_DIRECTORY_NAME + "/dex",
                ProfileBundleAssembler.SHELL_FILES_DIRECTORY_NAME + "/libs",
                ProfileBundleAssembler.ARM64_SO_DIRECTORY));
    }

    private static Map<String, String> jniMappings(List<JniMethodMapping> mappings) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JniMethodMapping mapping : mappings) {
            result.put(mapping.getSemanticId(), mapping.getMethodName() + "\n"
                    + mapping.getJvmSignature());
        }
        return result;
    }

    private static String classDescriptor(String className) {
        return "L" + className.replace('.', '/') + ";";
    }

    private static String unixPath(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }

    private static void requireEqual(String field, Object expected, Object actual)
            throws IOException {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IOException(field + " 不匹配：期望=" + expected + "，实际=" + actual);
        }
    }
}
