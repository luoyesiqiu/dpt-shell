package com.luoye.dpt.profile.generation;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShellReleaseSurfaceTest {

    @Test
    public void releaseShellContainsNoDemoComponentsLauncherResourcesOrInjectedAssets()
            throws Exception {
        Path projectRoot = findProjectRoot();
        Path shellMain = projectRoot.resolve("shell/src/main");

        for (String relativePath : Arrays.asList(
                "java/com/luoye/dpt/MainActivity.java",
                "java/com/luoye/dpt/ThisApplication.java",
                "java/com/luoye/dpt/MyAppComponentFactory.java")) {
            Assert.assertFalse(relativePath, Files.exists(shellMain.resolve(relativePath)));
        }

        String manifest = Files.readString(shellMain.resolve("AndroidManifest.xml"));
        Assert.assertFalse(manifest.contains("<activity"));
        Assert.assertFalse(manifest.contains("android.intent.action.MAIN"));
        Assert.assertFalse(manifest.contains("android.intent.category.LAUNCHER"));
        Assert.assertFalse(manifest.contains("@mipmap/"));
        Assert.assertFalse(manifest.contains("@string/"));
        Assert.assertFalse(manifest.contains("@style/"));

        Assert.assertTrue(regularFiles(shellMain.resolve("res")).isEmpty());
        Assert.assertTrue(regularFiles(shellMain.resolve("assets")).isEmpty());

        String shellBuild = Files.readString(projectRoot.resolve("shell/build.gradle"));
        Assert.assertFalse(shellBuild.contains("afterNativeBuildCopy"));
    }

    @Test
    public void launchableBusinessFixtureProvidesFourApplicationDeclarationVariants()
            throws Exception {
        Path projectRoot = findProjectRoot();
        Path fixtureRoot = projectRoot.resolve("test-apks/java-shell-fixture");
        Assert.assertTrue(Files.isRegularFile(fixtureRoot.resolve("build.gradle")));

        Path fixtureMain = fixtureRoot.resolve("src/main");
        String manifest = Files.readString(fixtureMain.resolve("AndroidManifest.xml"));
        Assert.assertFalse(manifest.contains("FixtureApplication"));
        Assert.assertFalse(manifest.contains("FixtureAppComponentFactory"));
        Assert.assertTrue(manifest.contains(".FixtureActivity"));
        Assert.assertTrue(manifest.contains("android.intent.action.MAIN"));
        Assert.assertTrue(manifest.contains("android.intent.category.LAUNCHER"));

        String build = Files.readString(fixtureRoot.resolve("build.gradle"));
        for (String flavor : Arrays.asList("noCustom", "applicationOnly",
                "componentFactoryOnly", "applicationAndFactory")) {
            Assert.assertTrue(flavor, build.contains(flavor));
        }
        assertApplicationDeclaration(fixtureRoot, "applicationOnly", true, false);
        assertApplicationDeclaration(fixtureRoot, "componentFactoryOnly", false, true);
        assertApplicationDeclaration(fixtureRoot, "applicationAndFactory", true, true);

        for (String className : Arrays.asList("FixtureApplication", "FixtureAppComponentFactory",
                "FixtureActivity", "FixtureService", "FixtureReceiver", "FixtureProvider",
                "FixtureProbe")) {
            Assert.assertTrue(className, Files.isRegularFile(fixtureMain.resolve(
                    "java/com/example/dptfixture/" + className + ".java")));
        }
        Assert.assertTrue(manifest.contains(".FixtureService"));
        Assert.assertTrue(manifest.contains(".FixtureReceiver"));
        Assert.assertTrue(manifest.contains(".FixtureProvider"));
        Assert.assertTrue(manifest.contains("${applicationId}.fixture-provider"));

        String settings = Files.readString(projectRoot.resolve("settings.gradle"));
        Assert.assertTrue(settings.contains("':test-apks:java-shell-fixture'"));
    }

    private static void assertApplicationDeclaration(Path fixtureRoot, String flavor,
                                                     boolean application,
                                                     boolean componentFactory) throws Exception {
        Path manifestPath = fixtureRoot.resolve("src").resolve(flavor)
                .resolve("AndroidManifest.xml");
        Assert.assertTrue(flavor, Files.isRegularFile(manifestPath));
        String manifest = Files.readString(manifestPath);
        Assert.assertEquals(flavor + " application", application,
                manifest.contains("FixtureApplication"));
        Assert.assertEquals(flavor + " component factory", componentFactory,
                manifest.contains("FixtureAppComponentFactory"));
    }

    private static List<Path> regularFiles(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return java.util.Collections.emptyList();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
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
