package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Assembles the compiled artifacts of one candidate Revision into an isolated Bundle.
 *
 * <p>The assembler deliberately does not activate or publish the Revision. It only creates the
 * candidate directory consumed by later local and external gates.</p>
 */
public final class ProfileBundleAssembler {
    public static final String CANDIDATES_DIRECTORY_NAME = "candidates";
    public static final String BUNDLE_DIRECTORY_NAME = "bundle";
    public static final String REVISION_MANIFEST_FILE_NAME = "revision.json";
    public static final String PROFILE_CONTRACT_FILE_NAME = "profile.json";
    public static final String ARTIFACT_HASH_MANIFEST_FILE_NAME = "artifact-sha256.json";
    public static final String ARTIFACT_SHA256_FILE_NAME = ARTIFACT_HASH_MANIFEST_FILE_NAME;
    public static final String PROFILE_JSON_FILE_NAME = PROFILE_CONTRACT_FILE_NAME;
    public static final String BUILD_KEY_FILE_NAME = "build-key";
    public static final String SHELL_FILES_DIRECTORY_NAME = "shell-files";
    public static final String BUILD_KEY_RELATIVE_PATH = SHELL_FILES_DIRECTORY_NAME
            + "/" + BUILD_KEY_FILE_NAME;
    public static final String SHELL_DEX_RELATIVE_PATH = SHELL_FILES_DIRECTORY_NAME
            + "/dex/classes.dex";
    public static final String ARM64_SO_DIRECTORY = SHELL_FILES_DIRECTORY_NAME
            + "/libs/arm64";

    private static final List<String> HASHED_ARTIFACT_PATHS = Arrays.asList(
            REVISION_MANIFEST_FILE_NAME,
            PROFILE_CONTRACT_FILE_NAME,
            BUILD_KEY_RELATIVE_PATH,
            SHELL_DEX_RELATIVE_PATH);

    private final Path profilesRoot;
    private final ProfileRegistry registry;
    private final ProfileIdentityStore identityStore;
    private final ProfileRevisionStore revisionStore;

    public ProfileBundleAssembler(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        this.profilesRoot = profilesRoot.toAbsolutePath().normalize();
        this.registry = new ProfileRegistry(this.profilesRoot);
        this.identityStore = new ProfileIdentityStore(this.profilesRoot);
        this.revisionStore = new ProfileRevisionStore(this.profilesRoot);
    }

    /**
     * Loads the persisted identity and candidate Revision for an application and assembles its
     * default candidate Bundle.
     */
    public ProfileBundle assemble(String applicationId, String profileId, String revisionId,
                                  Path shellDexSource, Path arm64SoSource) throws IOException {
        return assemble(applicationId, profileId, revisionId, shellDexSource, arm64SoSource, null);
    }

    public ProfileBundle assemble(String applicationId, String profileId, String revisionId,
                                  Path shellDexSource, Path arm64SoSource, Path buildKeySource)
            throws IOException {
        String resolvedProfileId = profileId;
        String resolvedApplicationId = applicationId;
        if (resolvedProfileId == null || resolvedProfileId.trim().isEmpty()) {
            if (resolvedApplicationId == null || resolvedApplicationId.trim().isEmpty()) {
                throw new IllegalArgumentException("applicationId 或 profileId 不能为空");
            }
            java.util.Optional<String> profile = registry.findProfileId(resolvedApplicationId);
            if (!profile.isPresent()) {
                throw new IOException("Profile 不存在：applicationId=" + resolvedApplicationId);
            }
            resolvedProfileId = profile.get();
        }
        if (resolvedApplicationId == null || resolvedApplicationId.trim().isEmpty()) {
            java.util.Optional<String> application = registry.findApplicationId(resolvedProfileId);
            if (!application.isPresent()) {
                throw new IOException("Profile 不存在：profileId=" + resolvedProfileId);
            }
            resolvedApplicationId = application.get();
        }
        ProfileIdentityLock identity = identityStore.loadExisting(resolvedApplicationId,
                resolvedProfileId);
        ProfileRevisionManifest revision = revisionStore.load(resolvedProfileId, revisionId);
        return assemble(identity, revision, shellDexSource, arm64SoSource, buildKeySource);
    }

    public ProfileBundle assemble(ProfileIdentityLock identity, ProfileRevisionManifest revision,
                                  Path shellDexSource, Path arm64SoSource) throws IOException {
        return assemble(identity, revision, null, shellDexSource, arm64SoSource, null,
                defaultBundleRoot(identity, revision));
    }

    public ProfileBundle assemble(ProfileIdentityLock identity, ProfileRevisionManifest revision,
                                  Path shellDexSource, Path arm64SoSource, Path buildKeySource)
            throws IOException {
        return assemble(identity, revision, null, shellDexSource, arm64SoSource, buildKeySource,
                defaultBundleRoot(identity, revision));
    }

