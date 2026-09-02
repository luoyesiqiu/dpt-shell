package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileContractCodecTest {

    @Test
    public void serializesEquivalentContractsToIdenticalCanonicalBytes() {
        List<JniMethodMapping> mappings = mappings();
        ProfileContract first = contract(mappings);
        List<JniMethodMapping> reversed = new ArrayList<>(mappings);
        Collections.reverse(reversed);
        ProfileContract second = contract(reversed);

        byte[] firstBytes = ProfileContractCodec.serialize(first);
        byte[] secondBytes = ProfileContractCodec.serialize(second);

        Assert.assertArrayEquals(firstBytes, secondBytes);
        Assert.assertArrayEquals(firstBytes,
                ProfileContractCodec.serialize(ProfileContractCodec.deserialize(firstBytes)));
        String json = new String(firstBytes, StandardCharsets.UTF_8);
        Assert.assertTrue(json.indexOf("\"schemaVersion\"")
                < json.indexOf("\"protocolVersion\""));
        Assert.assertTrue(json.indexOf("\"profileId\"") < json.indexOf("\"jniMethods\""));
    }

    @Test
    public void normalizesUnorderedJsonAndRejectsUnknownFields() {
        byte[] canonical = ProfileContractCodec.serialize(contract(mappings()));
        JSONObject object = JSON.parseObject(canonical);
        List<String> keys = new ArrayList<>(object.keySet());
        Collections.reverse(keys);
        Map<String, Object> reversed = new LinkedHashMap<>();
        for (String key : keys) {
            reversed.put(key, object.get(key));
        }

        byte[] normalized = ProfileContractCodec.serialize(ProfileContractCodec.deserialize(
                JSON.toJSONString(reversed).getBytes(StandardCharsets.UTF_8)));
        Assert.assertArrayEquals(canonical, normalized);

        object.put("unexpected", true);
        assertInvalid("unexpected", () -> ProfileContractCodec.deserialize(
                JSON.toJSONString(object).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void rejectsUnsupportedSchemaAndProtocolVersions() {
        JSONObject object = JSON.parseObject(ProfileContractCodec.serialize(contract(mappings())));

        object.put("schemaVersion", 99);
        assertInvalid("schemaVersion", () -> ProfileContractCodec.deserialize(
                JSON.toJSONString(object).getBytes(StandardCharsets.UTF_8)));

        object.put("schemaVersion", ProfileSchemaVersions.PROFILE_CONTRACT);
        object.put("protocolVersion", 99);
        assertInvalid("protocolVersion", () -> ProfileContractCodec.deserialize(
                JSON.toJSONString(object).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void rejectsMissingJniSemanticAndFixedSignatureMismatch() {
        List<JniMethodMapping> missingSemantic = mappings();
        missingSemantic.remove(missingSemantic.size() - 1);
        assertInvalid("jniMethods", () -> ProfileContractCodec.serialize(
                contract(missingSemantic)));

        List<JniMethodMapping> signatureMismatch = mappings();
        for (JniMethodMapping mapping : signatureMismatch) {
            if (ProfileJniSemantic.INIT_APP.getSemanticId().equals(mapping.getSemanticId())) {
                mapping.setJvmSignature("(Ljava/lang/String;)V");
                break;
            }
        }
        assertInvalid("jvmSignature", () -> ProfileContractCodec.serialize(
                contract(signatureMismatch)));
    }

    private static ProfileContract contract(List<JniMethodMapping> mappings) {
        ProfileContract value = new ProfileContract();
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        value.setApplicationId("com.example.alpha");
        value.setProtocolVersion(ProfileSchemaVersions.PROFILE_PROTOCOL);
        value.setGeneratorVersion("generator-1");
        value.setAbis(Arrays.asList("arm64-v8a"));
        value.setProxyApplicationClassName("p.a1b2c3d4.Abc");
        value.setProxyComponentFactoryClassName("p.a1b2c3d4.Def");
        value.setJniBridgeClassName("p.a1b2c3d4.Ghi");
        value.setJniMethods(mappings);
        value.setSoName("libj1k2l3m4.so");
        value.setShellConfigAssetName("n1o2p3q4");
        value.setDexAssetName("r1s2t3u4.zip");
        value.setCodeItemAssetName("v1w2x3y4");
        value.setNativeLibAssetDirName("z1a2b3c4");
        value.setRuntimeDirName("d1e2f3g4");
        value.setJunkClassName("p.a1b2c3d4.Hij");
        value.setRc4KeySymbol("k1l2m3n4");
        value.setBuildKey("0123456789abcdef");
        return value;
    }

    private static List<JniMethodMapping> mappings() {
        List<JniMethodMapping> mappings = new ArrayList<>();
        int index = 0;
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            mappings.add(new JniMethodMapping(semantic.getSemanticId(), "m" + index++,
                    semantic.getJvmSignature()));
        }
        return mappings;
    }

    private static void assertInvalid(String field, Runnable action) {
        try {
            action.run();
            Assert.fail("Expected invalid Profile contract: " + field);
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(field));
        }
    }
}
