package com.luoye.dpt.profile.generation;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Verifies the build-script boundary for Profile native candidates without requiring an NDK.
 */
public class ProfileNativeBuildConfigurationTest {

    @Test
    public void profileNativeBuildUsesArm64AndIsolatedRevisionDirectories() throws Exception {
        String profileGradle = readProjectFile("gradle/profile.gradle");
        String shellGradle = readProjectFile("shell/build.gradle");

        String profileBuild = section(profileGradle, "tasks.named(\"profileBuild\").configure",
                "tasks.named(\"profilePublish\").configure");
        Assert.assertTrue(profileBuild.contains("runProfileNativeBuild"));
        Assert.assertTrue(profileGradle.contains("profileNativeCmakeDir"));
        Assert.assertTrue(profileGradle.contains("profileNativeCacheDir"));
        Assert.assertTrue(profileGradle.contains("profileNativeOutputDir"));
        Assert.assertTrue(profileGradle.contains("\"cache/"));
        Assert.assertTrue(profileGradle.contains("\"candidates/"));
        Assert.assertTrue(profileGradle.contains("profileBuildNative"));
        Assert.assertTrue(profileBuild.contains("PROFILE_NATIVE_BUILD_DEFERRED"));
        Assert.assertFalse(profileBuild.contains("shell-files"));

        String nativeBuild = section(shellGradle, "tasks.register(\"profileNativeBuild\")",
                "def afterAssembleCopy");
        Assert.assertTrue(nativeBuild.contains("stripReleaseDebugSymbols"));
        Assert.assertTrue(nativeBuild.contains("stripped_native_libs"));
        Assert.assertTrue(nativeBuild.contains("profileNativeOutputDir"));
        Assert.assertTrue(nativeBuild.contains("minifyReleaseWithR8"));
        Assert.assertTrue(nativeBuild.contains("classes.dex"));
        Assert.assertTrue(nativeBuild.contains("libs/arm64"));
        Assert.assertTrue(nativeBuild.contains("build-key"));
        Assert.assertTrue(nativeBuild.contains("arm64-v8a"));
        Assert.assertFalse(nativeBuild.contains("executable/shell-files"));

        String profileConfiguration = section(shellGradle,
                "def profileNativeBuild =", "android {");
        Assert.assertTrue(profileConfiguration.contains("profileNativeCmakeDir"));
        Assert.assertTrue(profileConfiguration.contains("profileNativeCacheDir"));
        Assert.assertTrue(profileConfiguration.contains("profileNativeOutputDir"));
        Assert.assertTrue(profileConfiguration.contains("profileManifestFile"));
        Assert.assertTrue(profileConfiguration.contains("DPT_PROFILE_SO_FILE"));
        Assert.assertTrue(profileConfiguration.contains("profileSoFile"));
        Assert.assertTrue(shellGradle.contains("resolvedShellSoName"));
        String buildConfigLine = null;
        for (String line : shellGradle.split("\\R")) {
            if (line.contains("buildConfigField") && line.contains("\"SO_NAME\"")) {
                buildConfigLine = line;
                break;
            }
        }
        Assert.assertNotNull("Profile SO name must feed BuildConfig.SO_NAME", buildConfigLine);
        Assert.assertTrue(buildConfigLine.contains("resolvedShellSoName"));
        String releaseConfiguration = section(shellGradle, "release {", "debug {");
        int profileMode = releaseConfiguration.indexOf("if (profileNativeBuild)");
        int profileRules = releaseConfiguration.indexOf("proguardFiles profileR8RulesFile");
        int legacyRules = releaseConfiguration.indexOf("'proguard-rules.pro'");
        Assert.assertTrue(profileMode >= 0 && profileRules > profileMode
                && legacyRules > profileRules);
        Assert.assertTrue(shellGradle.contains("canonicalFile"));
        Assert.assertTrue(shellGradle.contains("legacyShellFiles"));
        Assert.assertTrue(shellGradle.contains("isWithin"));
    }

    @Test
    public void cmakeConsumesGeneratedProfileContractAndRejectsNonArm64() throws Exception {
        String cmake = readProjectFile("shell/src/main/cpp/CMakeLists.txt");

        Assert.assertTrue(cmake.contains("DPT_PROFILE_CMAKE_DIR is required"));
        Assert.assertTrue(cmake.contains("profile-parameters.cmake"));
        Assert.assertTrue(cmake.contains("profile-sources.cmake"));
        Assert.assertTrue(cmake.contains("DPT_PROFILE_ABI STREQUAL \"arm64-v8a\""));
        Assert.assertTrue(cmake.contains("CMAKE_ANDROID_ARCH_ABI STREQUAL DPT_PROFILE_ABI"));
        Assert.assertTrue(cmake.contains("DPT_PROFILE_GENERATED_INCLUDE_DIR"));
        Assert.assertTrue(cmake.contains("DPT_PROFILE_GENERATED_SOURCES"));
        Assert.assertTrue(cmake.contains("add_compile_definitions(DPT_PROFILE_BUILD=${DPT_PROFILE_BUILD})"));
        Assert.assertTrue(cmake.contains("DPT_PROFILE_CMAKE_DIR requires DPT_PROFILE_BUILD=1"));

        assertInOrder(cmake,
                "include(\"${DPT_PROFILE_CMAKE_DIR}/profile-parameters.cmake\")",
                "include(\"${DPT_PROFILE_CMAKE_DIR}/profile-sources.cmake\")",
                "CMAKE_ANDROID_ARCH_ABI STREQUAL DPT_PROFILE_ABI",
                "add_library(${SO_NAME} SHARED ${DPT_NATIVE_SOURCE})");
    }

    @Test
    public void profileNativeModeDoesNotRegisterLegacyAssembleCopyHook() throws Exception {
        String shellGradle = readProjectFile("shell/build.gradle");
        String afterEvaluate = section(shellGradle, "afterEvaluate { Project project ->",
                "dependencies {");
        int profileGuard = afterEvaluate.indexOf("if (profileNativeBuild)");
        int assembleLookup = afterEvaluate.indexOf("project.tasks.getByName(\"assemble\")");
        Assert.assertTrue("Profile Native mode must exit before legacy assemble lookup",
                profileGuard >= 0 && profileGuard < assembleLookup);
        Assert.assertTrue(afterEvaluate.contains("afterAssembleCopy(project, archMap, debug)"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("dpt-shell project root not found: " + relativePath);
    }

    private static String section(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = start < 0 ? -1 : source.indexOf(endToken, start + startToken.length());
        Assert.assertTrue("Missing start token: " + startToken, start >= 0);
        Assert.assertTrue("Missing end token: " + endToken, end > start);
        return source.substring(start, end);
    }

    private static void assertInOrder(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token);
            Assert.assertTrue("Missing or out-of-order token: " + token, current > previous);
            previous = current;
        }
    }
}
