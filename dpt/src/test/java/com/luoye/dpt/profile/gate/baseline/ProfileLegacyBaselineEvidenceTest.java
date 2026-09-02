package com.luoye.dpt.profile.gate.baseline;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileLegacyBaselineEvidenceTest {
    private static final String APPLICATION_ID = "com.example.dptfixture.both";
    private static final String INPUT_APK_SHA256 =
            "6f79ab7d0ec02a0bb4c0485a5090cce3d4bd3a1888443a0bb73ef15b9f9382b6";
    private static final String LEGACY_APK_SHA256 =
            "da7a8f29bb3f734e2d3c03338c4f43b8dc2c1676235f35270a4eccab1fbfe603";
    private static final String LEGACY_ARM64_SO_SHA256 =
            "fe613b21de3f57aaa9f33af112be81dff1340e470f5913fe49af267febcb7f27";

    @Test
    public void committedLegacyBaselineEvidenceIsInternallyConsistent() throws Exception {
        Path evidenceRoot = findProjectRoot().resolve(
                "verification/dpt-profile/legacy-baseline/" + APPLICATION_ID);

        ProfileBaselineReport baseline = ProfileBaselineReportCodec.deserialize(
                Files.readAllBytes(evidenceRoot.resolve("baseline-report.json")));
        Assert.assertEquals(APPLICATION_ID, baseline.getApplicationId());
        Assert.assertEquals("arm64-v8a", baseline.getAbi());
        Assert.assertTrue(baseline.isSkipSigning());
        Assert.assertEquals(INPUT_APK_SHA256, baseline.getInputApk().getSha256());
        Assert.assertEquals(13_289L, baseline.getInputApk().getBytes());
        Assert.assertEquals(LEGACY_APK_SHA256,
                baseline.getLegacyProtectedApk().getSha256());
        Assert.assertEquals(307_930L, baseline.getLegacyProtectedApk().getBytes());
        Assert.assertEquals(LEGACY_ARM64_SO_SHA256,
                baseline.getLegacyArm64So().getSha256());
        Assert.assertEquals(719_152L, baseline.getLegacyArm64So().getBytes());

        JSONObject performance = readObject(evidenceRoot.resolve("performance.json"));
        assertSamples(performance.getJSONObject("apkProtection"), 10, 860L, 1_890L);
        assertSamples(performance.getJSONObject("coldStart"), 30, 3_961L, 8_708L);
        Assert.assertEquals(126_390L,
                performance.getJSONObject("arm64Build").getLongValue("durationMillis"));

        JSONObject behavior = readObject(evidenceRoot.resolve("behavior-snapshot.json"));
        Assert.assertEquals(APPLICATION_ID, behavior.getString("applicationId"));
        Assert.assertEquals(LEGACY_APK_SHA256, behavior.getString("apkSha256"));
        Assert.assertNotNull(behavior.getJSONArray("permissions"));
        Assert.assertNotNull(behavior.getJSONArray("exportedComponents"));
        Assert.assertNotNull(behavior.getJSONArray("dependencies"));
        Assert.assertNotNull(behavior.getJSONArray("networkCapabilities"));
        Assert.assertNotNull(behavior.getJSONArray("dynamicCodeCapabilities"));

        JSONObject scanRequest = readObject(evidenceRoot.resolve("manual-scan-request.json"));
        Assert.assertEquals("PENDING_MANUAL_SCAN", scanRequest.getString("status"));
        Assert.assertEquals(APPLICATION_ID, scanRequest.getString("applicationId"));
        Assert.assertEquals(LEGACY_APK_SHA256, scanRequest.getString("apkSha256"));
    }

    private static void assertSamples(JSONObject evidence, int expectedCount,
                                      long expectedP50, long expectedP95) {
        JSONArray rawSamples = evidence.getJSONArray("samplesMillis");
        Assert.assertNotNull(rawSamples);
        List<Long> samples = new ArrayList<>(rawSamples.size());
        for (int index = 0; index < rawSamples.size(); index++) {
            samples.add(rawSamples.getLongValue(index));
        }
        Assert.assertEquals(expectedCount, samples.size());
        Assert.assertEquals(expectedP50, percentile(samples, 50));
        Assert.assertEquals(expectedP95, percentile(samples, 95));
        Assert.assertEquals(expectedP50, evidence.getLongValue("p50Millis"));
        Assert.assertEquals(expectedP95, evidence.getLongValue("p95Millis"));
    }

    private static long percentile(List<Long> samples, int percentile) {
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int rank = (sorted.size() * percentile + 99) / 100;
        return sorted.get(rank - 1);
    }

    private static JSONObject readObject(Path path) throws Exception {
        JSONObject object = JSON.parseObject(Files.readAllBytes(path));
        Assert.assertNotNull("证据文件不是 JSON 对象：" + path, object);
        return object;
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isRegularFile(current.resolve("gradlew"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("dpt-shell project root not found");
    }
}
