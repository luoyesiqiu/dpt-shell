package com.luoye.dpt.profile.archive;

import com.alibaba.fastjson2.JSON;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.HexUtils;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Secure reader and validator for portable .dptprofile archives. */
public final class ProfileArchiveReader {
    public static final String PROFILE_MANIFEST_ENTRY = "manifest.json";
    public static final String PROFILE_CONTRACT_ENTRY = "profile.json";
    public static final String REVISION_ENTRY = "revision.json";
    public static final String ARTIFACT_HASHES_ENTRY = "artifact-sha256.json";
    public static final String CHECKSUMS_ENTRY = "checksums.sha256";
    public static final String BUILD_KEY_ENTRY = "shell-files/build-key";
    public static final String SHELL_DEX_ENTRY = "shell-files/dex/classes.dex";
    public static final String SHELL_LIBS_ENTRY = "shell-files/libs/arm64-v8a";

    private static final int MAX_ENTRY_COUNT = 256;
    private static final long MAX_ENTRY_SIZE = 256L * 1024L * 1024L;
    private static final long MAX_TOTAL_SIZE = 512L * 1024L * 1024L;
    private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-f]{64})  (.+)$");

    public ProfileArchiveBundle read(Path archive) throws IOException {
        if (archive == null) {
            throw new IllegalArgumentException("Profile 归档路径不能为空");
        }
        Path extraction = Files.createTempDirectory("dpt-profile-");
        try {
            return readInternal(archive, extraction, null, true);
        } catch (IOException | RuntimeException failure) {
            deleteTree(extraction);
            throw failure;
        }
    }

    public ProfileArchiveBundle read(Path archive, Path extractionRoot) throws IOException {
        return read(archive, extractionRoot, null);
    }

    public ProfileArchiveBundle read(Path archive, Path extractionRoot,
                                     String expectedApplicationId) throws IOException {
        if (archive == null || extractionRoot == null) {
            throw new IllegalArgumentException("Profile 归档和解包目录不能为空");
        }
        Path root = prepareExtractionRoot(extractionRoot);
        try {
            return readInternal(archive, root, expectedApplicationId, false);
        } catch (IOException | RuntimeException failure) {
            deleteChildren(root);
            throw failure;
        }
    }

    public ProfileArchiveBundle readVerified(Path archive, Path extractionRoot) throws IOException {
        ProfileArchiveBundle bundle = read(archive, extractionRoot);
        if (bundle.getStatus() != ProfileArchiveStatus.VERIFIED) {
            deleteChildren(extractionRoot.toAbsolutePath().normalize());
            throw new IllegalArgumentException("candidate Profile 不能用于正式加固");
        }
        return bundle;
    }

    public ProfileArchiveBundle readVerified(Path archive, Path extractionRoot,
                                             String expectedApplicationId) throws IOException {
        ProfileArchiveBundle bundle = read(archive, extractionRoot, expectedApplicationId);
        if (bundle.getStatus() != ProfileArchiveStatus.VERIFIED) {
            deleteChildren(extractionRoot.toAbsolutePath().normalize());
            throw new IllegalArgumentException("candidate Profile 不能用于正式加固");
        }
        return bundle;
    }

    public ProfileArchiveBundle readVerified(Path archive) throws IOException {
        ProfileArchiveBundle bundle = read(archive);
        if (bundle.getStatus() != ProfileArchiveStatus.VERIFIED) {
            try {
                bundle.close();
            } catch (IOException ignored) {
                // Preserve the semantic failure below.
            }
            throw new IllegalArgumentException("candidate Profile 不能用于正式加固");
        }
        return bundle;
    }

    private static ProfileArchiveBundle readInternal(Path archive, Path extractionRoot,
                                                      String expectedApplicationId,
                                                      boolean deleteRootOnClose) throws IOException {
        Path archivePath = archive.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(archivePath)
                || !Files.isRegularFile(archivePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Profile 归档不存在或不是普通文件：" + archivePath);
        }
        Map<String, byte[]> entries = readEntries(archivePath);
        byte[] manifestBytes = requireEntry(entries, PROFILE_MANIFEST_ENTRY);
        byte[] contractBytes = requireEntry(entries, PROFILE_CONTRACT_ENTRY);
        byte[] revisionBytes = requireEntry(entries, REVISION_ENTRY);
        byte[] hashesBytes = requireEntry(entries, ARTIFACT_HASHES_ENTRY);
        byte[] checksumsBytes = requireEntry(entries, CHECKSUMS_ENTRY);

        ProfileArchiveManifest manifest = parseManifest(manifestBytes);
        validateArchiveFileName(archivePath, manifest.getStatus());
        ProfileContract contract = parseContract(contractBytes);
        ProfileRevisionManifest revision = parseRevision(revisionBytes);
        ArtifactHashManifest hashes = parseHashes(hashesBytes);
        validateIdentity(manifest, contract, revision, hashes, expectedApplicationId);
        validateEntries(entries, contract);
        validateArtifactHashes(entries, hashes, manifest);
        validateChecksums(entries, checksumsBytes);
        validateReports(entries, manifest);
        writeEntries(entries, extractionRoot);
        return new ProfileArchiveBundle(archivePath, extractionRoot, manifest, contract, revision,
                hashes, deleteRootOnClose);
    }

    private static Map<String, byte[]> readEntries(Path archive) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        rejectZipSymlinks(archive);
        long total = 0L;
        try (InputStream file = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(file, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entries.size() >= MAX_ENTRY_COUNT) {
                    throw new IOException("Profile 归档条目数量超过限制");
                }
                if (entry.isDirectory()) {
                    validateDirectoryName(entry.getName());
                    continue;
                }
                String name = validateEntryName(entry.getName());
                if (entries.containsKey(name)) {
                    throw new IOException("Profile 归档包含重复条目：" + name);
                }
                long declared = entry.getSize();
                if (declared > MAX_ENTRY_SIZE) {
                    throw new IOException("Profile 归档单文件过大：" + name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream(
                        declared > 0 && declared < Integer.MAX_VALUE ? (int) declared : 4096);
                byte[] buffer = new byte[8192];
                int count;
                long size = 0L;
                while ((count = zip.read(buffer)) != -1) {
                    size += count;
                    total += count;
                    if (size > MAX_ENTRY_SIZE || total > MAX_TOTAL_SIZE) {
                        throw new IOException("Profile 归档解压数据超过限制：" + name);
                    }
                    output.write(buffer, 0, count);
                }
                entries.put(name, output.toByteArray());
                zip.closeEntry();
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IOException("读取 Profile 归档失败：" + archive + "，原因="
                    + exception.getMessage(), exception);
        }
        return entries;
    }

    private static void rejectZipSymlinks(Path archive) throws IOException {
        try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(archive.toFile())) {
            for (net.lingala.zip4j.model.FileHeader header : zip.getFileHeaders()) {
                if (isUnixSymlink(header)) {
                    throw new IOException("Profile 归档不得包含符号链接：" + header.getFileName());
                }
            }
        } catch (net.lingala.zip4j.exception.ZipException exception) {
            throw new IOException("Profile 归档中央目录无效：" + archive, exception);
        }
    }

    private static boolean isUnixSymlink(net.lingala.zip4j.model.FileHeader header) {
        int host = (header.getVersionMadeBy() >>> 8) & 0xff;
        if (host != 3) {
            return false;
        }
        byte[] attributes = header.getExternalFileAttributes();
        if (attributes == null || attributes.length < 4) {
            return false;
        }
        long littleEndian = 0L;
        for (int index = 0; index < 4; index++) {
            littleEndian |= (attributes[index] & 0xffL) << (index * 8);
        }
        long bigEndian = 0L;
        for (int index = 0; index < 4; index++) {
            bigEndian = (bigEndian << 8) | (attributes[index] & 0xffL);
        }
        return isSymlinkMode((littleEndian >>> 16) & 0xffffL)
                || isSymlinkMode(littleEndian & 0xffffL)
                || isSymlinkMode((bigEndian >>> 16) & 0xffffL)
                || isSymlinkMode(bigEndian & 0xffffL);
    }

    private static boolean isSymlinkMode(long mode) {
        return (mode & 0xf000L) == 0xa000L;
    }

    /** Validates and returns the canonical Unix entry name. */
    public static String validateEntryName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0
                || name.startsWith("/") || name.startsWith("\\")
                || name.contains("\\") || name.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Profile 归档条目路径无效：" + name);
        }
        Path path = Paths.get(name).normalize();
        if (path.isAbsolute() || path.startsWith("..") || ".".equals(name)
                || name.contains("//") || !name.equals(path.toString().replace('\\', '/'))) {
            throw new IllegalArgumentException("Profile 归档条目路径越界：" + name);
        }
        return name;
    }

    private static void validateDirectoryName(String name) {
        if (name == null || !name.endsWith("/") || name.length() == 1) {
            throw new IllegalArgumentException("Profile 归档目录路径无效：" + name);
        }
        validateEntryName(name.substring(0, name.length() - 1));
    }

    private static ProfileArchiveManifest parseManifest(byte[] content) throws IOException {
        try {
            return ProfileArchiveManifestCodec.deserialize(content);
        } catch (RuntimeException exception) {
            throw new IOException("manifest.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ProfileContract parseContract(byte[] content) throws IOException {
        try {
            return ProfileContractCodec.deserialize(content);
        } catch (RuntimeException exception) {
            throw new IOException("profile.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ProfileRevisionManifest parseRevision(byte[] content) throws IOException {
        try {
            ProfileRevisionManifest revision = JSON.parseObject(content,
                    ProfileRevisionManifest.class);
            ProfileModelValidator.validate(revision);
            return revision;
        } catch (RuntimeException exception) {
            throw new IOException("revision.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static ArtifactHashManifest parseHashes(byte[] content) throws IOException {
        try {
            ArtifactHashManifest hashes = JSON.parseObject(content, ArtifactHashManifest.class);
            ProfileModelValidator.validate(hashes);
            return hashes;
        } catch (RuntimeException exception) {
            throw new IOException("artifact-sha256.json 无效：" + exception.getMessage(), exception);
        }
    }

    private static void validateIdentity(ProfileArchiveManifest manifest, ProfileContract contract,
                                         ProfileRevisionManifest revision,
                                         ArtifactHashManifest hashes,
                                         String expectedApplicationId) throws IOException {
        if (expectedApplicationId != null
                && !expectedApplicationId.equals(manifest.getApplicationId())) {
            throw new IllegalArgumentException("applicationId 不匹配：期望=" + expectedApplicationId
                    + "，实际=" + manifest.getApplicationId());
        }
        requireEqual("manifest/profile applicationId", manifest.getApplicationId(),
                contract.getApplicationId());
        requireEqual("manifest/revision applicationId", manifest.getApplicationId(),
                revision.getApplicationId());
        requireEqual("manifest/profile profileId", manifest.getProfileId(), contract.getProfileId());
        requireEqual("manifest/revision profileId", manifest.getProfileId(), revision.getProfileId());
        requireEqual("manifest/revision revisionId", manifest.getRevisionId(), revision.getRevisionId());
        requireEqual("manifest/hash profileId", manifest.getProfileId(), hashes.getProfileId());
        requireEqual("manifest/hash revisionId", manifest.getRevisionId(), hashes.getRevisionId());
        if (manifest.getProtocolVersion() != contract.getProtocolVersion()) {
            throw new IllegalArgumentException("Profile 协议版本不匹配");
        }
        requireEqual("manifest/profile generatorVersion", manifest.getGeneratorVersion(),
                contract.getGeneratorVersion());
        requireEqual("manifest/revision generatorVersion", manifest.getGeneratorVersion(),
                revision.getGeneratorVersion());
        requireEqual("manifest/revision coreVersion", manifest.getCoreVersion(),
                revision.getCoreVersion());
        requireEqual("manifest/revision toolchainVersion", manifest.getToolchainVersion(),
                revision.getToolchainVersion());
        if (!ProfileCodeGenerator.GENERATOR_VERSION.equals(manifest.getGeneratorVersion())
                || !ProfileCodeGenerator.IMMUTABLE_CORE_VERSION.equals(manifest.getCoreVersion())) {
            throw new IllegalArgumentException("Profile 生成器或核心版本不兼容");
        }
        if (manifest.getStatus() == ProfileArchiveStatus.CANDIDATE
                && revision.getStatus() != RevisionStatus.CANDIDATE) {
            throw new IllegalArgumentException("candidate 归档的 Revision 状态无效");
        }
        if (manifest.getStatus() == ProfileArchiveStatus.VERIFIED
                && revision.getStatus() != RevisionStatus.VERIFIED) {
            throw new IllegalArgumentException("verified 归档只能来自 VERIFIED Revision");
        }
    }

    private static void validateEntries(Map<String, byte[]> entries, ProfileContract contract)
            throws IOException {
        String soEntry = SHELL_LIBS_ENTRY + "/" + contract.getSoName();
        Set<String> expected = new LinkedHashSet<>();
        expected.add(PROFILE_MANIFEST_ENTRY);
        expected.add(PROFILE_CONTRACT_ENTRY);
        expected.add(REVISION_ENTRY);
        expected.add(ARTIFACT_HASHES_ENTRY);
        expected.add(CHECKSUMS_ENTRY);
        expected.add(BUILD_KEY_ENTRY);
        expected.add(SHELL_DEX_ENTRY);
        expected.add(soEntry);
        for (String name : entries.keySet()) {
            if (name.startsWith("reports/") && name.length() > "reports/".length()) {
                continue;
            }
            if (!expected.contains(name)) {
                throw new IOException("Profile 归档包含未允许的条目：" + name);
            }
        }
        for (String name : expected) {
            byte[] content = entries.get(name);
            if (content == null || content.length == 0) {
                throw new IOException("Profile 归档缺少或为空：" + name);
            }
        }
        long soCount = entries.keySet().stream()
                .filter(name -> name.startsWith(SHELL_LIBS_ENTRY + "/") && name.endsWith(".so"))
                .count();
        if (soCount != 1 || !entries.containsKey(soEntry)) {
            throw new IOException("Profile 归档必须包含唯一 arm64-v8a 壳 SO");
        }
        String buildKey = new String(entries.get(BUILD_KEY_ENTRY), StandardCharsets.UTF_8);
        if (!(contract.getBuildKey() + "\n").equals(buildKey)) {
            throw new IOException("Profile 归档 build-key 与契约不匹配");
        }
        validateDex(entries.get(SHELL_DEX_ENTRY), contract);
        validateArm64So(entries.get(soEntry), contract);
    }

    private static void validateArtifactHashes(Map<String, byte[]> entries,
                                                ArtifactHashManifest hashes,
                                                ProfileArchiveManifest manifest) throws IOException {
        String artifactHash = MessageDigestUtils.sha256(entries.get(ARTIFACT_HASHES_ENTRY));
        if (!artifactHash.equals(manifest.getArtifactSha256())) {
            throw new IOException("artifact-sha256.json 与 manifest 摘要不匹配");
        }
        Set<String> expected = new LinkedHashSet<>();
        expected.add(REVISION_ENTRY);
        expected.add(PROFILE_CONTRACT_ENTRY);
        expected.add(BUILD_KEY_ENTRY);
        expected.add(SHELL_DEX_ENTRY);
        expected.add(SHELL_LIBS_ENTRY + "/" + entries.keySet().stream()
                .filter(name -> name.startsWith(SHELL_LIBS_ENTRY + "/"))
                .findFirst().orElse("").substring(SHELL_LIBS_ENTRY.length() + 1));
        if (!expected.equals(hashes.getSha256().keySet())) {
            throw new IOException("artifact-sha256.json 文件集合不匹配");
        }
        for (String name : expected) {
            byte[] content = entries.get(name);
            String actual = content == null ? null : MessageDigestUtils.sha256(content);
            if (actual == null || !actual.equals(hashes.getSha256().get(name))) {
                throw new IOException("Profile 归档 SHA-256 不匹配：" + name);
            }
        }
    }

    private static void validateChecksums(Map<String, byte[]> entries, byte[] checksumBytes)
            throws IOException {
        String text = new String(checksumBytes, StandardCharsets.UTF_8);
        Map<String, String> expected = new LinkedHashMap<>();
        String[] lines = text.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (index == lines.length - 1 && line.isEmpty()) {
                continue;
            }
            Matcher matcher = CHECKSUM_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IOException("checksums.sha256 格式无效");
            }
            if (expected.put(matcher.group(2), matcher.group(1)) != null) {
                throw new IOException("checksums.sha256 包含重复条目：" + matcher.group(2));
            }
        }
        Set<String> actualEntries = new LinkedHashSet<>(entries.keySet());
        actualEntries.remove(CHECKSUMS_ENTRY);
        if (!actualEntries.equals(new LinkedHashSet<>(expected.keySet()))) {
            throw new IOException("checksums.sha256 文件集合不匹配");
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String actual = MessageDigestUtils.sha256(entries.get(entry.getKey()));
            if (!actual.equals(entry.getValue())) {
                throw new IOException("checksums.sha256 摘要不匹配：" + entry.getKey());
            }
        }
    }

    private static void validateReports(Map<String, byte[]> entries,
                                        ProfileArchiveManifest manifest) throws IOException {
        Map<String, byte[]> reports = entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("reports/"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, TreeMap::new));
        if (reports.isEmpty()) {
            // Report payloads are optional provenance for a production archive; consumability
            // is enforced by the identity, ABI, artifact-hash and DEX/SO contract checks above.
            if (manifest.getReportsSha256() != null) {
                throw new IOException("manifest 声明了不存在的报告摘要");
            }
            return;
        }
        if (manifest.getStatus() == ProfileArchiveStatus.VERIFIED) {
            String[] required = {"reports/gate-report.json", "reports/scan-baseline.json",
                    "reports/scan-candidate.json"};
            for (String name : required) {
                if (!reports.containsKey(name)) {
                    throw new IOException("verified Profile 缺少报告：" + name);
                }
            }
        }
        MessageDigest digest = sha256Digest();
        for (Map.Entry<String, byte[]> report : reports.entrySet()) {
            digest.update(report.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(report.getValue());
            digest.update((byte) 0);
        }
        String actual = HexUtils.toHexString(digest.digest());
        if (!actual.equals(manifest.getReportsSha256())) {
            throw new IOException("报告摘要与 manifest 不匹配");
        }
    }

    private static void validateDex(byte[] content, ProfileContract contract) throws IOException {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("dpt-profile-dex-", ".dex");
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            DexBackedDexFile dex = DexFileFactory.loadDexFile(temporary.toFile(),
                    Opcodes.getDefault());
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
                if (!AccessFlags.STATIC.isSet(method.getAccessFlags())
                        || !mapping.getJvmSignature().equals(methodDescriptor(method))) {
                    throw new IOException("DEX JNI 签名不匹配：" + mapping.getMethodName());
                }
            }
            if (!nativeByName.isEmpty()) {
                throw new IOException("DEX JNI Bridge 包含契约外方法："
                        + nativeByName.keySet().iterator().next());
            }
        } catch (IOException exception) {
            throw new IOException("壳 classes.dex 无法解析：" + exception.getMessage(), exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The validation result is more useful than a temporary-file cleanup error.
                }
            }
        }
    }

    private static void validateArm64So(byte[] content, ProfileContract contract) throws IOException {
        if (content.length < 20 || content[0] != 0x7f || content[1] != 'E'
                || content[2] != 'L' || content[3] != 'F' || content[4] != 2
                || content[5] != 1 || content[6] != 1
                || Byte.toUnsignedInt(content[18]) != 0xb7
                || Byte.toUnsignedInt(content[19]) != 0) {
            throw new IOException("arm64 壳 SO 不是有效的 AArch64 ELF");
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

    private static ClassDef requireDexClass(Map<String, ClassDef> classes, String className,
                                            String label) throws IOException {
        ClassDef classDef = classes.get("L" + className.replace('.', '/') + ";");
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

    private static void validateArchiveFileName(Path archive, ProfileArchiveStatus status)
            throws IOException {
        Path normalized = archive.toAbsolutePath().normalize();
        String name = normalized.getFileName() == null ? "" : normalized.getFileName().toString();
        if (status == ProfileArchiveStatus.CANDIDATE
                && !name.endsWith(ProfileArchiveWriter.CANDIDATE_SUFFIX)) {
            throw new IOException("candidate Profile 文件名必须以 "
                    + ProfileArchiveWriter.CANDIDATE_SUFFIX + " 结尾：" + normalized);
        }
        if (status == ProfileArchiveStatus.VERIFIED
                && (!name.endsWith(ProfileArchiveWriter.VERIFIED_SUFFIX)
                || name.endsWith(ProfileArchiveWriter.CANDIDATE_SUFFIX))) {
            throw new IOException("verified Profile 文件名必须以 "
                    + ProfileArchiveWriter.VERIFIED_SUFFIX + " 结尾且不能是 candidate："
                    + normalized);
        }
    }

    private static byte[] requireEntry(Map<String, byte[]> entries, String name) throws IOException {
        byte[] value = entries.get(name);
        if (value == null) {
            throw new IOException("Profile 归档缺少条目：" + name);
        }
        return value;
    }

    private static void writeEntries(Map<String, byte[]> entries, Path root) throws IOException {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            Path target = root.resolve(entry.getKey()).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Profile 归档条目越界：" + entry.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue(), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
    }

    private static Path prepareExtractionRoot(Path requested) throws IOException {
        Path root = requested.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Profile 解包目录不得是符号链接：" + root);
        }
        Files.createDirectories(root);
        try (Stream<Path> paths = Files.list(root)) {
            if (paths.findAny().isPresent()) {
                throw new IOException("Profile 解包目录必须为空：" + root);
            }
        }
        return root;
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

    private static void deleteChildren(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.filter(path -> !path.equals(root))
                    .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; never delete outside the requested root.
                }
            }
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            IOException failure = null;
            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
