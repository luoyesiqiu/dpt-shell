package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.util.HexUtils;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable-by-convention cache for generated Profile source trees.
 *
 * <p>An entry is usable only when its completion marker, metadata, file set and every SHA-256
 * digest agree. Corrupt entries are treated as cache misses and can be rebuilt safely.</p>
 */
public final class ProfileCandidateCache {
    private static final int SCHEMA_VERSION = ProfileSchemaVersions.CANDIDATE_CACHE;
    private static final String CACHE_ROOT_NAME = "candidate-cache";
    private static final String MANIFEST_FILE_NAME = "cache.json";
    private static final String COMPLETE_FILE_NAME = "complete";
    private static final String GENERATED_DIRECTORY_NAME = "generated";

    private final Path profilesRoot;
    private final Path cacheRoot;

    public ProfileCandidateCache(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        this.profilesRoot = profilesRoot;
        this.cacheRoot = profilesRoot.resolve("cache").resolve(CACHE_ROOT_NAME);
    }

    public Path getEntryPath(ProfileCandidateCacheKey key) {
        requireKey(key);
        return cacheRoot.resolve(key.getProfileId()).resolve(key.getKey());
    }

    /** Returns a verified entry, or empty when it is missing/incomplete/corrupt. */
    public Optional<CacheEntry> find(ProfileCandidateCacheKey key) throws IOException {
        requireKey(key);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, key.getProfileId())) {
            return findLocked(key);
        }
    }

    /** Stores a source tree atomically. A valid existing entry is never overwritten. */
    public CacheEntry store(ProfileCandidateCacheKey key, Path generatedSourceRoot)
            throws IOException {
        requireKey(key);
        Path sourceRoot = requireDirectory(generatedSourceRoot, "generated source root");
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, key.getProfileId())) {
            Optional<CacheEntry> existing = findLocked(key);
            if (existing.isPresent()) {
                return existing.get();
            }

            Files.createDirectories(cacheRoot.resolve(key.getProfileId()));
            Path temporaryRoot = Files.createTempDirectory(cacheRoot.resolve(key.getProfileId()),
                    "." + key.getKey() + ".tmp-");
            try {
                Path generatedTarget = temporaryRoot.resolve(GENERATED_DIRECTORY_NAME);
                copyTree(sourceRoot, generatedTarget);
                TreeSnapshot snapshot = snapshot(generatedTarget);
                CacheManifest manifest = CacheManifest.from(key, snapshot.generatedSourceSha256,
                        snapshot.fileHashes);
                writeString(temporaryRoot.resolve(MANIFEST_FILE_NAME),
                        JSON.toJSONString(manifest));
                writeString(temporaryRoot.resolve(COMPLETE_FILE_NAME), key.getKey() + "\n");
                Path target = getEntryPath(key);
                if (Files.exists(target)) {
                    deleteTree(target);
                }
                moveAtomically(temporaryRoot, target);
                return readVerifiedEntry(key);
            } finally {
                if (Files.exists(temporaryRoot)) {
                    deleteTree(temporaryRoot);
                }
            }
        }
    }

    /**
     * Copies a verified cache result into an isolated revision source directory. The cache is
     * never modified by this operation.
     */
    public Optional<CacheEntry> restore(ProfileCandidateCacheKey key, Path destinationRoot)
            throws IOException {
        requireKey(key);
        if (destinationRoot == null) {
            throw new IllegalArgumentException("destinationRoot must not be null");
        }
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, key.getProfileId())) {
            Optional<CacheEntry> entry = findLocked(key);
            if (!entry.isPresent()) {
                return Optional.empty();
            }
            Path destination = destinationRoot.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(destination)) {
                throw new IOException("cache restore destination must not be a symbolic link: "
                        + destination);
            }
            Files.createDirectories(destination);
            try (Stream<Path> paths = Files.list(destination)) {
                if (paths.anyMatch(path -> Files.isRegularFile(path) || Files.isSymbolicLink(path))) {
                    throw new IOException("cache restore destination is not empty: " + destination);
                }
            }

            Path generatedRoot = getEntryPath(key).resolve(GENERATED_DIRECTORY_NAME);
            copyTree(generatedRoot, destination);
            TreeSnapshot restored = snapshot(destination);
            if (!entry.get().getGeneratedSourceSha256().equals(restored.generatedSourceSha256)
                    || !entry.get().getFileHashes().equals(restored.fileHashes)) {
                throw new ProfileCandidateCacheException("cache changed during restore: " + key.getKey());
            }
            return entry;
        }
    }

    private Optional<CacheEntry> findLocked(ProfileCandidateCacheKey key) throws IOException {
        Path entryPath = getEntryPath(key);
        if (!Files.isDirectory(entryPath) || Files.isSymbolicLink(entryPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(readVerifiedEntry(key));
        } catch (ProfileCandidateCacheException e) {
            return Optional.empty();
        }
    }

    private CacheEntry readVerifiedEntry(ProfileCandidateCacheKey key) throws IOException {
        Path entryPath = getEntryPath(key);
        Path manifestPath = entryPath.resolve(MANIFEST_FILE_NAME);
        Path completePath = entryPath.resolve(COMPLETE_FILE_NAME);
        Path generatedPath = entryPath.resolve(GENERATED_DIRECTORY_NAME);
        if (!Files.isRegularFile(manifestPath) || !Files.isRegularFile(completePath)
                || !Files.isDirectory(generatedPath) || Files.isSymbolicLink(generatedPath)) {
            throw new ProfileCandidateCacheException("cache entry is incomplete: " + entryPath);
        }
        String marker = Files.readString(completePath, StandardCharsets.UTF_8);
        if (!marker.equals(key.getKey() + "\n")) {
            throw new ProfileCandidateCacheException("cache completion marker mismatch: " + entryPath);
        }
        CacheManifest manifest;
        try {
            manifest = JSON.parseObject(Files.readString(manifestPath, StandardCharsets.UTF_8),
                    CacheManifest.class);
        } catch (RuntimeException e) {
            throw new ProfileCandidateCacheException("cannot parse cache manifest: " + manifestPath, e);
        }
        if (manifest == null) {
            throw new ProfileCandidateCacheException("cache manifest is empty: " + manifestPath);
        }
        manifest.verifyAgainst(key);
        TreeSnapshot snapshot = snapshot(generatedPath);
        if (!manifest.generatedSourceSha256.equals(snapshot.generatedSourceSha256)
                || !manifest.fileHashes.equals(snapshot.fileHashes)) {
            throw new ProfileCandidateCacheException("cache artifact hash mismatch: " + entryPath);
        }
        verifyNoUnexpectedFiles(entryPath, snapshot.fileHashes.keySet());
        return new CacheEntry(entryPath, manifest.generatedSourceSha256, manifest.fileHashes);
    }

    private static void verifyNoUnexpectedFiles(Path entryPath, Iterable<String> expectedGeneratedFiles)
            throws IOException {
        Map<String, Boolean> expected = new LinkedHashMap<>();
        expected.put(MANIFEST_FILE_NAME, Boolean.TRUE);
        expected.put(COMPLETE_FILE_NAME, Boolean.TRUE);
        for (String relative : expectedGeneratedFiles) {
            expected.put(GENERATED_DIRECTORY_NAME + "/" + relative, Boolean.TRUE);
        }
        List<Path> allPaths;
        try (Stream<Path> walked = Files.walk(entryPath)) {
            allPaths = walked.sorted(Comparator.comparing(path -> slash(entryPath.relativize(path))))
                    .collect(Collectors.toList());
        }
        for (Path path : allPaths) {
            if (path.equals(entryPath)) {
                continue;
            }
            String relative = slash(entryPath.relativize(path));
            if (Files.isSymbolicLink(path)) {
                throw new ProfileCandidateCacheException("cache entry contains symbolic link: " + path);
            }
            if (Files.isDirectory(path)) {
                if (!relative.equals(GENERATED_DIRECTORY_NAME)
                        && !relative.startsWith(GENERATED_DIRECTORY_NAME + "/")) {
                    throw new ProfileCandidateCacheException("unexpected cache directory: " + path);
                }
                continue;
            }
            if (!Files.isRegularFile(path) || !expected.containsKey(relative)) {
                throw new ProfileCandidateCacheException("unexpected cache file: " + path);
            }
        }
    }

    private static TreeSnapshot snapshot(Path root) throws IOException {
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new ProfileCandidateCacheException("cache source is not a regular directory: " + root);
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.sorted(Comparator.comparing(path -> slash(root.relativize(path))))
                    .collect(Collectors.toList());
        }
        TreeMap<String, String> hashes = new TreeMap<>();
        MessageDigest digest = sha256Digest();
        for (Path file : files) {
            if (file.equals(root)) {
                continue;
            }
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                if (Files.isSymbolicLink(file) || !Files.isDirectory(file)) {
                    throw new ProfileCandidateCacheException("cache tree contains unsupported file: "
                            + file);
                }
                continue;
            }
            String relative = slash(root.relativize(file));
            byte[] content = Files.readAllBytes(file);
            hashes.put(relative, MessageDigestUtils.sha256(content));
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(content);
            digest.update((byte) 0);
        }
        return new TreeSnapshot(HexUtils.toHexString(digest.digest()), hashes);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isDirectory(source)) {
            throw new IOException("source tree is not a regular directory: " + source);
        }
        Files.createDirectories(target);
        try (Stream<Path> paths = Files.walk(source)) {
            List<Path> sorted = paths.sorted(Comparator.comparing(path -> slash(source.relativize(path))))
                    .collect(Collectors.toList());
            for (Path path : sorted) {
                if (path.equals(source)) {
                    continue;
                }
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target.toAbsolutePath().normalize())) {
                    throw new IOException("source tree path escapes target: " + relative);
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("source tree contains symbolic link: " + path);
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    throw new IOException("source tree contains unsupported file: " + path);
                }
            }
        }
    }

    private static Path requireDirectory(Path path, String label) throws IOException {
        if (path == null || Files.isSymbolicLink(path) || !Files.isDirectory(path)) {
            throw new IOException(label + " must be a regular directory: " + path);
        }
        return path.toAbsolutePath().normalize();
    }

    private static void writeString(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            throw new ProfileCandidateCacheException("atomic cache publish is not supported: "
                    + target, e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception)
                    throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String slash(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static void requireKey(ProfileCandidateCacheKey key) {
        if (key == null) {
            throw new IllegalArgumentException("cache key must not be null");
        }
    }

    public static final class CacheEntry {
        private final Path entryPath;
        private final String generatedSourceSha256;
        private final Map<String, String> fileHashes;

        private CacheEntry(Path entryPath, String generatedSourceSha256,
                           Map<String, String> fileHashes) {
            this.entryPath = entryPath;
            this.generatedSourceSha256 = generatedSourceSha256;
            this.fileHashes = Collections.unmodifiableMap(new TreeMap<>(fileHashes));
        }

        public Path getEntryPath() {
            return entryPath;
        }

        public Path getGeneratedSourceRoot() {
            return entryPath.resolve(GENERATED_DIRECTORY_NAME);
        }

        public String getGeneratedSourceSha256() {
            return generatedSourceSha256;
        }

        public Map<String, String> getFileHashes() {
            return fileHashes;
        }
    }

    private static final class TreeSnapshot {
        private final String generatedSourceSha256;
        private final Map<String, String> fileHashes;

        private TreeSnapshot(String generatedSourceSha256, Map<String, String> fileHashes) {
            this.generatedSourceSha256 = generatedSourceSha256;
            this.fileHashes = Collections.unmodifiableMap(new TreeMap<>(fileHashes));
        }
    }

    public static final class CacheManifest {
        private int schemaVersion = SCHEMA_VERSION;
        private String key;
        private String profileId;
        private String applicationId;
        private String identityLockSha256;
        private String recipeId;
        private int recipeProtocolVersion;
        private String javaCompositionId;
        private String nativeCompositionId;
        private String recipeVerificationStatus;
        private String overlaySha256;
        private String generatorVersion;
        private String coreVersion;
        private String toolchainVersion;
        private String generatedSourceSha256;
        private Map<String, String> fileHashes = new TreeMap<>();

        public int getSchemaVersion() {
            return schemaVersion;
        }

        public void setSchemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getProfileId() {
            return profileId;
        }

        public void setProfileId(String profileId) {
            this.profileId = profileId;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getIdentityLockSha256() {
            return identityLockSha256;
        }

        public void setIdentityLockSha256(String identityLockSha256) {
            this.identityLockSha256 = identityLockSha256;
        }

        public String getRecipeId() {
            return recipeId;
        }

        public void setRecipeId(String recipeId) {
            this.recipeId = recipeId;
        }

        public int getRecipeProtocolVersion() {
            return recipeProtocolVersion;
        }

        public void setRecipeProtocolVersion(int recipeProtocolVersion) {
            this.recipeProtocolVersion = recipeProtocolVersion;
        }

        public String getJavaCompositionId() {
            return javaCompositionId;
        }

        public void setJavaCompositionId(String javaCompositionId) {
            this.javaCompositionId = javaCompositionId;
        }

        public String getNativeCompositionId() {
            return nativeCompositionId;
        }

        public void setNativeCompositionId(String nativeCompositionId) {
            this.nativeCompositionId = nativeCompositionId;
        }

        public String getRecipeVerificationStatus() {
            return recipeVerificationStatus;
        }

        public void setRecipeVerificationStatus(String recipeVerificationStatus) {
            this.recipeVerificationStatus = recipeVerificationStatus;
        }

        public String getOverlaySha256() {
            return overlaySha256;
        }

        public void setOverlaySha256(String overlaySha256) {
            this.overlaySha256 = overlaySha256;
        }

        public String getGeneratorVersion() {
            return generatorVersion;
        }

        public void setGeneratorVersion(String generatorVersion) {
            this.generatorVersion = generatorVersion;
        }

        public String getCoreVersion() {
            return coreVersion;
        }

        public void setCoreVersion(String coreVersion) {
            this.coreVersion = coreVersion;
        }

        public String getToolchainVersion() {
            return toolchainVersion;
        }

        public void setToolchainVersion(String toolchainVersion) {
            this.toolchainVersion = toolchainVersion;
        }

        public String getGeneratedSourceSha256() {
            return generatedSourceSha256;
        }

        public void setGeneratedSourceSha256(String generatedSourceSha256) {
            this.generatedSourceSha256 = generatedSourceSha256;
        }

        public Map<String, String> getFileHashes() {
            return fileHashes;
        }

        public void setFileHashes(Map<String, String> fileHashes) {
            this.fileHashes = fileHashes == null ? null : new TreeMap<>(fileHashes);
        }

        private static CacheManifest from(ProfileCandidateCacheKey key,
                                          String generatedSourceSha256,
                                          Map<String, String> fileHashes) {
            CacheManifest manifest = new CacheManifest();
            manifest.key = key.getKey();
            manifest.profileId = key.getProfileId();
            manifest.applicationId = key.getApplicationId();
            manifest.identityLockSha256 = key.getIdentityLockSha256();
            manifest.recipeId = key.getRecipeId();
            manifest.recipeProtocolVersion = key.getRecipeProtocolVersion();
            manifest.javaCompositionId = key.getJavaCompositionId();
            manifest.nativeCompositionId = key.getNativeCompositionId();
            manifest.recipeVerificationStatus = key.getRecipeVerificationStatus();
            manifest.overlaySha256 = key.getOverlaySha256();
            manifest.generatorVersion = key.getGeneratorVersion();
            manifest.coreVersion = key.getCoreVersion();
            manifest.toolchainVersion = key.getToolchainVersion();
            manifest.generatedSourceSha256 = generatedSourceSha256;
            manifest.fileHashes = new TreeMap<>(fileHashes);
            return manifest;
        }

        private void verifyAgainst(ProfileCandidateCacheKey key) throws ProfileCandidateCacheException {
            if (schemaVersion != SCHEMA_VERSION || !key.getKey().equals(this.key)
                    || !key.getProfileId().equals(profileId)
                    || !key.getApplicationId().equals(applicationId)
                    || !key.getIdentityLockSha256().equals(identityLockSha256)
                    || !key.getRecipeId().equals(recipeId)
                    || key.getRecipeProtocolVersion() != recipeProtocolVersion
                    || !key.getJavaCompositionId().equals(javaCompositionId)
                    || !key.getNativeCompositionId().equals(nativeCompositionId)
                    || !key.getRecipeVerificationStatus().equals(recipeVerificationStatus)
                    || !key.getOverlaySha256().equals(overlaySha256)
                    || !key.getGeneratorVersion().equals(generatorVersion)
                    || !key.getCoreVersion().equals(coreVersion)
                    || !key.getToolchainVersion().equals(toolchainVersion)
                    || generatedSourceSha256 == null || !generatedSourceSha256.matches("[0-9a-f]{64}")
                    || fileHashes == null) {
                throw new ProfileCandidateCacheException("cache manifest input mismatch");
            }
        }
    }
}
