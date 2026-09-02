package com.luoye.dpt.profile.gate.behavior;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileLegacyBehaviorSnapshotCaptureTest {
    @Test
    public void captureLegacyBehaviorSnapshot() throws Exception {
        Path root = findProjectRoot();
        ProfileApkBehaviorSnapshot snapshot = ProfileApkBehaviorAnalyzer.analyze(
                root.resolve("build/dpt-profile-baseline/legacy-fixture-signed.apk"),
                ProfileApkBehaviorScanPolicy.empty());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("apkSha256", snapshot.getApkSha256());
        evidence.put("applicationId", snapshot.getApplicationId());
        evidence.put("permissions", snapshot.getPermissions());
        evidence.put("exportedComponents", snapshot.getExportedComponents());
        evidence.put("dependencies", snapshot.getDependencies());
        evidence.put("networkCapabilities", snapshot.getNetworkCapabilities());
        evidence.put("dynamicCodeCapabilities", snapshot.getDynamicCodeCapabilities());
        Path output = root.resolve(
                "build/dpt-profile-baseline/behavior-snapshot.generated.json");
        Files.writeString(output, JSON.toJSONString(evidence, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8);
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
