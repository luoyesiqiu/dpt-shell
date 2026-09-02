package com.luoye.dpt.profile.gate.behavior;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.luoye.dpt.builder.Apk;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProfileApkBehaviorDeviceIntegrationTest {
    private static final String DEVICE_SERIAL_ENV = "DPT_BEHAVIOR_DEVICE_SERIAL";
    private static final String APPLICATION_ID = "com.example.dptfixture.none";
    private static final String ACTIVITY_CLASS = "com.example.dptfixture.FixtureActivity";
    private static final String RESULT_PATH = "files/profile-behavior-result.json";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path adb;
    private String serial;
    private String previousInstallNonMarketApps;
    private final List<String> installedApplicationIds = new ArrayList<>();

    @Before
    public void requireExplicitArm64Device() throws Exception {
        serial = System.getenv(DEVICE_SERIAL_ENV);
        Assume.assumeTrue("设置 " + DEVICE_SERIAL_ENV + " 后才执行真实设备行为测试",
                serial != null && !serial.trim().isEmpty());
        serial = serial.trim();
        adb = findSdkRoot(findProjectRoot()).resolve("platform-tools/adb");
        Assert.assertTrue("找不到 adb：" + adb, Files.isExecutable(adb));
        Assert.assertEquals("arm64-v8a", adb("shell", "getprop", "ro.product.cpu.abi")
                .requireSuccess().stdout.trim());
        previousInstallNonMarketApps = adb("shell", "settings", "get", "secure",
                "install_non_market_apps").requireSuccess().stdout.trim();
        if (!"1".equals(previousInstallNonMarketApps)) {
            adb("shell", "settings", "put", "secure", "install_non_market_apps", "1")
                    .requireSuccess();
        }
    }

    @After
    public void uninstallFixture() throws Exception {
        if (adb != null && serial != null) {
            for (String applicationId : installedApplicationIds) {
                adb("uninstall", applicationId);
            }
            if (previousInstallNonMarketApps == null
                    || previousInstallNonMarketApps.isEmpty()
                    || "null".equalsIgnoreCase(previousInstallNonMarketApps)) {
                adb("shell", "settings", "delete", "secure", "install_non_market_apps");
            } else {
                adb("shell", "settings", "put", "secure", "install_non_market_apps",
                        previousInstallNonMarketApps);
            }
        }
    }

    @Test
    public void recipeAWithoutCustomComponentsRunsThroughProtectedApk() throws Exception {
        runProtectedFixture("noCustom", APPLICATION_ID, "recipe-a", false);
    }

    @Test
    public void recipeAWithApplicationAndFactoryRunsThroughProtectedApk() throws Exception {
        runProtectedFixture("applicationAndFactory", "com.example.dptfixture.both",
                "recipe-a", false);
    }

    /** Runs the four manifest declaration variants against the second complete Recipe. */
    @Test
    public void recipeBAllManifestDeclarationVariantsRunThroughProtectedApk() throws Exception {
        runAllManifestDeclarationVariants("recipe-b", false);
    }

    /** Runs every manifest declaration variant with the supported Java Overlay slot. */
    @Test
    public void recipeAWithLegalOverlayAllManifestDeclarationVariantsRunThroughProtectedApk()
            throws Exception {
        runAllManifestDeclarationVariants("recipe-a", true);
    }

    private void runAllManifestDeclarationVariants(String recipeId, boolean withOverlay)
            throws Exception {
        runProtectedFixture("noCustom", "com.example.dptfixture.none", recipeId, withOverlay);
        runProtectedFixture("applicationOnly", "com.example.dptfixture.application", recipeId,
                withOverlay);
        runProtectedFixture("componentFactoryOnly", "com.example.dptfixture.factory", recipeId,
                withOverlay);
        runProtectedFixture("applicationAndFactory", "com.example.dptfixture.both", recipeId,
                withOverlay);
    }

    private void runProtectedFixture(String flavor, String applicationId, String recipeId,
                                     boolean withOverlay) throws Exception {
        Path projectRoot = findProjectRoot();
        String suffix = flavor + "-" + recipeId + (withOverlay ? "-overlay" : "");
        Path profilesRoot = temporaryFolder.newFolder("profiles-" + suffix).toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active-" + suffix).toPath();
        Path outputApk = temporaryFolder.newFolder("output-" + suffix).toPath()
                .resolve(suffix + "-protected.apk");

        runGradle(projectRoot,
                "-PprofileStateDir=" + profilesRoot,
                "-PprofileActiveDir=" + activeProfilesRoot,
                "-PprofileApplicationId=" + applicationId,
                "profileCreate");
        String profileId = new ProfileRegistry(profilesRoot)
                .findProfileId(applicationId).orElseThrow();
        Path overlayRoot = null;
        if (withOverlay) {
            ProfileIdentityLock identity = new com.luoye.dpt.profile.storage.ProfileIdentityStore(
                    profilesRoot).loadExisting(applicationId, profileId);
            overlayRoot = temporaryFolder.newFolder("overlay-" + suffix).toPath();
            Path source = overlayRoot.resolve("java/profile-private")
                    .resolve(identity.getShellPackageName().replace('.', '/'))
                    .resolve("ProbeOverlay.java");
            Files.createDirectories(source.getParent());
            Files.writeString(source, "package " + identity.getShellPackageName() + ";\n"
                    + "final class ProbeOverlay { static int marker() { return 7; } }\n");
        }
        List<String> buildArguments = new ArrayList<>(Arrays.asList(
                "-PprofileStateDir=" + profilesRoot,
                "-PprofileActiveDir=" + activeProfilesRoot,
                "-PprofileApplicationId=" + applicationId,
                "-PprofileRecipe=" + recipeId,
                "-PprofileBuildNative=true"));
        if (overlayRoot != null) {
            buildArguments.add("-PprofileOverlayDir=" + overlayRoot);
        }
        buildArguments.add("profileBuild");
        runGradle(projectRoot,
                buildArguments.toArray(new String[0]));
        runGradle(projectRoot,
                ":test-apks:java-shell-fixture:assemble" + capitalize(flavor) + "Debug");

        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest revision = revisions.list(profileId).get(0);
        new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot)
                .publishCandidate(applicationId, profileId, revision.getRevisionId());
        revisions.markVerified(profileId, revision.getRevisionId());
        new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot, revisions)
                .activate(profileId, revision.getRevisionId());

        Path inputApk = projectRoot.resolve(
                "test-apks/java-shell-fixture/build/outputs/apk/" + flavor + "/debug/"
                        + "java-shell-fixture-" + flavor + "-debug.apk");
        Assert.assertTrue("测试 APK 未生成：" + inputApk, Files.isRegularFile(inputApk));
        Apk.Builder builder = new Apk.Builder();
        builder.profileRoots(profilesRoot, activeProfilesRoot);
        builder.filePath(inputApk.toString())
                .outputPath(outputApk.toString())
                .debuggable(true)
                // The host-side test signs with the SDK debug key after protection. The
                // shaded test runtime intentionally does not depend on dpt.jks resources.
                .sign(false);
        builder.build().protect();
        Assert.assertTrue("加固 APK 未生成：" + outputApk, Files.isRegularFile(outputApk));
        String debugOutput = System.getenv("DPT_DEBUG_PROTECTED_APK");
        if (debugOutput != null && !debugOutput.trim().isEmpty()) {
            Files.copy(outputApk, Paths.get(debugOutput.trim()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.err.println("PROTECTED_DEBUG path=" + outputApk
                    + " size=" + Files.size(outputApk));
        }

        Path signedApk = outputApk.resolveSibling("signed.apk");
        signForDevice(outputApk, signedApk);
        Files.move(signedApk, outputApk, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        installPackage(outputApk, applicationId);
        installedApplicationIds.add(applicationId);
        adb("logcat", "-c").requireSuccess();
        CommandResult launch = adb("shell", "am", "start", "-W", "-n",
                applicationId + "/" + ACTIVITY_CLASS);
        Assert.assertEquals(launch.describe(), 0, launch.exitCode);

        JSONObject result = awaitBehaviorResult(applicationId);
        Assert.assertEquals(result.toJSONString(), "passed", result.getString("status"));
        Assert.assertEquals(applicationId, result.getString("applicationId"));
        List<String> events = result.getList("events", String.class);
        for (String required : Arrays.asList(
                "activity", "service", "receiver", "provider", "class-loader")) {
            Assert.assertTrue("缺少事件 " + required + "：" + result.toJSONString(),
                    events.contains(required));
        }
        if ("applicationOnly".equals(flavor) || "applicationAndFactory".equals(flavor)) {
            Assert.assertTrue("缺少原 Application 事件：" + result.toJSONString(),
                    events.contains("application"));
        }
        // AppComponentFactory was introduced in Android 9 (API 28). Android 5-8
        // must exercise only the proxy Application chain.
        if (deviceSdk() >= 28 && ("componentFactoryOnly".equals(flavor)
                || "applicationAndFactory".equals(flavor))) {
            List<String> requiredFactoryEvents = new ArrayList<>(Arrays.asList(
                    "factory-application", "factory-activity", "factory-service",
                    "factory-receiver", "factory-provider"));
            // instantiateClassLoader was added in Android 10 (API 29); Android 9
            // must still verify the remaining AppComponentFactory callbacks.
            if (deviceSdk() >= 29) {
                requiredFactoryEvents.add("factory-class-loader");
            }
            for (String required : requiredFactoryEvents) {
                Assert.assertTrue("缺少原 AppComponentFactory 事件 " + required + "："
                        + result.toJSONString(), events.contains(required));
            }
        }
    }

    private int deviceSdk() throws Exception {
        return Integer.parseInt(adb("shell", "getprop", "ro.build.version.sdk")
                .requireSuccess().stdout.trim());
    }

    private void installPackage(Path apk, String applicationId) throws Exception {
        String remotePath = "/data/local/tmp/dpt-profile-"
                + applicationId.replace('.', '_') + "-" + System.nanoTime() + ".apk";
        try {
            // Some OEM package installers open an interactive confirmation screen for
            // `adb install`, which blocks a headless test. Push and invoke pm directly.
            adb("uninstall", applicationId);
            adb("push", apk.toString(), remotePath).requireSuccess();
            runPackageInstall(applicationId, remotePath).requireSuccess();
        } finally {
            adb("shell", "rm", "-f", remotePath);
        }
    }

    private CommandResult runPackageInstall(String applicationId, String remotePath)
            throws Exception {
        List<String> command = Arrays.asList(adb.toString(), "-s", serial, "shell", "pm",
                "install", "-r", "-t", remotePath);
        Process process = new ProcessBuilder(command).start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stdoutReader = copyAsync(process.getInputStream(), stdout);
        Thread stderrReader = copyAsync(process.getErrorStream(), stderr);
        AtomicBoolean stopInstallerCloser = new AtomicBoolean(false);
        Thread installerCloser = closeOemInstallerWhenVisible(stopInstallerCloser);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300);
        try {
            if (!process.waitFor(300, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("命令超时：" + command);
            }
        } finally {
            stopInstallerCloser.set(true);
            installerCloser.interrupt();
            installerCloser.join(2000L);
        }
        stdoutReader.join();
        stderrReader.join();
        int exitCode = process.exitValue();
        return new CommandResult(command, exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private Thread closeOemInstallerWhenVisible(AtomicBoolean stop) {
        Thread closer = new Thread(() -> {
            while (!stop.get()) {
                try {
                    CommandResult windows = adb("shell", "dumpsys", "window", "windows");
                    String output = windows.stdout + windows.stderr;
                    if (output.contains("com.oplus.appdetail")
                            || output.contains("com.android.packageinstaller")) {
                        // ColorOS may keep pm waiting for its post-install guide. Back closes
                        // the guide without querying PackageManager while install is in flight.
                        adb("shell", "input", "keyevent", "4");
                    }
                    Thread.sleep(500L);
                } catch (Exception ignored) {
                    // The main install result remains authoritative.
                }
            }
        }, "dpt-profile-installer-ui-closer");
        closer.setDaemon(true);
        closer.start();
        return closer;
    }

    private JSONObject awaitBehaviorResult(String applicationId) throws Exception {
        CommandResult latest = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            latest = adb("shell", "run-as", applicationId, "cat", RESULT_PATH);
            String output = latest.stdout.trim();
            // Android 5/6 may return the shell error text with exit code 0 while the
            // application sandbox file is still being created; only parse JSON payloads.
            if (latest.exitCode == 0 && output.startsWith("{")) {
                try {
                    return JSON.parseObject(output);
                } catch (com.alibaba.fastjson2.JSONException ignored) {
                    // Keep polling until the fixture has written a complete result.
                }
            }
            Thread.sleep(500L);
        }
        CommandResult logcat = adb("logcat", "-d", "-v", "brief", "-t", "300");
        Assert.fail("设备未生成行为结果。最后一次 run-as：\n"
                + (latest == null ? "未执行" : latest.describe())
                + "\n最近 logcat：\n" + logcat.stdout + logcat.stderr);
        return null;
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private CommandResult adb(String... arguments) throws Exception {
        String[] command = new String[arguments.length + 3];
        command[0] = adb.toString();
        command[1] = "-s";
        command[2] = serial;
        System.arraycopy(arguments, 0, command, 3, arguments.length);
        // Nested Gradle/NDK builds can exceed 90 seconds on a cold cache; keep the
        // device test bounded without treating a slow build as a protocol failure.
        return runCommand(Arrays.asList(command), findProjectRoot(), 300);
    }

    private void signForDevice(Path unsignedApk, Path signedApk) throws Exception {
        Path sdkRoot = adb.getParent().getParent();
        Path apksigner = sdkRoot.resolve("build-tools/35.0.0/apksigner");
        if (!Files.isExecutable(apksigner)) {
            throw new IOException("找不到 apksigner：" + apksigner);
        }
        Path debugKeystore = Paths.get(System.getProperty("user.home"), ".android",
                "debug.keystore");
        Assert.assertTrue("找不到 Android debug keystore：" + debugKeystore,
                Files.isRegularFile(debugKeystore));
        runCommand(Arrays.asList(apksigner.toString(), "sign", "--ks",
                        debugKeystore.toString(), "--ks-key-alias", "androiddebugkey",
                        "--ks-pass", "pass:android", "--key-pass", "pass:android",
                        "--out", signedApk.toString(), unsignedApk.toString()),
                findProjectRoot(), 90).requireSuccess();
    }

    private static void runGradle(Path projectRoot, String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectRoot.toFile())
                .withArguments(arguments)
                .forwardOutput()
                .build();
    }

    private static CommandResult runCommand(List<String> command, Path workDir,
                                            long timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stdoutReader = copyAsync(process.getInputStream(), stdout);
        Thread stderrReader = copyAsync(process.getErrorStream(), stderr);
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("命令超时：" + command);
        }
        stdoutReader.join();
        stderrReader.join();
        return new CommandResult(command, process.exitValue(),
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static Thread copyAsync(InputStream input, ByteArrayOutputStream output) {
        Thread thread = new Thread(() -> {
            try (InputStream stream = input) {
                stream.transferTo(output);
            } catch (IOException ignored) {
                // The command result still contains its exit code and the other stream.
            }
        });
        thread.start();
        return thread;
    }

    private static Path findSdkRoot(Path projectRoot) throws Exception {
        for (String name : Arrays.asList("ANDROID_SDK_ROOT", "ANDROID_HOME")) {
            String value = System.getenv(name);
            if (value != null && !value.trim().isEmpty()) {
                return Paths.get(value.trim()).toAbsolutePath().normalize();
            }
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(projectRoot.resolve("local.properties"))) {
            properties.load(input);
        }
        String sdkDir = properties.getProperty("sdk.dir");
        if (sdkDir == null || sdkDir.trim().isEmpty()) {
            throw new IOException("local.properties 缺少 sdk.dir");
        }
        return Paths.get(sdkDir.trim()).toAbsolutePath().normalize();
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

    private static final class CommandResult {
        private final List<String> command;
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private CommandResult(List<String> command, int exitCode, String stdout, String stderr) {
            this.command = command;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        private CommandResult requireSuccess() {
            Assert.assertEquals(describe(), 0, exitCode);
            return this;
        }

        private String describe() {
            return "command=" + command + ", exitCode=" + exitCode
                    + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }
}
