package com.luoye.dpt.profile.apk;

import com.luoye.dpt.profile.archive.ProfileArchiveBundle;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.res.ApkManifestEditor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads APK identity and native ABI metadata without consulting Profile state. */
public final class ApkProfileInputValidator {
    public static final String REQUIRED_ABI = "arm64-v8a";
    private static final String MANIFEST_FILE_NAME = "AndroidManifest.xml";
    private static final long MAX_MANIFEST_SIZE = 16L * 1024L * 1024L;
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L;

    private ApkProfileInputValidator() {
    }

    public static Metadata inspectArchive(Path apk) throws IOException {
        Path archive = requireRegularFile(apk, "APK");
        Path temporary = Files.createTempDirectory("dpt-apk-metadata-");
        try {
            Path manifest = temporary.resolve(MANIFEST_FILE_NAME);
            Set<String> abis = new LinkedHashSet<>();
            boolean manifestSeen = false;
            long total = 0L;
            int entryCount = 0;
            try (InputStream file = Files.newInputStream(archive);
                 ZipInputStream zip = new ZipInputStream(file)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entryCount > 4096) {
                        throw new IOException("APK 条目数量超过限制");
                    }
                    if (entry.isDirectory()) {
                        validateDirectoryName(entry.getName());
                        continue;
                    }
                    String name = validateEntryName(entry.getName());
                    if (MANIFEST_FILE_NAME.equals(name)) {
                        if (manifestSeen) {
                            throw new IOException("APK 包含重复 AndroidManifest.xml");
                        }
                        manifestSeen = true;
                        byte[] bytes = readLimited(zip, MAX_MANIFEST_SIZE, "APK Manifest");
                        Files.write(manifest, bytes, StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE);
                        total += bytes.length;
                    } else {
                        String[] parts = name.split("/");
                        if (parts.length == 3 && "lib".equals(parts[0])
                                && parts[2].endsWith(".so")) {
                            abis.add(parts[1]);
                        }
                        total += skipLimited(zip, MAX_ARCHIVE_BYTES - total, "APK");
                    }
                    if (total > MAX_ARCHIVE_BYTES) {
                        throw new IOException("APK 元数据读取超过限制");
                    }
                    zip.closeEntry();
                }
            }
            if (!manifestSeen) {
                throw new IOException("APK Manifest 不存在：" + archive);
            }
            Metadata metadata = inspectUnpackedRoot(manifest, temporary.resolve("lib"), abis);
            return metadata;
        } finally {
            deleteTree(temporary);
        }
    }

    public static Metadata inspectUnpacked(Path unpackedApkRoot) throws IOException {
        if (unpackedApkRoot == null || Files.isSymbolicLink(unpackedApkRoot)
                || !Files.isDirectory(unpackedApkRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("APK 解包目录不存在或不是普通目录：" + unpackedApkRoot);
        }
        Path manifest = unpackedApkRoot.resolve(MANIFEST_FILE_NAME);
        Set<String> abis = readBusinessNativeAbis(unpackedApkRoot);
        return inspectUnpackedRoot(manifest, unpackedApkRoot.resolve("lib"), abis);
    }

    public static void validate(ProfileArchiveBundle bundle, Metadata metadata)
            throws ProfileCommandException {
        if (bundle == null || metadata == null) {
            throw new IllegalArgumentException("Profile 和 APK 元数据不能为空");
        }
        if (!bundle.getApplicationId().equals(metadata.getApplicationId())) {
            throw new ProfileCommandException(ProfileErrorCode.APK_PROFILE_TARGET_MISMATCH,
                    "Profile 目标应用不匹配：Profile=" + bundle.getApplicationId()
                            + "，APK=" + metadata.getApplicationId());
        }
        if (!metadata.getBusinessNativeAbis().isEmpty()
                && !metadata.getBusinessNativeAbis().contains(REQUIRED_ABI)) {
            throw new ProfileCommandException(ProfileErrorCode.APK_ABI_INCOMPATIBLE,
                    "APK 包含业务 Native 库但缺少 " + REQUIRED_ABI + "：业务 ABI="
                            + metadata.getBusinessNativeAbis());
        }
    }

    private static Metadata inspectUnpackedRoot(Path manifest, Path libRoot,
                                                 Set<String> knownAbis) throws IOException {
        if (Files.isSymbolicLink(manifest)
                || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("APK Manifest 不存在或不是普通文件：" + manifest);
        }
        String applicationId = ApkManifestEditor.getPackageName(manifest.toString());
        try {
            ProfileModelValidator.validateApplicationId(applicationId);
        } catch (RuntimeException exception) {
            throw new IOException("APK Manifest 缺少有效 applicationId：" + manifest, exception);
        }
        return new Metadata(applicationId, knownAbis);
    }

    private static Set<String> readBusinessNativeAbis(Path root) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        Path libRoot = root.resolve("lib");
        if (!Files.exists(libRoot, LinkOption.NOFOLLOW_LINKS)) {
            return result;
        }
        if (Files.isSymbolicLink(libRoot)
                || !Files.isDirectory(libRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("APK Native 目录不是普通目录：" + libRoot);
        }
        try (Stream<Path> abis = Files.list(libRoot)) {
            for (Path abi : (Iterable<Path>) abis::iterator) {
                if (Files.isSymbolicLink(abi)
                        || !Files.isDirectory(abi, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try (Stream<Path> libraries = Files.list(abi)) {
                    boolean hasSo = libraries.anyMatch(path -> !Files.isSymbolicLink(path)
                            && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && path.getFileName().toString().endsWith(".so"));
                    if (hasSo) {
                        result.add(abi.getFileName().toString());
                    }
                }
            }
        }
        return result;
    }

    private static byte[] readLimited(InputStream input, long limit, String label)
            throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0L;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size += count;
            if (size > limit) {
                throw new IOException(label + "过大");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static long skipLimited(InputStream input, long limit, String label) throws IOException {
        long total = 0L;
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) {
                throw new IOException(label + "条目过大");
            }
        }
        return total;
    }

    private static String validateEntryName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0 || name.startsWith("/")
                || name.startsWith("\\") || name.contains("\\") || name.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("APK 条目路径无效：" + name);
        }
        Path normalized = java.nio.file.Paths.get(name).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..") || name.contains("//")
                || !name.equals(normalized.toString().replace('\\', '/'))) {
            throw new IllegalArgumentException("APK 条目路径越界：" + name);
        }
        return name;
    }

    private static void validateDirectoryName(String name) {
        if (name == null || !name.endsWith("/") || name.length() == 1) {
            throw new IllegalArgumentException("APK 目录条目路径无效：" + name);
        }
        validateEntryName(name.substring(0, name.length() - 1));
    }

    private static Path requireRegularFile(Path path, String label) throws IOException {
        if (path == null) {
            throw new IOException(label + "路径不能为空");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + "不存在或不是普通文件：" + normalized);
        }
        return normalized;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort for metadata-only temporary files.
                }
            });
        } catch (IOException ignored) {
            // Preserve the original metadata validation result.
        }
    }

    public static final class Metadata {
        private final String applicationId;
        private final Set<String> businessNativeAbis;

        private Metadata(String applicationId, Set<String> businessNativeAbis) {
            this.applicationId = applicationId;
            this.businessNativeAbis = java.util.Collections.unmodifiableSet(
                    new LinkedHashSet<>(businessNativeAbis));
        }

        public String getApplicationId() {
            return applicationId;
        }

        public Set<String> getBusinessNativeAbis() {
            return businessNativeAbis;
        }
    }
}
