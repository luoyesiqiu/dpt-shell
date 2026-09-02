package com.luoye.dpt.profile.gate.behavior;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProfileApkBehaviorGateTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void permitsOnlyProfileProxyAndShellLibraryReplacement() throws Exception {
        Path legacy = apk("legacy.apk", manifest(
                "android.permission.INTERNET",
                "<activity android:name=\".MainActivity\" android:exported=\"true\"/>",
                "com.example.legacy.LegacyApplication",
                "com.example.legacy.LegacyFactory"), entries(
                "lib/arm64-v8a/libbusiness.so", "business",
                "lib/arm64-v8a/liblegacy.so", "android_dlopen_ext"));
        Path candidate = apk("candidate.apk", manifest(
                "android.permission.INTERNET",
                "<activity android:name=\".MainActivity\" android:exported=\"true\"/>",
                "com.example.profile.ProfileApplication",
                "com.example.profile.ProfileFactory"), entries(
                "lib/arm64-v8a/libbusiness.so", "business",
                "lib/arm64-v8a/libprofile.so", "android_dlopen_ext"));

        ProfileApkBehaviorComparisonResult result = ProfileApkBehaviorGate.evaluate(
                legacy, ProfileApkBehaviorScanPolicy.of(
                        Collections.singleton("com.example.legacy"),
                        Collections.singleton("liblegacy.so")),
                candidate, ProfileApkBehaviorScanPolicy.of(
                        Collections.singleton("com.example.profile"),
                        Collections.singleton("libprofile.so")));

        Assert.assertTrue(result.getFailureReasons().toString(), result.isPassed());
        Assert.assertTrue(result.getAddedPermissions().isEmpty());
        Assert.assertTrue(result.getAddedExportedComponents().isEmpty());
        Assert.assertTrue(result.getAddedDependencies().isEmpty());
        Assert.assertTrue(result.getAddedNetworkCapabilities().isEmpty());
        Assert.assertTrue(result.getAddedDynamicCodeCapabilities().isEmpty());
    }

    @Test
    public void rejectsNewPermissionExportedComponentDependencyAndStaticBehavior()
            throws Exception {
        Path legacy = apk("legacy-risk.apk", manifest(
                "android.permission.INTERNET",
                "<activity android:name=\".MainActivity\" android:exported=\"true\"/>",
                "com.example.legacy.LegacyApplication",
                "com.example.legacy.LegacyFactory"), entries(
                "lib/arm64-v8a/libbusiness.so", "business"));
        Path candidate = apk("candidate-risk.apk", manifest(
                "android.permission.INTERNET,android.permission.CAMERA",
                "<activity android:name=\".MainActivity\" android:exported=\"true\"/>"
                        + "<service android:name=\".RemoteService\" android:exported=\"true\"/>",
                "com.example.profile.ProfileApplication",
                "com.example.profile.ProfileFactory"), entries(
                "lib/arm64-v8a/libbusiness.so", "business",
                "lib/arm64-v8a/libtracker.so",
                "getaddrinfo android_dlopen_ext https://payload.example/code.dex"));

        ProfileApkBehaviorComparisonResult result = ProfileApkBehaviorGate.evaluate(
                legacy, ProfileApkBehaviorScanPolicy.empty(),
                candidate, ProfileApkBehaviorScanPolicy.empty());

        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.getAddedPermissions().contains("android.permission.CAMERA"));
        Assert.assertTrue(result.getAddedExportedComponents().contains(
                "service:com.example.behavior.RemoteService"));
        Assert.assertTrue(result.getAddedDependencies().contains(
                "native:arm64-v8a:libtracker.so"));
        Assert.assertTrue(result.getAddedNetworkCapabilities().contains("native-dns"));
        Assert.assertTrue(result.getAddedNetworkCapabilities().contains("embedded-http-url"));
        Assert.assertTrue(result.getAddedDynamicCodeCapabilities().contains("native-dynamic-loader"));
        Assert.assertEquals(5, result.getFailureReasons().size());
    }

    private Path apk(String name, String manifest, Map<String, String> entries) throws Exception {
        Path apk = temporaryFolder.newFile(name).toPath();
        try (OutputStream output = Files.newOutputStream(apk);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip, "AndroidManifest.xml", manifest);
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                put(zip, entry.getKey(), entry.getValue());
            }
        }
        return apk;
    }

    private static void put(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String manifest(String permissions, String components,
                                   String applicationName, String factoryName) {
        StringBuilder permissionXml = new StringBuilder();
        for (String permission : permissions.split(",")) {
            permissionXml.append("<uses-permission android:name=\"")
                    .append(permission).append("\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                + "package=\"com.example.behavior\">" + permissionXml
                + "<application android:name=\"" + applicationName
                + "\" android:appComponentFactory=\"" + factoryName + "\">"
                + components + "</application></manifest>";
    }

    private static Map<String, String> entries(String... values) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            entries.put(values[index], values[index + 1]);
        }
        return entries;
    }
}
