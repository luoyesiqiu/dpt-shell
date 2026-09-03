package com.luoye.dpt.profile.archive;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Deterministic and strict JSON codec for the archive envelope. */
public final class ProfileArchiveManifestCodec {
    private static final Set<String> FIELDS = orderedSet(
            "formatVersion", "status", "applicationId", "profileId", "revisionId", "abi",
            "protocolVersion", "generatorVersion", "coreVersion", "toolchainVersion",
            "artifactSha256", "reportsSha256", "createdAtEpochMillis");

    private ProfileArchiveManifestCodec() {
    }

    public static byte[] serialize(ProfileArchiveManifest manifest) {
        validate(manifest);
        LinkedHashSet<String> ordered = new LinkedHashSet<>(FIELDS);
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
        for (String field : ordered) {
            switch (field) {
                case "formatVersion":
                    values.put(field, manifest.getFormatVersion());
                    break;
                case "status":
                    values.put(field, manifest.getStatus().name().toLowerCase(Locale.US));
                    break;
                case "applicationId":
                    values.put(field, manifest.getApplicationId());
                    break;
                case "profileId":
                    values.put(field, manifest.getProfileId());
                    break;
                case "revisionId":
                    values.put(field, manifest.getRevisionId());
                    break;
                case "abi":
                    values.put(field, manifest.getAbi());
                    break;
                case "protocolVersion":
                    values.put(field, manifest.getProtocolVersion());
                    break;
                case "generatorVersion":
                    values.put(field, manifest.getGeneratorVersion());
                    break;
                case "coreVersion":
                    values.put(field, manifest.getCoreVersion());
                    break;
                case "toolchainVersion":
                    values.put(field, manifest.getToolchainVersion());
                    break;
                case "artifactSha256":
                    values.put(field, manifest.getArtifactSha256());
                    break;
                case "reportsSha256":
                    values.put(field, manifest.getReportsSha256());
                    break;
                case "createdAtEpochMillis":
                    values.put(field, manifest.getCreatedAtEpochMillis());
                    break;
                default:
                    throw new IllegalStateException("unknown archive manifest field: " + field);
            }
        }
        return JSON.toJSONString(values, JSONWriter.Feature.WriteNulls)
                .getBytes(StandardCharsets.UTF_8);
    }

    public static ProfileArchiveManifest deserialize(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalid("manifest", "内容不能为空");
        }
        final JSONObject object;
        try {
            object = JSON.parseObject(content);
        } catch (RuntimeException exception) {
            throw invalid("manifest", "不是有效 JSON");
        }
        if (object == null) {
            throw invalid("manifest", "必须是 JSON 对象");
        }
        requireExactFields(object);
        ProfileArchiveManifest manifest = new ProfileArchiveManifest();
        manifest.setFormatVersion(requireInteger(object, "formatVersion"));
        if (manifest.getFormatVersion() != ProfileArchiveManifest.CURRENT_FORMAT_VERSION) {
            throw invalid("formatVersion", "不支持的值 " + manifest.getFormatVersion());
        }
        String status = requireString(object, "status");
        try {
            manifest.setStatus(ProfileArchiveStatus.valueOf(status.toUpperCase(Locale.US)));
        } catch (IllegalArgumentException exception) {
            throw invalid("status", "必须是 candidate 或 verified");
        }
        manifest.setApplicationId(requireString(object, "applicationId"));
        manifest.setProfileId(requireString(object, "profileId"));
        manifest.setRevisionId(requireString(object, "revisionId"));
        manifest.setAbi(requireString(object, "abi"));
        manifest.setProtocolVersion(requireInteger(object, "protocolVersion"));
        manifest.setGeneratorVersion(requireString(object, "generatorVersion"));
        manifest.setCoreVersion(requireString(object, "coreVersion"));
        manifest.setToolchainVersion(requireString(object, "toolchainVersion"));
        manifest.setArtifactSha256(requireString(object, "artifactSha256"));
        Object reports = object.get("reportsSha256");
        if (reports != null && !(reports instanceof String)) {
            throw invalid("reportsSha256", "必须是字符串或 null");
        }
        manifest.setReportsSha256((String) reports);
        manifest.setCreatedAtEpochMillis(requireLong(object, "createdAtEpochMillis"));
        validate(manifest);
        return manifest;
    }

    public static void validate(ProfileArchiveManifest manifest) {
        if (manifest == null) {
            throw invalid("manifest", "不能为空");
        }
        if (manifest.getFormatVersion() != ProfileArchiveManifest.CURRENT_FORMAT_VERSION) {
            throw invalid("formatVersion", "不支持的值 " + manifest.getFormatVersion());
        }
        if (manifest.getStatus() == null) {
            throw invalid("status", "不能为空");
        }
        ProfileModelValidator.validateApplicationId(manifest.getApplicationId());
        ProfileModelValidator.validateProfileId(manifest.getProfileId());
        ProfileModelValidator.validateRevisionId(manifest.getRevisionId());
        if (!ProfileCodeGenerator.PROFILE_ABI.equals(manifest.getAbi())) {
            throw invalid("abi", "只支持 arm64-v8a");
        }
        if (manifest.getProtocolVersion() != ProfileSchemaVersions.PROFILE_PROTOCOL) {
            throw invalid("protocolVersion", "不支持的值 " + manifest.getProtocolVersion());
        }
        requireText("generatorVersion", manifest.getGeneratorVersion());
        requireText("coreVersion", manifest.getCoreVersion());
        requireText("toolchainVersion", manifest.getToolchainVersion());
        requireSha256("artifactSha256", manifest.getArtifactSha256());
        if (manifest.getReportsSha256() != null) {
            requireSha256("reportsSha256", manifest.getReportsSha256());
        }
        if (manifest.getCreatedAtEpochMillis() <= 0L) {
            throw invalid("createdAtEpochMillis", "必须大于 0");
        }
    }

    private static void requireExactFields(JSONObject object) {
        for (String key : object.keySet()) {
            if (!FIELDS.contains(key)) {
                throw invalid("manifest." + key, "未知字段");
            }
        }
        for (String key : FIELDS) {
            if (!object.containsKey(key)) {
                throw invalid("manifest." + key, "缺少字段");
            }
        }
    }

    private static int requireInteger(JSONObject object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Integer)) {
            throw invalid(key, "必须是整数");
        }
        return (Integer) value;
    }

    private static long requireLong(JSONObject object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Number)) {
            throw invalid(key, "必须是数字");
        }
        return ((Number) value).longValue();
    }

    private static String requireString(JSONObject object, String key) {
        Object value = object.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw invalid(key, "必须是非空字符串");
        }
        return (String) value;
    }

    private static void requireText(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(key, "不能为空");
        }
    }

    private static void requireSha256(String key, String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw invalid(key, "必须是小写 SHA-256");
        }
    }

    private static Set<String> orderedSet(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    private static IllegalArgumentException invalid(String field, String reason) {
        return new IllegalArgumentException("归档清单 " + field + "：" + reason);
    }
}
