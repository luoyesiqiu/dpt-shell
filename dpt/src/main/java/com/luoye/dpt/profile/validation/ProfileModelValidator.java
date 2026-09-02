package com.luoye.dpt.profile.validation;

import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.ProfileSourceConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProfileModelValidator {
    private static final String ARM64_ABI = "arm64-v8a";
    private static final Pattern APPLICATION_ID = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Pattern PROFILE_ID = Pattern.compile("pf_[0-9a-f]{16}");
    private static final Pattern REVISION_ID = Pattern.compile("r[0-9]{6}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern HEX_128 = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern BUILD_KEY = Pattern.compile("[0-9a-f]{16,64}");
    private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern SEMANTIC_ID = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern NATIVE_SYMBOL = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SO_NAME = Pattern.compile("lib[A-Za-z0-9_]+\\.so");

    private ProfileModelValidator() {
    }

    public static void validateApplicationId(String value) {
        requireApplicationId(value);
    }

    public static void validateProfileId(String value) {
        requireProfileId(value);
    }

    public static void validateRevisionId(String value) {
        requireRevisionId(value);
    }

    public static void validate(ProfileSourceConfig value) {
        requireObject("profileSourceConfig", value);
        requireSchemaVersion(value.getSchemaVersion(), ProfileSchemaVersions.SOURCE_CONFIG);
        requireApplicationId(value.getApplicationId());
        requireOptionalSimpleName("recipeId", value.getRecipeId());
        requireOptionalRelativePath("overlayDir", value.getOverlayDir());
        requireArm64Only(value.getAbis());
    }

    public static void validate(ProfileIdentityLock value) {
        requireObject("profileIdentityLock", value);
        requireSchemaVersion(value.getSchemaVersion(), ProfileSchemaVersions.IDENTITY_LOCK);
        requireProfileId(value.getProfileId());
        requireApplicationId(value.getApplicationId());
        requireMatch("identitySeed", value.getIdentitySeed(), HEX_128);
        requireJavaPackage("shellPackageName", value.getShellPackageName());
        requireClassInPackage("proxyApplicationClassName", value.getProxyApplicationClassName(),
                value.getShellPackageName());
        requireClassInPackage("proxyComponentFactoryClassName",
                value.getProxyComponentFactoryClassName(), value.getShellPackageName());
        requireClassInPackage("jniBridgeClassName", value.getJniBridgeClassName(),
                value.getShellPackageName());
        requireMatch("soName", value.getSoName(), SO_NAME);
        requireSimpleName("shellConfigAssetName", value.getShellConfigAssetName());
        requireSimpleName("dexAssetName", value.getDexAssetName());
        requireSimpleName("codeItemAssetName", value.getCodeItemAssetName());
        requireSimpleName("nativeLibAssetDirName", value.getNativeLibAssetDirName());
        requireSimpleName("runtimeDirName", value.getRuntimeDirName());
        requireClassInPackage("junkClassName", value.getJunkClassName(), value.getShellPackageName());
        requireMatch("rc4KeySymbol", value.getRc4KeySymbol(), NATIVE_SYMBOL);
        requireMatch("buildKey", value.getBuildKey(), BUILD_KEY);
        requireJniMethods(value.getJniMethods());
    }

    public static void validate(ProfileRevisionManifest value) {
        requireObject("profileRevisionManifest", value);
        requireSchemaVersion(value.getSchemaVersion(), ProfileSchemaVersions.REVISION_MANIFEST);
        requireProfileId(value.getProfileId());
        requireApplicationId(value.getApplicationId());
        requireRevisionId(value.getRevisionId());
        requireObject("status", value.getStatus());
        requireSimpleName("recipeId", value.getRecipeId());
        requireSha256("overlaySha256", value.getOverlaySha256());
        requireText("generatorVersion", value.getGeneratorVersion());
        requireText("coreVersion", value.getCoreVersion());
        requireText("toolchainVersion", value.getToolchainVersion());
        if (value.getCacheKey() != null) {
            requireSha256("cacheKey", value.getCacheKey());
        }
        requireSha256("generatedSourceSha256", value.getGeneratedSourceSha256());
        if (value.getStructuralDifferenceReportSha256() != null) {
            requireSha256("structuralDifferenceReportSha256",
                    value.getStructuralDifferenceReportSha256());
        }
        if (value.getGateReportSha256() != null) {
            requireSha256("gateReportSha256", value.getGateReportSha256());
        }
        requirePositive("createdAtEpochMillis", value.getCreatedAtEpochMillis());
    }

    public static void validate(ActiveRevisionPointer value) {
        requireObject("activeRevisionPointer", value);
        requireSchemaVersion(value.getSchemaVersion(), ProfileSchemaVersions.ACTIVE_POINTER);
        requireProfileId(value.getProfileId());
        requireRevisionId(value.getRevisionId());
        requireSha256("profileManifestSha256", value.getProfileManifestSha256());
    }

    public static void validate(ProfileContract value) {
        requireObject("profileContract", value);
        requireSchemaVersion(value.getSchemaVersion(), ProfileSchemaVersions.PROFILE_CONTRACT);
        if (value.getProtocolVersion() != ProfileSchemaVersions.PROFILE_PROTOCOL) {
            fail("protocolVersion", "unsupported value " + value.getProtocolVersion());
        }
        requireText("generatorVersion", value.getGeneratorVersion());
        requireProfileId(value.getProfileId());
        requireRevisionId(value.getRevisionId());
        requireApplicationId(value.getApplicationId());
        requireArm64Only(value.getAbis());

        String shellPackageName = parentPackage("proxyApplicationClassName",
                value.getProxyApplicationClassName());
        requireClassInPackage("proxyComponentFactoryClassName",
                value.getProxyComponentFactoryClassName(), shellPackageName);
        requireClassInPackage("jniBridgeClassName", value.getJniBridgeClassName(), shellPackageName);
        requireJniMethods(value.getJniMethods());
        requireMatch("soName", value.getSoName(), SO_NAME);
        requireSimpleName("shellConfigAssetName", value.getShellConfigAssetName());
        requireSimpleName("dexAssetName", value.getDexAssetName());
        requireSimpleName("codeItemAssetName", value.getCodeItemAssetName());
        requireSimpleName("nativeLibAssetDirName", value.getNativeLibAssetDirName());
        requireSimpleName("runtimeDirName", value.getRuntimeDirName());
        requireClassInPackage("junkClassName", value.getJunkClassName(), shellPackageName);
        requireMatch("rc4KeySymbol", value.getRc4KeySymbol(), NATIVE_SYMBOL);
        requireMatch("buildKey", value.getBuildKey(), BUILD_KEY);
    }

    public static void validate(ArtifactHashManifest value) {
        requireObject("artifactHashManifest", value);
        requireSchemaVersion(value.getSchemaVersion(), ProfileSchemaVersions.ARTIFACT_HASHES);
        requireProfileId(value.getProfileId());
        requireRevisionId(value.getRevisionId());
        Map<String, String> hashes = value.getSha256();
        if (hashes == null || hashes.isEmpty()) {
            fail("sha256", "must contain at least one artifact");
        }
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            requireRelativePath("sha256 path", entry.getKey());
            requireSha256("sha256 value", entry.getValue());
        }
    }

    public static void validate(ProfileGateReport value) {
        requireObject("profileGateReport", value);
        requireSchemaVersion(value.getSchemaVersion(), ProfileSchemaVersions.GATE_REPORT);
        requireProfileId(value.getProfileId());
        requireRevisionId(value.getRevisionId());
        requireSha256("candidateApkSha256", value.getCandidateApkSha256());
        requireObject("overallStatus", value.getOverallStatus());
        requirePositive("generatedAtEpochMillis", value.getGeneratedAtEpochMillis());
        Map<String, ProfileGateReport.Result> results = value.getResults();
        if (results == null) {
            fail("results", "must not be null");
        }
        for (Map.Entry<String, ProfileGateReport.Result> entry : results.entrySet()) {
            requireSimpleName("results key", entry.getKey());
            ProfileGateReport.Result result = entry.getValue();
            requireObject("results value", result);
            requireObject("results status", result.getStatus());
            if (result.getMetrics() == null) {
                fail("results metrics", "must not be null");
            }
        }
    }

    private static void requireSchemaVersion(int actual, int expected) {
        if (actual != expected) {
            fail("schemaVersion", "expected " + expected + " but was " + actual);
        }
    }

    private static void requireApplicationId(String value) {
        requireMatch("applicationId", value, APPLICATION_ID);
    }

    private static void requireProfileId(String value) {
        requireMatch("profileId", value, PROFILE_ID);
    }

    private static void requireRevisionId(String value) {
        requireMatch("revisionId", value, REVISION_ID);
    }

    private static void requireArm64Only(List<String> abis) {
        if (abis == null || abis.size() != 1 || !ARM64_ABI.equals(abis.get(0))) {
            fail("abis", "must contain exactly arm64-v8a");
        }
    }

    private static void requireJniMethods(List<JniMethodMapping> methods) {
        if (methods == null || methods.isEmpty()) {
            fail("jniMethods", "must contain at least one mapping");
        }

        Set<String> semanticIds = new HashSet<>();
        Set<String> methodNames = new HashSet<>();
        for (JniMethodMapping method : methods) {
            if (method == null) {
                fail("jniMethods", "must not contain null mappings");
            }
            requireMatch("semanticId", method.getSemanticId(), SEMANTIC_ID);
            requireMatch("methodName", method.getMethodName(), JAVA_IDENTIFIER);
            if (!semanticIds.add(method.getSemanticId()) || !methodNames.add(method.getMethodName())) {
                fail("jniMethods", "semanticId and methodName must be unique");
            }
            if (!isValidJvmMethodDescriptor(method.getJvmSignature())) {
                fail("jvmSignature", "invalid JVM method descriptor");
            }
            ProfileJniSemantic semantic = ProfileJniSemantic.fromSemanticId(method.getSemanticId());
            if (semantic == null) {
                fail("semanticId", "unknown JNI operation " + method.getSemanticId());
            }
            if (!semantic.getJvmSignature().equals(method.getJvmSignature())) {
                fail("jvmSignature", "expected " + semantic.getJvmSignature()
                        + " for " + method.getSemanticId());
            }
        }
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            if (!semanticIds.contains(semantic.getSemanticId())) {
                fail("jniMethods", "missing semantic ID " + semantic.getSemanticId());
            }
        }
    }

    private static boolean isValidJvmMethodDescriptor(String value) {
        if (value == null || value.length() < 3 || value.charAt(0) != '(') {
            return false;
        }
        int position = 1;
        while (position < value.length() && value.charAt(position) != ')') {
            position = descriptorEnd(value, position, false);
            if (position < 0) {
                return false;
            }
        }
        if (position >= value.length() || value.charAt(position) != ')') {
            return false;
        }
        int returnEnd = descriptorEnd(value, position + 1, true);
        return returnEnd == value.length();
    }

    private static int descriptorEnd(String value, int start, boolean allowVoid) {
        if (start >= value.length()) {
            return -1;
        }
        char type = value.charAt(start);
        if ("BCDFIJSZ".indexOf(type) >= 0 || (allowVoid && type == 'V')) {
            return start + 1;
        }
        if (type == '[') {
            int componentStart = start;
            while (componentStart < value.length() && value.charAt(componentStart) == '[') {
                componentStart++;
            }
            return descriptorEnd(value, componentStart, false);
        }
        if (type != 'L') {
            return -1;
        }
        int end = value.indexOf(';', start + 1);
        if (end <= start + 1) {
            return -1;
        }
        String internalName = value.substring(start + 1, end);
        if (internalName.startsWith("/") || internalName.endsWith("/")
                || internalName.contains("//") || internalName.indexOf('.') >= 0
                || internalName.indexOf('[') >= 0) {
            return -1;
        }
        String[] segments = internalName.split("/");
        for (String segment : segments) {
            if (!JAVA_IDENTIFIER.matcher(segment).matches()) {
                return -1;
            }
        }
        return end + 1;
    }

    private static String parentPackage(String field, String className) {
        requireQualifiedClass(field, className);
        int separator = className.lastIndexOf('.');
        return className.substring(0, separator);
    }

    private static void requireClassInPackage(String field, String className, String packageName) {
        requireJavaPackage("shellPackageName", packageName);
        requireQualifiedClass(field, className);
        if (!className.startsWith(packageName + ".")) {
            fail(field, "must be inside shellPackageName");
        }
    }

    private static void requireQualifiedClass(String field, String value) {
        requireText(field, value);
        int separator = value.lastIndexOf('.');
        if (separator <= 0 || separator == value.length() - 1) {
            fail(field, "must be a fully qualified Java class name");
        }
        requireJavaPackage(field, value.substring(0, separator));
        requireMatch(field, value.substring(separator + 1), JAVA_IDENTIFIER);
    }

    private static void requireJavaPackage(String field, String value) {
        requireText(field, value);
        String[] segments = value.split("\\.", -1);
        if (segments.length < 2) {
            fail(field, "must contain at least two Java package segments");
        }
        for (String segment : segments) {
            if (!JAVA_IDENTIFIER.matcher(segment).matches()) {
                fail(field, "contains an invalid Java package segment");
            }
        }
    }

    private static void requireOptionalSimpleName(String field, String value) {
        if (value != null && !value.isEmpty()) {
            requireSimpleName(field, value);
        }
    }

    private static void requireSimpleName(String field, String value) {
        requireMatch(field, value, SIMPLE_NAME);
        if (".".equals(value) || "..".equals(value) || value.contains("..")) {
            fail(field, "must be a safe simple name");
        }
    }

    private static void requireOptionalRelativePath(String field, String value) {
        if (value != null && !value.isEmpty()) {
            requireRelativePath(field, value);
        }
    }

    private static void requireRelativePath(String field, String value) {
        requireText(field, value);
        if (value.startsWith("/") || value.startsWith("\\") || value.contains("\\")
                || value.matches("^[A-Za-z]:.*")) {
            fail(field, "must be a relative Unix-style path");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || !SIMPLE_NAME.matcher(segment).matches()) {
                fail(field, "contains an unsafe path segment");
            }
        }
    }

    private static void requireSha256(String field, String value) {
        requireMatch(field, value, SHA_256);
    }

    private static void requireMatch(String field, String value, Pattern pattern) {
        if (value == null || !pattern.matcher(value).matches()) {
            fail(field, "has invalid format");
        }
    }

    private static void requireText(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            fail(field, "must not be blank");
        }
    }

    private static void requirePositive(String field, long value) {
        if (value <= 0) {
            fail(field, "must be positive");
        }
    }

    private static void requireObject(String field, Object value) {
        if (value == null) {
            fail(field, "must not be null");
        }
    }

    private static void fail(String field, String reason) {
        throw new ProfileValidationException(field + ": " + reason);
    }
}