    /**
     * Assembles from a shell-files shaped directory. This is the strict integration form: the
     * directory must provide {@code build-key}, {@code dex/classes.dex}, and exactly one
     * {@code libs/arm64/*.so}.
     */
    public ProfileBundle assemble(ProfileIdentityLock identity, ProfileRevisionManifest revision,
                                  Path shellFilesRoot) throws IOException {
        if (shellFilesRoot == null) {
            throw new IOException("shell-files 目录不能为空");
        }
        Path root = shellFilesRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
            throw new IOException("shell-files 必须是普通目录：" + root);
        }
        return assemble(identity, revision,
                root.resolve("dex").resolve("classes.dex"),
                root.resolve("libs").resolve("arm64"),
                root.resolve(BUILD_KEY_FILE_NAME));
    }

    public ProfileBundle assembleFromShellFiles(ProfileIdentityLock identity,
                                                ProfileRevisionManifest revision,
                                                Path shellFilesRoot) throws IOException {
        return assemble(identity, revision, shellFilesRoot);
    }

    public ProfileBundle assembleCandidate(ProfileIdentityLock identity,
                                           ProfileRevisionManifest revision,
                                           Path shellDexSource, Path arm64SoSource,
                                           Path buildKeySource) throws IOException {
        return assemble(identity, revision, shellDexSource, arm64SoSource, buildKeySource);
    }

    public Path getDefaultBundleRoot(String profileId, String revisionId) {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        return profilesRoot.resolve(CANDIDATES_DIRECTORY_NAME).resolve(profileId)
                .resolve(revisionId).resolve(BUNDLE_DIRECTORY_NAME);
    }

    public ProfileBundle assemble(ProfileIdentityLock identity, ProfileRevisionManifest revision,
                                  ProfileContract contract, Path shellDexSource,
                                  Path arm64SoSource) throws IOException {
        return assemble(identity, revision, contract, shellDexSource, arm64SoSource, null,
                defaultBundleRoot(identity, revision));
    }

    /**
     * Variant used by build integrations that need to provide a compiled build-key file.
     */
    public ProfileBundle assemble(ProfileIdentityLock identity, ProfileRevisionManifest revision,
                                  ProfileContract contract, Path shellDexSource,
                                  Path arm64SoSource, Path buildKeySource) throws IOException {
        return assemble(identity, revision, contract, shellDexSource, arm64SoSource,
                buildKeySource, defaultBundleRoot(identity, revision));
    }

    /**
     * Assembles to an explicit destination. The destination must not already exist.
     */
    public ProfileBundle assembleAt(Path bundleRoot, ProfileIdentityLock identity,
                                    ProfileRevisionManifest revision, ProfileContract contract,
                                    Path shellDexSource, Path arm64SoSource,
                                    Path buildKeySource) throws IOException {
        return assemble(identity, revision, contract, shellDexSource, arm64SoSource,
                buildKeySource, bundleRoot);
    }

    private ProfileBundle assemble(ProfileIdentityLock identity, ProfileRevisionManifest revision,
                                   ProfileContract suppliedContract, Path shellDexSource,
                                   Path arm64SoSource, Path buildKeySource,
                                   Path bundleRoot) throws IOException {
        if (bundleRoot == null) {
            throw new IllegalArgumentException("bundleRoot must not be null");
        }
        validateIdentityAndRevision(identity, revision);
        ProfileContract contract = suppliedContract == null
                ? contractFor(identity, revision) : validateContract(identity, revision, suppliedContract);
        Path shellDex = resolveShellDex(shellDexSource);
        Path arm64So = resolveArm64So(arm64SoSource, identity.getSoName());
        byte[] buildKey = resolveBuildKey(identity.getBuildKey(), buildKeySource);

        Path target = bundleRoot.toAbsolutePath().normalize();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Profile Bundle 已存在，拒绝覆盖：" + target);
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Profile Bundle 目标路径无父目录：" + target);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempDirectory(parent, "." + revision.getRevisionId()
                + ".bundle-");
        try {
            byte[] revisionBytes = JSON.toJSONString(revision).getBytes(StandardCharsets.UTF_8);
            byte[] contractBytes = ProfileContractCodec.serialize(contract);
            writeBytes(temporary.resolve(REVISION_MANIFEST_FILE_NAME), revisionBytes);
            writeBytes(temporary.resolve(PROFILE_CONTRACT_FILE_NAME), contractBytes);
            writeBytes(temporary.resolve(BUILD_KEY_RELATIVE_PATH), buildKey);
            Path dexTarget = temporary.resolve(SHELL_DEX_RELATIVE_PATH);
            copyArtifact(shellDex, dexTarget);
            Path soTarget = temporary.resolve(ARM64_SO_DIRECTORY).resolve(identity.getSoName());
            copyArtifact(arm64So, soTarget);

            ArtifactHashManifest hashes = createHashManifest(identity, revision,
                    temporary, identity.getSoName());
            byte[] hashBytes = JSON.toJSONString(hashes).getBytes(StandardCharsets.UTF_8);
            writeBytes(temporary.resolve(ARTIFACT_HASH_MANIFEST_FILE_NAME), hashBytes);
            moveAtomically(temporary, target);
            return new ProfileBundle(target, contract, hashes);
        } catch (IOException | RuntimeException failure) {
            deleteTree(temporary);
            throw failure;
        }
    }

    private Path defaultBundleRoot(ProfileIdentityLock identity,
                                   ProfileRevisionManifest revision) {
        if (identity == null || revision == null) {
            throw new IllegalArgumentException("identity and revision must not be null");
        }
        return profilesRoot.resolve(CANDIDATES_DIRECTORY_NAME)
                .resolve(identity.getProfileId()).resolve(revision.getRevisionId())
                .resolve(BUNDLE_DIRECTORY_NAME);
    }

    private static void validateIdentityAndRevision(ProfileIdentityLock identity,
                                                    ProfileRevisionManifest revision)
            throws IOException {
        if (identity == null || revision == null) {
            throw new IllegalArgumentException("identity and revision must not be null");
        }
        ProfileModelValidator.validate(identity);
        ProfileModelValidator.validate(revision);
        if (!identity.getProfileId().equals(revision.getProfileId())
                || !identity.getApplicationId().equals(revision.getApplicationId())) {
            throw new IOException("Profile Bundle 身份与 Revision 不匹配");
        }
        if (revision.getStatus() != RevisionStatus.CANDIDATE) {
            throw new IOException("只能组装 CANDIDATE Revision：" + revision.getRevisionId()
                    + "，当前状态=" + revision.getStatus());
        }
        try {
            ProfileRecipeRegistry.builtIn().requireVerified(revision.getRecipeId(),
                    ProfileSchemaVersions.PROFILE_PROTOCOL);
        } catch (IllegalArgumentException e) {
            throw new IOException("Revision Recipe 不可用于 Bundle：" + revision.getRecipeId(), e);
        }
    }

    private static ProfileContract contractFor(ProfileIdentityLock identity,
                                               ProfileRevisionManifest revision) {
        ProfileContract contract = new ProfileContract();
        contract.setSchemaVersion(ProfileSchemaVersions.PROFILE_CONTRACT);
        contract.setProtocolVersion(ProfileSchemaVersions.PROFILE_PROTOCOL);
        contract.setGeneratorVersion(revision.getGeneratorVersion());
        contract.setProfileId(revision.getProfileId());
        contract.setRevisionId(revision.getRevisionId());
        contract.setApplicationId(revision.getApplicationId());
        contract.setAbis(Arrays.asList(ProfileCodeGenerator.PROFILE_ABI));
        contract.setProxyApplicationClassName(identity.getProxyApplicationClassName());
        contract.setProxyComponentFactoryClassName(identity.getProxyComponentFactoryClassName());
        contract.setJniBridgeClassName(identity.getJniBridgeClassName());
        List<JniMethodMapping> mappings = new ArrayList<>();
        for (JniMethodMapping mapping : identity.getJniMethods()) {
            mappings.add(new JniMethodMapping(mapping.getSemanticId(), mapping.getMethodName(),
                    mapping.getJvmSignature()));
        }
        contract.setJniMethods(mappings);
        contract.setSoName(identity.getSoName());
        contract.setShellConfigAssetName(identity.getShellConfigAssetName());
        contract.setDexAssetName(identity.getDexAssetName());
        contract.setCodeItemAssetName(identity.getCodeItemAssetName());
        contract.setNativeLibAssetDirName(identity.getNativeLibAssetDirName());
        contract.setRuntimeDirName(identity.getRuntimeDirName());
        contract.setJunkClassName(identity.getJunkClassName());
        contract.setRc4KeySymbol(identity.getRc4KeySymbol());
        contract.setBuildKey(identity.getBuildKey());
        ProfileModelValidator.validate(contract);
        return contract;
    }

    private static ProfileContract validateContract(ProfileIdentityLock identity,
                                                    ProfileRevisionManifest revision,
                                                    ProfileContract supplied) throws IOException {
        ProfileModelValidator.validate(supplied);
        ProfileContract expected = contractFor(identity, revision);
        if (!Arrays.equals(ProfileContractCodec.serialize(expected),
                ProfileContractCodec.serialize(supplied))) {
            throw new IOException("Profile Bundle 契约与身份或 Revision 不匹配");
        }
        return supplied;
    }

    private static Path resolveShellDex(Path source) throws IOException {
        if (source == null) {
            throw new IOException("壳 classes.dex 路径不能为空");
        }
        Path normalized = source.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)) {
            throw new IOException("壳 classes.dex 不得为符号链接：" + normalized);
        }
        if (Files.isRegularFile(normalized)) {
            if (!"classes.dex".equals(normalized.getFileName().toString())) {
                throw new IOException("壳 DEX 文件名必须为 classes.dex：" + normalized);
            }
            requireNonEmpty(normalized, "壳 classes.dex");
            return normalized;
        }
        if (!Files.isDirectory(normalized)) {
            throw new IOException("壳 classes.dex 路径不存在：" + normalized);
        }
        List<Path> dexFiles = regularFiles(normalized, ".dex");
        if (dexFiles.size() != 1 || !"classes.dex".equals(dexFiles.get(0).getFileName().toString())) {
            throw new IOException("壳 DEX 必须且只能包含一个 classes.dex：" + normalized);
        }
        requireNonEmpty(dexFiles.get(0), "壳 classes.dex");
        return dexFiles.get(0);
    }

    private static Path resolveArm64So(Path source, String expectedName) throws IOException {
        if (source == null) {
            throw new IOException("arm64 壳 SO 路径不能为空");
        }
        Path normalized = source.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)) {
            throw new IOException("arm64 壳 SO 不得为符号链接：" + normalized);
        }
        Path result;
        if (Files.isRegularFile(normalized)) {
            result = normalized;
        } else if (Files.isDirectory(normalized)) {
            List<Path> soFiles = regularFiles(normalized, ".so");
            if (soFiles.size() != 1) {
                throw new IOException("arm64 壳 SO 必须且只能包含一个文件：" + normalized);
            }
            result = soFiles.get(0);
        } else {
            throw new IOException("arm64 壳 SO 路径不存在：" + normalized);
        }
        if (!expectedName.equals(result.getFileName().toString())) {
            throw new IOException("arm64 壳 SO 文件名与 Profile 契约不一致：期望="
                    + expectedName + "，实际=" + result.getFileName());
        }
        requireNonEmpty(result, "arm64 壳 SO");
        return result;
    }

    private static List<Path> regularFiles(Path root, String suffix) throws IOException {
        List<Path> result;
        try (Stream<Path> paths = Files.walk(root)) {
            result = paths.filter(path -> !path.equals(root))
                    .peek(ProfileBundleAssembler::rejectSymbolicLinkUnchecked)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        } catch (UncheckedIOExceptionWrapper e) {
            throw e.ioException();
        }
        return result;
    }

    private static void rejectSymbolicLinkUnchecked(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new UncheckedIOExceptionWrapper(new IOException(
                    "Bundle 输入树不得包含符号链接：" + path));
        }
    }

    private static byte[] resolveBuildKey(String expected, Path source) throws IOException {
        if (source == null) {
            return (expected + "\n").getBytes(StandardCharsets.UTF_8);
        }
        Path normalized = source.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized) || !Files.isRegularFile(normalized)) {
            throw new IOException("build-key 必须是普通文件：" + normalized);
        }
        String actual = Files.readString(normalized, StandardCharsets.UTF_8).trim();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("build-key 与 Profile 身份不一致：期望="
                    + expected + "，实际=" + actual);
        }
        return (expected + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static ArtifactHashManifest createHashManifest(ProfileIdentityLock identity,
                                                            ProfileRevisionManifest revision,
                                                            Path bundleRoot, String soName)
            throws IOException {
        ArtifactHashManifest hashes = new ArtifactHashManifest();
        hashes.setProfileId(identity.getProfileId());
        hashes.setRevisionId(revision.getRevisionId());
        Map<String, String> values = new LinkedHashMap<>();
        for (String path : HASHED_ARTIFACT_PATHS) {
            values.put(path, MessageDigestUtils.sha256(Files.readAllBytes(bundleRoot.resolve(path))));
        }
        String soPath = ARM64_SO_DIRECTORY + "/" + soName;
        values.put(soPath, MessageDigestUtils.sha256(Files.readAllBytes(bundleRoot.resolve(soPath))));
        hashes.setSha256(values);
        ProfileModelValidator.validate(hashes);
        return hashes;
    }

    private static void copyArtifact(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void writeBytes(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void requireNonEmpty(Path path, String label) throws IOException {
        if (Files.size(path) <= 0L) {
            throw new IOException(label + " 不能为空：" + path);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            throw new IOException("Profile Bundle 不支持原子移动：" + target, e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> all = paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            IOException failure = null;
            for (Path path : all) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class UncheckedIOExceptionWrapper extends RuntimeException {
        private UncheckedIOExceptionWrapper(IOException cause) {
            super(cause);
        }

        private IOException ioException() {
            return (IOException) super.getCause();
        }
    }
}
