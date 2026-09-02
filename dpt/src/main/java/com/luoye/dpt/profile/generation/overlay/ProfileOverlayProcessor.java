package com.luoye.dpt.profile.generation.overlay;

import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.ProfileNativeCoreContract;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.util.HexUtils;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProfileOverlayProcessor {
    private static final String EMPTY_SHA256 = MessageDigestUtils.sha256(new byte[0]);
    private static final Pattern JAVA_PACKAGE = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\s*;");
    private static final Pattern JAVA_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][A-Za-z0-9_$.]*(?:\\.\\*)?)\\s*;");
    private static final Pattern JAVA_TYPE = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern CPP_SYSTEM_INCLUDE = Pattern.compile(
            "(?m)^\\s*#\\s*include\\s*<([^>]+)>");
    private static final Pattern CPP_LOCAL_INCLUDE = Pattern.compile(
            "(?m)^\\s*#\\s*include\\s*\"([^\"]+)\"");
    private static final Pattern MANIFEST_CONTENT = Pattern.compile(
            "(?is)<\\s*(?:manifest|uses-permission|permission|application|activity|service|receiver|provider)\\b");
    private static final List<Pattern> DYNAMIC_CODE_PATTERNS = patterns(
            "\\bDexClassLoader\\b", "\\bInMemoryDexClassLoader\\b",
            "\\bPathClassLoader\\b", "\\bBaseDexClassLoader\\b",
            "\\bURLClassLoader\\b", "\\bloadDex\\s*\\(", "\\bdefineClass\\s*\\(",
            "\\bdlopen\\s*\\(", "\\bandroid_dlopen_ext\\b", "\\bdlsym\\s*\\(");
    private static final List<Pattern> NETWORK_PATTERNS = caseInsensitivePatterns(
            "\\bjava\\.net\\.", "\\bjavax\\.net\\.", "\\bandroid\\.net\\.",
            "\\bokhttp(?:3)?\\b", "\\bretrofit(?:2)?\\b", "\\borg\\.apache\\.http\\b",
            "\\bHttpURLConnection\\b", "\\bURLConnection\\b", "\\bDownloadManager\\b",
            "\\bCronet\\b", "\\bServerSocket\\b", "\\bDatagramSocket\\b",
            "\\bsocket\\s*\\(", "\\bconnect\\s*\\(", "\\bgetaddrinfo\\s*\\(",
            "\\b(?:send|recv)\\s*\\(", "https?://", "<sys/socket\\.h>",
            "<netinet/", "<arpa/inet\\.h>", "<curl/");
    private static final Set<String> ALLOWED_CPP_SYSTEM_HEADERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "algorithm", "array", "atomic", "chrono", "cstddef", "cstdint", "cstdio",
                    "cstdlib", "cstring", "functional", "jni.h", "limits", "map", "memory",
                    "mutex", "optional", "set", "string", "string_view", "tuple", "type_traits",
                    "unordered_map", "utility", "vector", "errno.h", "fcntl.h", "limits.h",
                    "stddef.h", "stdint.h", "stdio.h", "stdlib.h", "string.h", "sys/stat.h",
                    "sys/types.h", "unistd.h")));

    private final ProfileOverlayContract contract;
    private final ProfileNativeCoreContract nativeCoreContract;
    private final List<NativeCorePattern> nativeCorePatterns;

    public ProfileOverlayProcessor() {
        this(ProfileOverlayContract.builtIn(), ProfileNativeCoreContract.builtIn());
    }

    ProfileOverlayProcessor(ProfileOverlayContract contract) {
        this(contract, ProfileNativeCoreContract.builtIn());
    }

    ProfileOverlayProcessor(ProfileOverlayContract contract,
                            ProfileNativeCoreContract nativeCoreContract) {
        if (contract == null || nativeCoreContract == null) {
            throw new IllegalArgumentException("Overlay contracts must not be null");
        }
        this.contract = contract;
        this.nativeCoreContract = nativeCoreContract;
        this.nativeCorePatterns = nativeCorePatterns(nativeCoreContract);
    }

    public PreparedOverlay prepare(Path overlayRoot, ProfileIdentityLock identity) throws IOException {
        if (identity == null) {
            throw new IllegalArgumentException("Profile identity must not be null");
        }
        if (overlayRoot == null) {
            return new PreparedOverlay(Collections.emptyList(), EMPTY_SHA256);
        }
        Path normalizedRoot = overlayRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedRoot) || !Files.isDirectory(normalizedRoot)) {
            throw new ProfileOverlayException("Overlay 目录不存在或不是普通目录：" + normalizedRoot);
        }

        List<Path> paths;
        try (Stream<Path> stream = Files.walk(normalizedRoot)) {
            paths = stream.sorted(Comparator.comparing(path -> unixPath(normalizedRoot.relativize(path))))
                    .collect(Collectors.toList());
        }
        List<OverlayFile> files = new ArrayList<>();
        Map<String, String> targetOwners = new LinkedHashMap<>();
        Map<String, String> javaTypeOwners = new LinkedHashMap<>();
        for (Path path : paths) {
            if (path.equals(normalizedRoot)) {
                continue;
            }
            String sourceRelative = unixPath(normalizedRoot.relativize(path));
            if (Files.isSymbolicLink(path)) {
                throw new ProfileOverlayException("Overlay 不允许符号链接或特殊文件："
                        + sourceRelative);
            }
            if (Files.isDirectory(path)) {
                continue;
            }
            if (!Files.isRegularFile(path)) {
                throw new ProfileOverlayException("Overlay 不允许符号链接或特殊文件："
                        + sourceRelative);
            }
            byte[] content = Files.readAllBytes(path);
            rejectMetadataMutation(sourceRelative, content);
            ProfileOverlayContract.ExtensionPoint extensionPoint = findExtensionPoint(sourceRelative);
            String suffix = matchingSuffix(sourceRelative, extensionPoint.getSourceSuffixes());
            if (suffix == null) {
                throw new ProfileOverlayException("Overlay 文件类型不在白名单：" + sourceRelative);
            }
            String targetRelative = sourceRelative.substring(
                    extensionPoint.getRelativeDirectory().length() + 1);
            if (targetRelative.isEmpty()) {
                throw new ProfileOverlayException("Overlay 扩展接口目录中缺少源码文件："
                        + sourceRelative);
            }
            rejectNativeCoreSourcePath(extensionPoint, targetRelative, sourceRelative);
            String source = new String(content, StandardCharsets.UTF_8);
            validateSource(extensionPoint, targetRelative, source, identity);
            Set<String> declaredJavaTypes = extensionPoint.getLanguage()
                    == ProfileOverlayContract.Language.JAVA
                    ? declaredJavaTypes(source, targetRelative) : Collections.emptySet();
            String targetKey = extensionPoint.getLanguage().name() + ":" + targetRelative;
            String previous = targetOwners.put(targetKey, sourceRelative);
            if (previous != null) {
                throw new ProfileOverlayException("Overlay 生成文件冲突：" + previous + " 与 "
                        + sourceRelative + " -> " + targetRelative);
            }
            for (String declaredJavaType : declaredJavaTypes) {
                String previousTypeOwner = javaTypeOwners.put(declaredJavaType, sourceRelative);
                if (previousTypeOwner != null) {
                    throw new ProfileOverlayException("Overlay 生成文件冲突："
                            + previousTypeOwner + " 与 " + sourceRelative + " 声明 "
                            + declaredJavaType);
                }
            }
            files.add(new OverlayFile(sourceRelative, targetRelative, extensionPoint.getLanguage(),
                    content, source, declaredJavaTypes));
        }
        validateLocalCppIncludes(files);
        return new PreparedOverlay(files, hash(files));
    }

    private ProfileOverlayContract.ExtensionPoint findExtensionPoint(String relativePath)
            throws ProfileOverlayException {
        for (ProfileOverlayContract.ExtensionPoint extensionPoint : contract.getExtensionPoints()) {
            if (relativePath.startsWith(extensionPoint.getRelativeDirectory() + "/")) {
                return extensionPoint;
            }
        }
        throw new ProfileOverlayException("Overlay 扩展接口目录未登记：" + relativePath);
    }

    private static String matchingSuffix(String path, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (path.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }

    private static void rejectMetadataMutation(String relativePath, byte[] content)
            throws ProfileOverlayException {
        String lowerPath = relativePath.toLowerCase(Locale.US);
        String source = new String(content, StandardCharsets.UTF_8);
        if (lowerPath.endsWith("androidmanifest.xml") || MANIFEST_CONTENT.matcher(source).find()
                || source.contains("android.permission.")) {
            throw new ProfileOverlayException("Overlay 禁止修改 Manifest 权限/组件："
                    + relativePath);
        }
        if (lowerPath.endsWith("build.gradle") || lowerPath.endsWith("build.gradle.kts")
                || lowerPath.endsWith("settings.gradle")
                || lowerPath.endsWith("settings.gradle.kts") || lowerPath.endsWith("pom.xml")
                || lowerPath.endsWith("libs.versions.toml") || lowerPath.endsWith(".jar")
                || lowerPath.endsWith(".aar") || lowerPath.endsWith(".so")
                || lowerPath.endsWith(".a")) {
            throw new ProfileOverlayException("Overlay 禁止新增依赖或二进制产物："
                    + relativePath);
        }
    }

    private void validateSource(ProfileOverlayContract.ExtensionPoint extensionPoint,
                                String targetRelative, String source,
                                ProfileIdentityLock identity)
            throws ProfileOverlayException {
        rejectImmutableNativeCoreSymbol(targetRelative, source);
        if (extensionPoint.getLanguage() == ProfileOverlayContract.Language.JAVA
                && Pattern.compile("\\bnative\\b").matcher(source).find()) {
            throw new ProfileOverlayException("Overlay 使用禁用 API/符号：" + targetRelative);
        }
        rejectPattern(source, DYNAMIC_CODE_PATTERNS,
                "Overlay 禁止远程动态代码加载：" + targetRelative);

        if (extensionPoint.getLanguage() == ProfileOverlayContract.Language.JAVA) {
            validateJavaSource(targetRelative, source, identity);
            rejectPattern(source, NETWORK_PATTERNS,
                    "Overlay 禁止网络能力：" + targetRelative);
        } else {
            rejectPattern(source, NETWORK_PATTERNS,
                    "Overlay 禁止网络能力：" + targetRelative);
            validateCppSystemIncludes(targetRelative, source);
        }
    }

    private void rejectNativeCoreSourcePath(ProfileOverlayContract.ExtensionPoint extensionPoint,
                                            String targetRelative, String sourceRelative)
            throws ProfileOverlayException {
        if (extensionPoint.getLanguage() != ProfileOverlayContract.Language.CPP) {
            return;
        }
        for (ProfileNativeCoreContract.CoreBoundary boundary
                : nativeCoreContract.getBoundaries()) {
            if (boundary.getSourcePaths().contains(targetRelative)) {
                throw new ProfileOverlayException("Overlay 禁止覆盖不可变 Native 核心源码："
                        + sourceRelative + " -> " + targetRelative + "，能力="
                        + boundary.getCapability());
            }
        }
    }

    private void rejectImmutableNativeCoreSymbol(String targetRelative, String source)
            throws ProfileOverlayException {
        for (NativeCorePattern corePattern : nativeCorePatterns) {
            if (corePattern.pattern.matcher(source).find()) {
                throw new ProfileOverlayException("Overlay 使用禁用 API/符号："
                        + targetRelative + "，不可变 Native 核心能力="
                        + corePattern.capability + "，符号=" + corePattern.symbol);
            }
        }
    }

    private static List<NativeCorePattern> nativeCorePatterns(
            ProfileNativeCoreContract nativeCoreContract) {
        List<NativeCorePattern> result = new ArrayList<>();
        for (ProfileNativeCoreContract.CoreBoundary boundary
                : nativeCoreContract.getBoundaries()) {
            for (ProfileNativeCoreContract.ProtectedSymbol protectedSymbol
                    : boundary.getProtectedSymbols()) {
                String suffix = protectedSymbol.getMatch()
                        == ProfileNativeCoreContract.Match.PREFIX ? "[A-Za-z0-9_]*" : "";
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(
                        protectedSymbol.getName()) + suffix + "\\b");
                result.add(new NativeCorePattern(boundary.getCapability(),
                        protectedSymbol.getName(), pattern));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static void validateJavaSource(String targetRelative, String source,
                                           ProfileIdentityLock identity)
            throws ProfileOverlayException {
        Matcher packageMatcher = JAVA_PACKAGE.matcher(sanitizeJava(source));
        if (!packageMatcher.find()) {
            throw new ProfileOverlayException("Overlay Java 源码缺少 Profile 专属 package："
                    + targetRelative);
        }
        String packageName = packageMatcher.group(1);
        String shellPackage = identity.getShellPackageName();
        if (!packageName.equals(shellPackage) && !packageName.startsWith(shellPackage + ".")) {
            throw new ProfileOverlayException("Overlay Java package 不属于当前 Profile："
                    + packageName);
        }
        Path targetPath = Path.of(targetRelative).normalize();
        Path parent = targetPath.getParent();
        String expectedParent = packageName.replace('.', '/');
        if (parent == null || !unixPath(parent).equals(expectedParent)) {
            throw new ProfileOverlayException("Overlay Java package 与允许目录路径不一致："
                    + targetRelative);
        }

        Matcher importMatcher = JAVA_IMPORT.matcher(source);
        while (importMatcher.find()) {
            String imported = importMatcher.group(1);
            if (!imported.startsWith("java.") && !imported.startsWith("javax.")
                    && !imported.startsWith("android.") && !imported.equals(shellPackage)
                    && !imported.startsWith(shellPackage + ".")) {
                throw new ProfileOverlayException("Overlay 禁止新增依赖：" + imported);
            }
        }
    }

    private static Set<String> declaredJavaTypes(String source, String targetRelative)
            throws ProfileOverlayException {
        String sanitized = sanitizeJava(source);
        Matcher packageMatcher = JAVA_PACKAGE.matcher(sanitized);
        if (!packageMatcher.find()) {
            throw new ProfileOverlayException("Overlay Java 源码缺少 Profile 专属 package："
                    + targetRelative);
        }
        String packageName = packageMatcher.group(1);
        LinkedHashMap<String, Boolean> names = new LinkedHashMap<>();
        Matcher typeMatcher = JAVA_TYPE.matcher(sanitized);
        int cursor = 0;
        int braceDepth = 0;
        while (typeMatcher.find()) {
            for (int index = cursor; index < typeMatcher.start(); index++) {
                char value = sanitized.charAt(index);
                if (value == '{') {
                    braceDepth++;
                } else if (value == '}' && braceDepth > 0) {
                    braceDepth--;
                }
            }
            if (braceDepth == 0) {
                names.put(packageName + "." + typeMatcher.group(1), Boolean.TRUE);
            }
            cursor = typeMatcher.end();
        }
        if (names.isEmpty()) {
            throw new ProfileOverlayException("Overlay Java 源码缺少顶层类型：" + targetRelative);
        }
        return Collections.unmodifiableSet(names.keySet());
    }

    private static String sanitizeJava(String source) {
        StringBuilder result = new StringBuilder(source.length());
        int state = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (state == 0) {
                if (current == '/' && next == '/') {
                    result.append("  ");
                    index++;
                    state = 1;
                } else if (current == '/' && next == '*') {
                    result.append("  ");
                    index++;
                    state = 2;
                } else if (current == '"') {
                    result.append(' ');
                    state = 3;
                } else if (current == '\'') {
                    result.append(' ');
                    state = 4;
                } else {
                    result.append(current);
                }
            } else if (state == 1) {
                if (current == '\n' || current == '\r') {
                    result.append(current);
                    state = 0;
                } else {
                    result.append(' ');
                }
            } else if (state == 2) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    index++;
                    state = 0;
                } else {
                    result.append(current == '\n' || current == '\r' ? current : ' ');
                }
            } else {
                if (current == '\\' && next != '\0') {
                    result.append("  ");
                    index++;
                } else if ((state == 3 && current == '"')
                        || (state == 4 && current == '\'')) {
                    result.append(' ');
                    state = 0;
                } else {
                    result.append(current == '\n' || current == '\r' ? current : ' ');
                }
            }
        }
        return result.toString();
    }

    private static void validateCppSystemIncludes(String targetRelative, String source)
            throws ProfileOverlayException {
        Matcher matcher = CPP_SYSTEM_INCLUDE.matcher(source);
        while (matcher.find()) {
            String include = matcher.group(1).trim();
            if (!ALLOWED_CPP_SYSTEM_HEADERS.contains(include) && !include.startsWith("android/")) {
                throw new ProfileOverlayException("Overlay 禁止新增依赖：" + include
                        + "（" + targetRelative + "）");
            }
        }
    }

    private static void validateLocalCppIncludes(List<OverlayFile> files)
            throws ProfileOverlayException {
        Set<String> headers = files.stream()
                .filter(file -> file.language == ProfileOverlayContract.Language.CPP)
                .filter(file -> file.targetRelative.endsWith(".h")
                        || file.targetRelative.endsWith(".hpp"))
                .map(file -> file.targetRelative)
                .collect(Collectors.toSet());
        for (OverlayFile file : files) {
            if (file.language != ProfileOverlayContract.Language.CPP) {
                continue;
            }
            Matcher matcher = CPP_LOCAL_INCLUDE.matcher(file.source);
            while (matcher.find()) {
                String include = matcher.group(1).trim().replace('\\', '/');
                if ("profile_config.h".equals(include)) {
                    continue;
                }
                Path ownDirectory = Path.of(file.targetRelative).getParent();
                Path fromOwnDirectory = (ownDirectory == null ? Path.of(include)
                        : ownDirectory.resolve(include)).normalize();
                Path fromRoot = Path.of(include).normalize();
                if (fromOwnDirectory.isAbsolute() || fromRoot.isAbsolute()
                        || unixPath(fromOwnDirectory).startsWith("../")
                        || unixPath(fromRoot).startsWith("../")
                        || (!headers.contains(unixPath(fromOwnDirectory))
                        && !headers.contains(unixPath(fromRoot)))) {
                    throw new ProfileOverlayException("Overlay 禁止新增依赖：" + include
                            + "（" + file.sourceRelative + "）");
                }
            }
        }
    }

    private static void rejectPattern(String source, List<Pattern> patterns, String message)
            throws ProfileOverlayException {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(source).find()) {
                throw new ProfileOverlayException(message + "，命中=" + pattern.pattern());
            }
        }
    }

    private static List<Pattern> patterns(String... expressions) {
        List<Pattern> result = new ArrayList<>();
        for (String expression : expressions) {
            result.add(Pattern.compile(expression));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Pattern> caseInsensitivePatterns(String... expressions) {
        List<Pattern> result = new ArrayList<>();
        for (String expression : expressions) {
            result.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
        }
        return Collections.unmodifiableList(result);
    }

    private static String hash(List<OverlayFile> files) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        for (OverlayFile file : files) {
            digest.update(file.sourceRelative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(file.content);
            digest.update((byte) 0);
        }
        return HexUtils.toHexString(digest.digest());
    }

    private static String unixPath(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    public static final class PreparedOverlay {
        private final List<OverlayFile> files;
        private final String sha256;

        private PreparedOverlay(List<OverlayFile> files, String sha256) {
            this.files = Collections.unmodifiableList(new ArrayList<>(files));
            this.sha256 = sha256;
        }

        public String getSha256() {
            return sha256;
        }

        public int getFileCount() {
            return files.size();
        }

        /**
         * Returns the relative C++ translation units that must be compiled for this overlay.
         * Headers are intentionally excluded from the CMake source list.
         */
        public List<String> getCppSourcePaths() {
            List<String> result = new ArrayList<>();
            for (OverlayFile file : files) {
                if (file.language == ProfileOverlayContract.Language.CPP
                        && file.targetRelative.endsWith(".cpp")) {
                    result.add(file.targetRelative);
                }
            }
            return Collections.unmodifiableList(result);
        }

        public void copyTo(ProfileCodeGenerator.GenerationLayout layout) throws IOException {
            if (layout == null) {
                throw new IllegalArgumentException("Generation layout must not be null");
            }
            Set<String> generatedJavaTypes = generatedJavaTypes(layout.getJavaDirectory());
            for (OverlayFile file : files) {
                for (String declaredJavaType : file.declaredJavaTypes) {
                    if (generatedJavaTypes.contains(declaredJavaType)) {
                        throw new ProfileOverlayException("Overlay 生成文件冲突："
                                + file.sourceRelative + " 声明已生成类型 " + declaredJavaType);
                    }
                }
            }

            List<TargetFile> targets = new ArrayList<>();
            for (OverlayFile file : files) {
                Path root = file.language == ProfileOverlayContract.Language.JAVA
                        ? layout.getJavaDirectory() : layout.getCppDirectory();
                Path normalizedRoot = root.toAbsolutePath().normalize();
                Path target = normalizedRoot.resolve(file.targetRelative).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    throw new ProfileOverlayException("Overlay 目标路径越界："
                            + file.sourceRelative);
                }
                if (Files.exists(target)) {
                    throw new ProfileOverlayException("Overlay 生成文件冲突："
                            + file.sourceRelative + " -> " + target);
                }
                targets.add(new TargetFile(file, target));
            }
            for (TargetFile target : targets) {
                Files.createDirectories(target.path.getParent());
                Files.write(target.path, target.file.content, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            }
        }

        private static Set<String> generatedJavaTypes(Path javaDirectory) throws IOException {
            Set<String> result = new HashSet<>();
            if (!Files.isDirectory(javaDirectory)) {
                return result;
            }
            List<Path> sources;
            try (Stream<Path> paths = Files.walk(javaDirectory)) {
                sources = paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }
            for (Path source : sources) {
                result.addAll(declaredJavaTypes(Files.readString(source, StandardCharsets.UTF_8),
                        unixPath(javaDirectory.relativize(source))));
            }
            return result;
        }
    }

    private static final class OverlayFile {
        private final String sourceRelative;
        private final String targetRelative;
        private final ProfileOverlayContract.Language language;
        private final byte[] content;
        private final String source;
        private final Set<String> declaredJavaTypes;

        private OverlayFile(String sourceRelative, String targetRelative,
                            ProfileOverlayContract.Language language, byte[] content,
                            String source, Set<String> declaredJavaTypes) {
            this.sourceRelative = sourceRelative;
            this.targetRelative = targetRelative;
            this.language = language;
            this.content = content.clone();
            this.source = source;
            this.declaredJavaTypes = Collections.unmodifiableSet(
                    new HashSet<>(declaredJavaTypes));
        }
    }

    private static final class TargetFile {
        private final OverlayFile file;
        private final Path path;

        private TargetFile(OverlayFile file, Path path) {
            this.file = file;
            this.path = path;
        }
    }

    private static final class NativeCorePattern {
        private final ProfileNativeCoreContract.Capability capability;
        private final String symbol;
        private final Pattern pattern;

        private NativeCorePattern(ProfileNativeCoreContract.Capability capability,
                                  String symbol, Pattern pattern) {
            this.capability = capability;
            this.symbol = symbol;
            this.pattern = pattern;
        }
    }
}
