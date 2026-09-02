package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Verifies the checked-in evidence for the Profile APK delivery contract. */
public class ProfileApkDeliveryEvidenceTest {
    @Test
    public void evidenceProvesAlignedSignedAndChannelIndependentOutput() throws Exception {
        Path evidencePath = findProjectRoot().resolve(
                "verification/dpt-profile/profile-apk-delivery.json");
        Assert.assertTrue("delivery evidence is missing: " + evidencePath,
                Files.isRegularFile(evidencePath));

        JSONObject evidence = JSON.parseObject(Files.readString(evidencePath));
        Assert.assertEquals(1, evidence.getIntValue("schemaVersion"));
        Assert.assertEquals("PASSED", evidence.getString("status"));
        Assert.assertNotNull(evidence.getString("applicationId"));
        Assert.assertNotNull(evidence.getString("profileId"));
        Assert.assertNotNull(evidence.getString("revisionId"));
        Assert.assertFalse(evidence.getBooleanValue("channelInputRequired"));
        Assert.assertFalse(evidence.getBooleanValue("channelInputProvided"));

        JSONObject steps = evidence.getJSONObject("steps");
        Assert.assertNotNull(steps);
        Assert.assertEquals("PASSED", steps.getString("dptProtection"));
        Assert.assertEquals("PASSED", steps.getString("zipalign"));
        Assert.assertEquals("PASSED", steps.getString("apksigner"));
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
