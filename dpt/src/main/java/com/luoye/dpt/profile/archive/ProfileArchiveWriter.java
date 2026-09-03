package com.luoye.dpt.profile.archive;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.HexUtils;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Writes deterministic, portable .dptprofile archives. */
public final class ProfileArchiveWriter {
    public static final String CANDIDATE_SUFFIX = ".candidate.dptprofile";
    public static final String VERIFIED_SUFFIX = ".dptprofile";

    public Path packCandidate(Path bundleRoot, Path output) throws IOException {
        return pack(bundleRoot, output, ProfileArchiveStatus.CANDIDATE, null);
    }

    /**
     * Formal archives are produced only by {@link ProfileArchivePromotionService}.  Keeping this
     * method as a guarded compatibility surface prevents callers from accidentally bypassing the
     * persisted gate and scan evidence.
     */
    public Path packVerified(Path bundleRoot, Path output, Path reportsRoot) throws IOException {
        throw new IOException("verified Profile 只能由 promote 生成");
    }

    public Path pack(Path bundleRoot, Path output, ProfileArchiveStatus status)
            throws IOException {
        requireCandidateStatus(status);
        return pack(bundleRoot, output, status, null);
    }

    /**
     * Packs a legacy directory-shaped Bundle.  The source is never modified and may use the
     * historical {@code shell-files/libs/arm64} directory; the archive always uses
     * {@code arm64-v8a}.
     */
    public Path pack(Path bundleRoot, Path output, ProfileArchiveStatus status,
                     Path reportsRoot) throws IOException {
        requireCandidateStatus(status);
        return packInternal(bundleRoot, output, status, reportsRoot);
    }

    /** Called only after ProfileArchivePromotionService has evaluated all hard gates. */
    Path packVerifiedAfterPromotion(Path bundleRoot, Path output, Path reportsRoot)
            throws IOException {
        if (reportsRoot == null) {
            throw new IOException("verified Profile 必须嵌入门禁和扫描报告");
        }
        return packInternal(bundleRoot, output, ProfileArchiveStatus.VERIFIED, reportsRoot);
    }

    private static void requireCandidateStatus(ProfileArchiveStatus status) throws IOException {
        if (status == ProfileArchiveStatus.VERIFIED) {
            throw new IOException("verified Profile 只能由 promote 生成");
        }
    }

