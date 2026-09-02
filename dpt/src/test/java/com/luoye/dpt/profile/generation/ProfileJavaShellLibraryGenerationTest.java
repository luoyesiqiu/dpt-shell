package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class ProfileJavaShellLibraryGenerationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void delegatedRecipeGeneratesLocatorExtractorPermissionAndLoaderGraph()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.library.delegate");
        ProfileRecipeTemplate template = template(ProfileRecipeRegistry.RECIPE_A);
        ProfileCodeGenerator.GenerationLayout layout = generate(identity, template,
                "delegate");
        LibraryNames names = new LibraryNames(identity, template);

        String root = read(layout, names.root);
        String locator = read(layout, names.support("locator"));
        String extractor = read(layout, names.support("extractor"));
        String permission = read(layout, names.support("permission"));
        String loader = read(layout, names.support("loader"));
        String all = root + locator + extractor + permission + loader;

        assertCommonLibrarySemantics(all, identity);
        assertInOrder(root, names.support("locator"), names.support("extractor"),
                names.support("permission"), names.support("loader"));
        Assert.assertTrue(extractor.contains("Enumeration<? extends ZipEntry>"));
        Assert.assertTrue(extractor.contains("entry.getCrc()"));
        compileGeneratedSources(layout);
    }

    @Test
    public void stagedRecipeGeneratesPlanTransferAndLoadPipelineWithSameSemantics()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.library.staged");
        ProfileRecipeTemplate template = template(ProfileRecipeRegistry.RECIPE_B);
        ProfileCodeGenerator.GenerationLayout layout = generate(identity, template,
                "staged");
        LibraryNames names = new LibraryNames(identity, template);

        String root = read(layout, names.root);
        String plan = read(layout, names.support("plan"));
        String transfer = read(layout, names.support("transfer-stage"));
        String loader = read(layout, names.support("load-stage"));
        String all = root + plan + transfer + loader;

        assertCommonLibrarySemantics(all, identity);
        assertInOrder(root, names.support("plan"), names.support("transfer-stage"),
                names.support("load-stage"));
        Assert.assertTrue(transfer.contains("zip.getEntry(plan."));
        Assert.assertTrue(transfer.contains("entry.getCrc()"));
        compileGeneratedSources(layout);
    }

    @Test
    public void recipesUseDifferentLibraryClassGraphsWithoutChangingProfilePaths()
            throws Exception {
        ProfileIdentityLock identity = createIdentity("com.example.library.compare");
        ProfileRecipeTemplate delegated = template(ProfileRecipeRegistry.RECIPE_A);
        ProfileRecipeTemplate staged = template(ProfileRecipeRegistry.RECIPE_B);
        ProfileCodeGenerator.GenerationLayout delegatedLayout = generate(identity, delegated,
                "compare-delegated");
        ProfileCodeGenerator.GenerationLayout stagedLayout = generate(identity, staged,
                "compare-staged");
        LibraryNames delegatedNames = new LibraryNames(identity, delegated);
        LibraryNames stagedNames = new LibraryNames(identity, staged);

        String delegatedRoot = read(delegatedLayout, delegatedNames.root);
        String stagedRoot = read(stagedLayout, stagedNames.root);
        Assert.assertNotEquals(delegatedNames.root, stagedNames.root);
        Assert.assertNotEquals(delegatedRoot, stagedRoot);
        Assert.assertTrue(delegatedRoot.contains(identity.getSoName()));
        Assert.assertTrue(stagedRoot.contains(identity.getSoName()));
        Assert.assertTrue(delegatedRoot.contains(identity.getRuntimeDirName()));
        Assert.assertTrue(stagedRoot.contains(identity.getRuntimeDirName()));
    }

    @Test
    public void completeRecipesHonorFirstExtractionReuseCrcChangeAndFailureTolerance()
            throws Exception {
        for (String recipeId : Arrays.asList(ProfileRecipeRegistry.RECIPE_A,
                ProfileRecipeRegistry.RECIPE_B)) {
            ProfileIdentityLock identity = createIdentity("com.example.runtime."
                    + recipeId.replace('-', '.'));
            ProfileRecipeTemplate recipe = template(recipeId);
            ProfileCodeGenerator.GenerationLayout layout = generate(identity, recipe,
                    "runtime-" + recipeId);
            LibraryNames names = new LibraryNames(identity, recipe);

            try (GeneratedLibraryRuntime runtime = new GeneratedLibraryRuntime(
                    compileGeneratedSources(layout), identity, names)) {
                Path sourceApk = temporaryFolder.newFile(recipeId + ".apk").toPath();
                Path dataDir = temporaryFolder.newFolder(recipeId + "-data").toPath();
                byte[] firstPayload = {1, 3, 5, 7};
                writeShellLibrary(sourceApk, identity, firstPayload);

                runtime.prepare(sourceApk, dataDir);
                Path target = runtime.target(dataDir);
                Assert.assertArrayEquals(recipeId, firstPayload, Files.readAllBytes(target));

                FileTime preservedTime = FileTime.fromMillis(1_600_000_000_000L);
                Files.setLastModifiedTime(target, preservedTime);
                runtime.prepare(sourceApk, dataDir);
                Assert.assertEquals(recipeId, preservedTime,
                        Files.getLastModifiedTime(target));

                byte[] changedPayload = {2, 4, 6, 8, 10};
                writeShellLibrary(sourceApk, identity, changedPayload);
                runtime.prepare(sourceApk, dataDir);
                Assert.assertArrayEquals(recipeId, changedPayload,
                        Files.readAllBytes(target));

                Path failedDataDir = temporaryFolder.newFolder(
                        recipeId + "-failed-data").toPath();
                runtime.prepare(sourceApk.resolveSibling("missing.apk"), failedDataDir);
                Assert.assertFalse(recipeId, Files.exists(runtime.target(failedDataDir)));
            }
        }
    }

    private static void assertCommonLibrarySemantics(String source,
                                                     ProfileIdentityLock identity) {
        Assert.assertTrue(source.contains("assets/" + identity.getNativeLibAssetDirName()
                + "/"));
        Assert.assertTrue(source.contains(identity.getRuntimeDirName()));
        Assert.assertTrue(source.contains(identity.getSoName()));
        Assert.assertTrue(source.contains("return \"arm64\";"));
        Assert.assertTrue(source.contains("CRC32"));
        Assert.assertTrue(source.contains("CheckedInputStream"));
        Assert.assertTrue(source.contains("setWritable(true)"));
        Assert.assertTrue(source.contains("Os.chmod"));
        Assert.assertTrue(source.contains("0444"));
        Assert.assertTrue(source.contains("setReadable(true, false)"));
        Assert.assertTrue(source.contains("setWritable(false, false)"));
        Assert.assertTrue(source.contains("System.load"));
        Assert.assertTrue(source.contains("endsWith(File.separator + \""
                + identity.getSoName() + "\")"));
        Assert.assertTrue(source.contains("catch (Throwable"));
        Assert.assertFalse(source.contains("com.luoyesiqiu"));
        Assert.assertFalse(source.contains("dpt-libs"));
        Assert.assertFalse(source.contains("vwwwwwvwww"));
    }

    private ProfileIdentityLock createIdentity(String applicationId) throws Exception {
        Path profilesRoot = temporaryFolder.newFolder().toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(applicationId);
        return new ProfileIdentityStore(profilesRoot).loadExisting(applicationId,
                created.getProfileId());
    }

    private ProfileCodeGenerator.GenerationLayout generate(ProfileIdentityLock identity,
                                                            ProfileRecipeTemplate template,
                                                            String directoryName)
            throws Exception {
        return new ProfileCodeGenerator(temporaryFolder.newFolder(directoryName).toPath())
                .generateJava(identity, "r000001", template);
    }

    private static ProfileRecipeTemplate template(String recipeId) {
        ProfileRecipe recipe = ProfileRecipeRegistry.builtIn().requireVerified(recipeId,
                ProfileSchemaVersions.PROFILE_PROTOCOL);
        return ProfileRecipeTemplateRegistry.builtIn().requireFor(recipe);
    }

    private static String read(ProfileCodeGenerator.GenerationLayout layout,
                               String simpleClassName) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(layout.getJavaDirectory())) {
            Path source = paths.filter(path -> path.getFileName().toString()
                            .equals(simpleClassName + ".java"))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "缺少生成源码：" + simpleClassName));
            return Files.readString(source);
        }
    }

    private Path compileGeneratedSources(ProfileCodeGenerator.GenerationLayout layout)
            throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull("测试运行环境缺少 JDK JavaCompiler", compiler);
        Path stubs = temporaryFolder.newFolder().toPath();
        writeStub(stubs, "android/annotation/TargetApi.java",
                "package android.annotation; public @interface TargetApi { int value(); }");
        writeStub(stubs, "android/content/Context.java",
                "package android.content; import android.content.pm.ApplicationInfo; "
                        + "import android.content.pm.PackageManager; public class Context { "
                        + "public ApplicationInfo getApplicationInfo() { return null; } "
                        + "public ClassLoader getClassLoader() { return null; } "
                        + "public Context createPackageContext(String name, int flags) "
                        + "throws PackageManager.NameNotFoundException { return this; } "
                        + "public String getPackageName() { return \"\"; } }");
        writeStub(stubs, "android/app/Application.java",
                "package android.app; import android.content.Context; "
                        + "public class Application extends Context { "
                        + "protected void attachBaseContext(Context base) {} "
                        + "public void onCreate() {} }");
        writeStub(stubs, "android/content/pm/ApplicationInfo.java",
                "package android.content.pm; public class ApplicationInfo { "
                        + "public String sourceDir; public String dataDir; public String packageName; }");
        writeStub(stubs, "android/content/pm/PackageManager.java",
                "package android.content.pm; public class PackageManager { "
                        + "public static class NameNotFoundException extends Exception {} }");
        writeStub(stubs, "android/app/AppComponentFactory.java",
                "package android.app; import android.content.BroadcastReceiver; "
                        + "import android.content.ContentProvider; import android.content.Intent; "
                        + "import android.content.pm.ApplicationInfo; public class AppComponentFactory { "
                        + "public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo info) { return cl; } "
                        + "public Application instantiateApplication(ClassLoader cl, String name) "
                        + "throws ClassNotFoundException, IllegalAccessException, InstantiationException { "
                        + "return (Application) Class.forName(name, true, cl).newInstance(); } "
                        + "public Activity instantiateActivity(ClassLoader cl, String name, Intent intent) "
                        + "throws ClassNotFoundException, IllegalAccessException, InstantiationException { "
                        + "return (Activity) Class.forName(name, true, cl).newInstance(); } "
                        + "public Service instantiateService(ClassLoader cl, String name, Intent intent) "
                        + "throws ClassNotFoundException, IllegalAccessException, InstantiationException { "
                        + "return (Service) Class.forName(name, true, cl).newInstance(); } "
                        + "public BroadcastReceiver instantiateReceiver(ClassLoader cl, String name, Intent intent) "
                        + "throws ClassNotFoundException, IllegalAccessException, InstantiationException { "
                        + "return (BroadcastReceiver) Class.forName(name, true, cl).newInstance(); } "
                        + "public ContentProvider instantiateProvider(ClassLoader cl, String name) "
                        + "throws ClassNotFoundException, IllegalAccessException, InstantiationException { "
                        + "return (ContentProvider) Class.forName(name, true, cl).newInstance(); } }");
        writeStub(stubs, "android/app/Activity.java",
                "package android.app; public class Activity {}");
        writeStub(stubs, "android/app/Service.java",
                "package android.app; public class Service {}");
        writeStub(stubs, "android/content/BroadcastReceiver.java",
                "package android.content; public class BroadcastReceiver {}");
        writeStub(stubs, "android/content/ContentProvider.java",
                "package android.content; public class ContentProvider {}");
        writeStub(stubs, "android/content/Intent.java",
                "package android.content; public class Intent {}");
        writeStub(stubs, "android/system/ErrnoException.java",
                "package android.system; public class ErrnoException extends Exception {}");
        writeStub(stubs, "android/system/Os.java",
                "package android.system; public final class Os { "
                        + "public static void chmod(String path, int mode) "
                        + "throws ErrnoException {} }");

        List<Path> sources = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(stubs)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(layout.getJavaDirectory())) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
        }
        Path output = temporaryFolder.newFolder().toPath();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
            boolean compiled = compiler.getTask(null, files, diagnostics,
                    java.util.Arrays.asList("-d", output.toString()), null, units).call();
            if (!compiled) {
                StringBuilder message = new StringBuilder("生成 Java 源码编译失败：\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic
                        : diagnostics.getDiagnostics()) {
                    message.append(diagnostic).append('\n');
                }
                Assert.fail(message.toString());
            }
        }
        return output;
    }

    private static void writeShellLibrary(Path apkPath, ProfileIdentityLock identity,
                                          byte[] content) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(apkPath))) {
            output.putNextEntry(new ZipEntry("assets/" + identity.getNativeLibAssetDirName()
                    + "/arm64/" + identity.getSoName()));
            output.write(content);
            output.closeEntry();
        }
    }

    private static void writeStub(Path root, String relativePath, String source)
            throws Exception {
        Path path = root.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
    }

    private static void assertInOrder(String source, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = source.indexOf(value, previous + 1);
            Assert.assertTrue("缺少或顺序错误：" + value + "\n" + source,
                    current > previous);
            previous = current;
        }
    }

    private static final class LibraryNames {
        private final ProfileIdentityLock identity;
        private final ProfileRecipeTemplate template;
        private final String root;

        private LibraryNames(ProfileIdentityLock identity, ProfileRecipeTemplate template) {
            this.identity = identity;
            this.template = template;
            this.root = derive("java-library-root-" + template.getRecipeId(), "L");
        }

        private String support(String unit) {
            return derive("java-library-class-" + template.getRecipeId() + "-" + unit, "L");
        }

        private String prepareMethod() {
            return derive("java-library-prepare", "m");
        }

        private String derive(String label, String prefix) {
            return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(), label,
                    prefix, 12);
        }
    }

    private static final class GeneratedLibraryRuntime implements AutoCloseable {
        private final URLClassLoader classLoader;
        private final ProfileIdentityLock identity;
        private final Method prepare;

        private GeneratedLibraryRuntime(Path classes, ProfileIdentityLock identity,
                                        LibraryNames names) throws Exception {
            this.identity = identity;
            classLoader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
            Class<?> root = Class.forName(identity.getShellPackageName() + "." + names.root,
                    true, classLoader);
            prepare = root.getDeclaredMethod(names.prepareMethod(), String.class, String.class);
            prepare.setAccessible(true);
        }

        private void prepare(Path sourceApk, Path dataDir) throws Exception {
            prepare.invoke(null, sourceApk.toString(), dataDir.toString());
        }

        private Path target(Path dataDir) {
            return dataDir.resolve(identity.getRuntimeDirName()).resolve("arm64")
                    .resolve(identity.getSoName());
        }

        @Override
        public void close() throws Exception {
            classLoader.close();
        }
    }
}
