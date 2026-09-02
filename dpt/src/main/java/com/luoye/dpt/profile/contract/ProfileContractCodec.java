package com.luoye.dpt.profile.contract;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.validation.ProfileValidationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileContractCodec {
    private static final Set<String> CONTRACT_FIELDS = orderedSet(
            "schemaVersion",
            "protocolVersion",
            "generatorVersion",
            "profileId",
            "revisionId",
            "applicationId",
            "abis",
            "proxyApplicationClassName",
            "proxyComponentFactoryClassName",
            "jniBridgeClassName",
            "jniMethods",
            "soName",
            "shellConfigAssetName",
            "dexAssetName",
            "codeItemAssetName",
            "nativeLibAssetDirName",
            "runtimeDirName",
            "junkClassName",
            "rc4KeySymbol",
            "buildKey");
    private static final Set<String> JNI_METHOD_FIELDS = orderedSet(
            "semanticId",
            "methodName",
            "jvmSignature");

    private ProfileContractCodec() {
    }

    public static byte[] serialize(ProfileContract contract) {
        ProfileModelValidator.validate(contract);

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("schemaVersion", contract.getSchemaVersion());
        canonical.put("protocolVersion", contract.getProtocolVersion());
        canonical.put("generatorVersion", contract.getGeneratorVersion());
        canonical.put("profileId", contract.getProfileId());
        canonical.put("revisionId", contract.getRevisionId());
        canonical.put("applicationId", contract.getApplicationId());
        canonical.put("abis", new ArrayList<>(contract.getAbis()));
        canonical.put("proxyApplicationClassName", contract.getProxyApplicationClassName());
        canonical.put("proxyComponentFactoryClassName",
                contract.getProxyComponentFactoryClassName());
        canonical.put("jniBridgeClassName", contract.getJniBridgeClassName());
        canonical.put("jniMethods", canonicalJniMethods(contract.getJniMethods()));
        canonical.put("soName", contract.getSoName());
        canonical.put("shellConfigAssetName", contract.getShellConfigAssetName());
        canonical.put("dexAssetName", contract.getDexAssetName());
        canonical.put("codeItemAssetName", contract.getCodeItemAssetName());
        canonical.put("nativeLibAssetDirName", contract.getNativeLibAssetDirName());
        canonical.put("runtimeDirName", contract.getRuntimeDirName());
        canonical.put("junkClassName", contract.getJunkClassName());
        canonical.put("rc4KeySymbol", contract.getRc4KeySymbol());
        canonical.put("buildKey", contract.getBuildKey());
        return JSON.toJSONString(canonical).getBytes(StandardCharsets.UTF_8);
    }

    public static ProfileContract deserialize(byte[] content) {
        if (content == null) {
            throw invalid("profileContract", "must not be null");
        }

        JSONObject object;
        try {
            object = JSON.parseObject(content);
        } catch (RuntimeException exception) {
            throw invalid("profileContract", "invalid JSON");
        }
        if (object == null) {
            throw invalid("profileContract", "must be a JSON object");
        }

        int schemaVersion = requireInteger(object, "schemaVersion");
        if (schemaVersion != ProfileSchemaVersions.PROFILE_CONTRACT) {
            throw invalid("schemaVersion", "unsupported value " + schemaVersion);
        }
        int protocolVersion = requireInteger(object, "protocolVersion");
        if (protocolVersion != ProfileSchemaVersions.PROFILE_PROTOCOL) {
            throw invalid("protocolVersion", "unsupported value " + protocolVersion);
        }
        requireExactFields(object, CONTRACT_FIELDS, "profileContract");

        ProfileContract contract = new ProfileContract();
        contract.setSchemaVersion(schemaVersion);
        contract.setProtocolVersion(protocolVersion);
        contract.setGeneratorVersion(requireString(object, "generatorVersion"));
        contract.setProfileId(requireString(object, "profileId"));
        contract.setRevisionId(requireString(object, "revisionId"));
        contract.setApplicationId(requireString(object, "applicationId"));
        contract.setAbis(requireStringList(object, "abis"));
        contract.setProxyApplicationClassName(requireString(object,
                "proxyApplicationClassName"));
        contract.setProxyComponentFactoryClassName(requireString(object,
                "proxyComponentFactoryClassName"));
        contract.setJniBridgeClassName(requireString(object, "jniBridgeClassName"));
        contract.setJniMethods(requireJniMethods(object));
        contract.setSoName(requireString(object, "soName"));
        contract.setShellConfigAssetName(requireString(object, "shellConfigAssetName"));
        contract.setDexAssetName(requireString(object, "dexAssetName"));
        contract.setCodeItemAssetName(requireString(object, "codeItemAssetName"));
        contract.setNativeLibAssetDirName(requireString(object, "nativeLibAssetDirName"));
        contract.setRuntimeDirName(requireString(object, "runtimeDirName"));
        contract.setJunkClassName(requireString(object, "junkClassName"));
        contract.setRc4KeySymbol(requireString(object, "rc4KeySymbol"));
        contract.setBuildKey(requireString(object, "buildKey"));
        ProfileModelValidator.validate(contract);
        return contract;
    }

    private static List<Map<String, Object>> canonicalJniMethods(List<JniMethodMapping> mappings) {
        List<JniMethodMapping> sorted = new ArrayList<>(mappings);
        sorted.sort(Comparator.comparing(JniMethodMapping::getSemanticId));

        List<Map<String, Object>> canonical = new ArrayList<>(sorted.size());
        for (JniMethodMapping mapping : sorted) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("semanticId", mapping.getSemanticId());
            value.put("methodName", mapping.getMethodName());
            value.put("jvmSignature", mapping.getJvmSignature());
            canonical.add(value);
        }
        return canonical;
    }

    private static List<JniMethodMapping> requireJniMethods(JSONObject object) {
        Object raw = object.get("jniMethods");
        if (!(raw instanceof JSONArray)) {
            throw invalid("jniMethods", "must be an array");
        }

        JSONArray array = (JSONArray) raw;
        List<JniMethodMapping> mappings = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            Object item = array.get(index);
            if (!(item instanceof JSONObject)) {
                throw invalid("jniMethods[" + index + "]", "must be an object");
            }
            JSONObject mapping = (JSONObject) item;
            String path = "jniMethods[" + index + "]";
            requireExactFields(mapping, JNI_METHOD_FIELDS, path);
            mappings.add(new JniMethodMapping(
                    requireString(mapping, "semanticId", path),
                    requireString(mapping, "methodName", path),
                    requireString(mapping, "jvmSignature", path)));
        }
        return mappings;
    }

    private static List<String> requireStringList(JSONObject object, String field) {
        Object raw = object.get(field);
        if (!(raw instanceof JSONArray)) {
            throw invalid(field, "must be an array");
        }
        JSONArray array = (JSONArray) raw;
        List<String> values = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            Object item = array.get(index);
            if (!(item instanceof String)) {
                throw invalid(field + "[" + index + "]", "must be a string");
            }
            values.add((String) item);
        }
        return values;
    }

    private static int requireInteger(JSONObject object, String field) {
        Object value = object.get(field);
        if (!(value instanceof Integer)) {
            throw invalid(field, "must be an integer");
        }
        return (Integer) value;
    }

    private static String requireString(JSONObject object, String field) {
        return requireString(object, field, null);
    }

    private static String requireString(JSONObject object, String field, String parent) {
        Object value = object.get(field);
        String path = parent == null ? field : parent + "." + field;
        if (!(value instanceof String)) {
            throw invalid(path, "must be a string");
        }
        return (String) value;
    }

    private static void requireExactFields(JSONObject object, Set<String> expected, String path) {
        for (String field : object.keySet()) {
            if (!expected.contains(field)) {
                throw invalid(path + "." + field, "unknown field");
            }
        }
        for (String field : expected) {
            if (!object.containsKey(field)) {
                throw invalid(path + "." + field, "missing field");
            }
        }
    }

    private static Set<String> orderedSet(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

    private static ProfileValidationException invalid(String field, String reason) {
        return new ProfileValidationException(field + ": " + reason);
    }
}