    private Path packInternal(Path bundleRoot, Path output, ProfileArchiveStatus status,
                              Path reportsRoot) throws IOException {
        if (bundleRoot == null || output == null || status == null) {
            throw new IllegalArgumentException("归档输入、输出和状态不能为空");
        }
        validateOutputSuffix(output, status);
        Path source = requireDirectory(bundleRoot, "候选 Bundle");
        SourcePayload payload = readSource(source, status, reportsRoot);
        Path target = output.toAbsolutePath().normalize();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Profile 归档输出已存在，拒绝覆盖：" + target);
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Profile 归档输出没有父目录：" + target);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
        try {
            writeArchive(temporary, payload);
            moveAtomically(temporary, target);
            return target;
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    private static SourcePayload readSource(Path source, ProfileArchiveStatus status,
                                             Path reportsRoot) throws IOException {
        Path contractPath = source.resolve(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY);
        Path revisionPath = source.resolve(ProfileArchiveReader.REVISION_ENTRY);
        Path hashesPath = source.resolve(ProfileArchiveReader.ARTIFACT_HASHES_ENTRY);
        ProfileContract contract = readContract(contractPath);
        ProfileRevisionManifest revision = readRevision(revisionPath);
        ArtifactHashManifest sourceHashes = readHashes(hashesPath);
        requireEqual("Profile/Revision applicationId", contract.getApplicationId(),
                revision.getApplicationId());
        requireEqual("Profile/Revision profileId", contract.getProfileId(), revision.getProfileId());
        requireEqual("Profile/Revision revisionId", contract.getRevisionId(), revision.getRevisionId());
        if (revision.getStatus() != RevisionStatus.CANDIDATE) {
            throw new IOException("归档只能来自 CANDIDATE Revision：" + revision.getRevisionId());
        }
        if (revision.getStatus() == RevisionStatus.REJECTED) {
            throw new IOException("REJECTED Revision 不能生成 Profile 归档：" + revision.getRevisionId());
        }

        Path dexPath = requireRegular(source.resolve(ProfileArchiveReader.SHELL_DEX_ENTRY),
                "壳 classes.dex");
        Path buildKeyPath = requireRegular(source.resolve(ProfileArchiveReader.BUILD_KEY_ENTRY),
                "build-key");
        String buildKey = Files.readString(buildKeyPath, StandardCharsets.UTF_8).trim();
        if (!contract.getBuildKey().equals(buildKey)) {
            throw new IOException("build-key 与 Profile 契约不匹配");
        }
        Path soPath = resolveSourceSo(source, contract.getSoName());
        byte[] dex = Files.readAllBytes(dexPath);
        byte[] so = Files.readAllBytes(soPath);
        if (dex.length == 0 || so.length == 0) {
            throw new IOException("壳 DEX/SO 不能为空");
        }
        validateSourceHashes(source, sourceHashes, contract.getSoName());

        ProfileRevisionManifest archiveRevision = copyRevision(revision);
        archiveRevision.setStatus(status == ProfileArchiveStatus.VERIFIED
                ? RevisionStatus.VERIFIED : RevisionStatus.CANDIDATE);
        byte[] revisionBytes = JSON.toJSONString(archiveRevision).getBytes(StandardCharsets.UTF_8);
        byte[] contractBytes = ProfileContractCodec.serialize(contract);
        byte[] buildKeyBytes = (contract.getBuildKey() + "\n").getBytes(StandardCharsets.UTF_8);

        Map<String, byte[]> entries = new TreeMap<>();
        entries.put(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY, contractBytes);
        entries.put(ProfileArchiveReader.REVISION_ENTRY, revisionBytes);
        entries.put(ProfileArchiveReader.BUILD_KEY_ENTRY, buildKeyBytes);
        entries.put(ProfileArchiveReader.SHELL_DEX_ENTRY, dex);
        String soEntry = ProfileArchiveReader.SHELL_LIBS_ENTRY + "/" + contract.getSoName();
        entries.put(soEntry, so);

        if (reportsRoot != null) {
            if (status == ProfileArchiveStatus.VERIFIED) {
                requirePromotionReports(reportsRoot);
            }
            addReports(entries, reportsRoot);
        }

        ArtifactHashManifest artifactHashes = new ArtifactHashManifest();
        artifactHashes.setProfileId(contract.getProfileId());
        artifactHashes.setRevisionId(contract.getRevisionId());
        Map<String, String> coreHashes = new LinkedHashMap<>();
        coreHashes.put(ProfileArchiveReader.REVISION_ENTRY,
                MessageDigestUtils.sha256(revisionBytes));
        coreHashes.put(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY,
                MessageDigestUtils.sha256(contractBytes));
        coreHashes.put(ProfileArchiveReader.BUILD_KEY_ENTRY,
                MessageDigestUtils.sha256(buildKeyBytes));
        coreHashes.put(ProfileArchiveReader.SHELL_DEX_ENTRY, MessageDigestUtils.sha256(dex));
        coreHashes.put(soEntry, MessageDigestUtils.sha256(so));
        artifactHashes.setSha256(coreHashes);
        ProfileModelValidator.validate(artifactHashes);
        byte[] artifactBytes = JSON.toJSONString(artifactHashes).getBytes(StandardCharsets.UTF_8);
        entries.put(ProfileArchiveReader.ARTIFACT_HASHES_ENTRY, artifactBytes);

        String reportsHash = reportsDigest(entries);
        ProfileArchiveManifest manifest = new ProfileArchiveManifest();
        manifest.setStatus(status);
        manifest.setApplicationId(contract.getApplicationId());
        manifest.setProfileId(contract.getProfileId());
        manifest.setRevisionId(contract.getRevisionId());
        manifest.setAbi(ProfileCodeGenerator.PROFILE_ABI);
        manifest.setProtocolVersion(contract.getProtocolVersion());
        manifest.setGeneratorVersion(revision.getGeneratorVersion());
        manifest.setCoreVersion(revision.getCoreVersion());
        manifest.setToolchainVersion(revision.getToolchainVersion());
        manifest.setArtifactSha256(MessageDigestUtils.sha256(artifactBytes));
        manifest.setReportsSha256(reportsHash);
        manifest.setCreatedAtEpochMillis(revision.getCreatedAtEpochMillis());
        byte[] manifestBytes = ProfileArchiveManifestCodec.serialize(manifest);
        entries.put(ProfileArchiveReader.PROFILE_MANIFEST_ENTRY, manifestBytes);

        byte[] checksumBytes = checksums(entries);
        entries.put(ProfileArchiveReader.CHECKSUMS_ENTRY, checksumBytes);
        return new SourcePayload(entries);
    }

    private static void addReports(Map<String, byte[]> entries, Path reportsRoot) throws IOException {
        Path root = reportsRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("报告目录不存在或不是普通目录：" + root);
        }
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(path -> !path.equals(root))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .collect(Collectors.toList());
        }
        for (Path path : files) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("报告目录不得包含符号链接：" + path);
            }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("报告目录包含非常规文件：" + path);
            }
            String relative = root.relativize(path).toString().replace('\\', '/');
            ProfileArchiveReader.validateEntryName("reports/" + relative);
            entries.put("reports/" + relative, Files.readAllBytes(path));
        }
    }

    private static void requirePromotionReports(Path reportsRoot) throws IOException {
        String[] required = {"gate-report.json", "scan-baseline.json", "scan-candidate.json"};
        for (String name : required) {
            Path path = reportsRoot.toAbsolutePath().normalize().resolve(name);
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("verified Profile 缺少报告：" + path);
            }
        }
    }

    private static String reportsDigest(Map<String, byte[]> entries) {
        MessageDigest digest = sha256Digest();
        boolean present = false;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().startsWith("reports/")) {
                continue;
            }
            present = true;
            digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(entry.getValue());
            digest.update((byte) 0);
        }
        return present ? HexUtils.toHexString(digest.digest()) : null;
    }

    private static byte[] checksums(Map<String, byte[]> entries) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            text.append(MessageDigestUtils.sha256(entry.getValue())).append("  ")
                    .append(entry.getKey()).append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void writeArchive(Path target, SourcePayload payload) throws IOException {
        try (OutputStream file = Files.newOutputStream(target, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
             ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : payload.entries.entrySet()) {
                byte[] bytes = entry.getValue();
                CRC32 crc = new CRC32();
                crc.update(bytes);
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setSize(bytes.length);
                zipEntry.setCompressedSize(bytes.length);
                zipEntry.setCrc(crc.getValue());
                zipEntry.setTime(0L);
                zipEntry.setExtra(new byte[0]);
                zip.putNextEntry(zipEntry);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
    }

    private static Path requireDirectory(Path path, String label) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + "必须是普通目录：" + normalized);
        }
        return normalized;
    }

    private static void validateOutputSuffix(Path output, ProfileArchiveStatus status)
            throws IOException {
        Path fileName = output.toAbsolutePath().normalize().getFileName();
        String name = fileName == null ? "" : fileName.toString();
        if (status == ProfileArchiveStatus.CANDIDATE
                && !name.endsWith(CANDIDATE_SUFFIX)) {
            throw new IOException("candidate Profile 输出必须以 " + CANDIDATE_SUFFIX + " 结尾："
                    + output);
        }
        if (status == ProfileArchiveStatus.VERIFIED
                && (!name.endsWith(VERIFIED_SUFFIX) || name.endsWith(CANDIDATE_SUFFIX))) {
            throw new IOException("verified Profile 输出必须以 " + VERIFIED_SUFFIX
                    + " 结尾且不能是 candidate：" + output);
        }
    }

    private static Path requireRegular(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + "不存在或不是普通文件：" + path);
        }
        return path;
    }

    private static Path resolveSourceSo(Path source, String soName) throws IOException {
        Path canonical = source.resolve(ProfileArchiveReader.SHELL_LIBS_ENTRY).resolve(soName);
        Path legacy = source.resolve("shell-files/libs/arm64").resolve(soName);
        Path result = Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS) ? canonical : legacy;
        requireRegular(result, "arm64 壳 SO");
        Path directory = result.getParent();
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> soFiles = stream.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".so"))
                    .collect(Collectors.toList());
            if (soFiles.size() != 1 || !soName.equals(soFiles.get(0).getFileName().toString())) {
                throw new IOException("arm64 壳 SO 必须且只能包含契约指定文件：" + directory);
            }
        }
        return result;
    }

    private static void validateSourceHashes(Path source, ArtifactHashManifest hashes,
                                             String soName) throws IOException {
        Map<String, String> expected = hashes.getSha256();
        Map<String, Path> paths = new LinkedHashMap<>();
        paths.put(ProfileArchiveReader.REVISION_ENTRY,
                source.resolve(ProfileArchiveReader.REVISION_ENTRY));
        paths.put(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY,
                source.resolve(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY));
        paths.put(ProfileArchiveReader.BUILD_KEY_ENTRY,
                source.resolve(ProfileArchiveReader.BUILD_KEY_ENTRY));
        paths.put(ProfileArchiveReader.SHELL_DEX_ENTRY,
                source.resolve(ProfileArchiveReader.SHELL_DEX_ENTRY));
        String legacySo = "shell-files/libs/arm64/" + soName;
        String canonicalSo = ProfileArchiveReader.SHELL_LIBS_ENTRY + "/" + soName;
        Path so = resolveSourceSo(source, soName);
        paths.put(so.toString().replace(source.toString() + java.io.File.separator, "")
                .replace('\\', '/'), so);
        for (Map.Entry<String, Path> entry : paths.entrySet()) {
            String key = entry.getKey();
            String actualKey = key.equals(canonicalSo) ? key : key;
            String expectedHash = expected.get(actualKey);
            if (expectedHash == null) {
                expectedHash = expected.get(legacySo);
            }
            if (expectedHash == null
                    || !expectedHash.equals(MessageDigestUtils.sha256(Files.readAllBytes(entry.getValue())))) {
                throw new IOException("候选 Bundle SHA-256 不匹配：" + key);
            }
        }
    }

    private static ProfileContract readContract(Path path) throws IOException {
        try {
            return ProfileContractCodec.deserialize(Files.readAllBytes(requireRegular(path, "profile.json")));
        } catch (RuntimeException exception) {
            throw new IOException("profile.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ProfileRevisionManifest readRevision(Path path) throws IOException {
        try {
            ProfileRevisionManifest revision = JSON.parseObject(
                    Files.readString(requireRegular(path, "revision.json")),
                    ProfileRevisionManifest.class);
            ProfileModelValidator.validate(revision);
            return revision;
        } catch (RuntimeException exception) {
            throw new IOException("revision.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ArtifactHashManifest readHashes(Path path) throws IOException {
        try {
            ArtifactHashManifest hashes = JSON.parseObject(
                    Files.readString(requireRegular(path, "artifact-sha256.json")),
                    ArtifactHashManifest.class);
            ProfileModelValidator.validate(hashes);
            return hashes;
        } catch (RuntimeException exception) {
            throw new IOException("artifact-sha256.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ProfileRevisionManifest copyRevision(ProfileRevisionManifest source) {
        return JSON.parseObject(JSON.toJSONString(source), ProfileRevisionManifest.class);
    }

    private static void requireEqual(String field, Object expected, Object actual) throws IOException {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IOException(field + " 不匹配：期望=" + expected + "，实际=" + actual);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Profile 归档不支持原子输出：" + target, exception);
        }
    }

    private static final class SourcePayload {
        private final Map<String, byte[]> entries;

        private SourcePayload(Map<String, byte[]> entries) {
            this.entries = entries;
        }
    }
}
