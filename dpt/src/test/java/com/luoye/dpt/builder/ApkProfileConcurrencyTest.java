package com.luoye.dpt.builder;

import com.luoye.dpt.config.ShellConfig;
import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class ApkProfileConcurrencyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void sameAndDifferentProfilesRemainIsolatedAndDoNotMutateProfileState() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        Fixture first = fixture(profilesRoot, activeProfilesRoot, "com.example.concurrent.one",
                "recipe-a");
        Fixture second = fixture(profilesRoot, activeProfilesRoot, "com.example.concurrent.two",
                "recipe-b");
        Map<String, String> stateBefore = treeHashes(profilesRoot, activeProfilesRoot);

        runConcurrent(profilesRoot, activeProfilesRoot, first, first);
        runConcurrent(profilesRoot, activeProfilesRoot, first, second);

        Assert.assertEquals(stateBefore, treeHashes(profilesRoot, activeProfilesRoot));
    }

    private void runConcurrent(Path profilesRoot, Path activeProfilesRoot,
                               Fixture left, Fixture right) throws Exception {
        CountDownLatch stageReady = new CountDownLatch(2);
        RecordingApk first = apk(profilesRoot, activeProfilesRoot, left, stageReady, "left");
        RecordingApk second = apk(profilesRoot, activeProfilesRoot, right, stageReady, "right");
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        Thread firstThread = protectThread(first, firstFailure);
        Thread secondThread = protectThread(second, secondFailure);
        firstThread.start();
        secondThread.start();
        firstThread.join(10_000L);
        secondThread.join(10_000L);

        Assert.assertFalse("first protection did not finish", firstThread.isAlive());
        Assert.assertFalse("second protection did not finish", secondThread.isAlive());
        assertNoFailure(firstFailure.get());
        assertNoFailure(secondFailure.get());
        Assert.assertEquals(left.contract.getProxyApplicationClassName(),
                first.observedProxyApplication);
        Assert.assertEquals(right.contract.getProxyApplicationClassName(),
                second.observedProxyApplication);
        Assert.assertFalse(Files.exists(first.runRoot));
        Assert.assertFalse(Files.exists(second.runRoot));
        Assert.assertNotEquals(first.runRoot, second.runRoot);
    }

    private RecordingApk apk(Path profilesRoot, Path activeProfilesRoot, Fixture fixture,
                             CountDownLatch stageReady, String suffix) throws Exception {
        Path input = temporaryFolder.newFile("input-" + suffix + "-" + System.nanoTime()
                + ".apk").toPath();
        writeApk(input, fixture.applicationId);
        Apk.Builder builder = new Apk.Builder();
        builder.filePath(input.toString());
        builder.sign(false);
        builder.profileRoots(profilesRoot, activeProfilesRoot);
        return new RecordingApk(builder, stageReady);
    }

    private static Thread protectThread(RecordingApk apk, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                apk.protect();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
    }

    private static void assertNoFailure(Throwable failure) {
        if (failure != null) {
            throw new AssertionError("Concurrent APK protection failed", failure);
        }
    }

    private Fixture fixture(Path profilesRoot, Path activeProfilesRoot, String applicationId,
                            String recipeId) throws Exception {
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(applicationId);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeProfilesRoot)
                .build(applicationId, null, recipeId, null);
        ProfileBundle candidate = ProfileTestArtifactFactory.assemble(profilesRoot, applicationId,
                revision, temporaryFolder.newFolder("artifacts-" + System.nanoTime()).toPath());
        new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot).publishCandidate(
                applicationId, created.getProfileId(), revision.getRevisionId());
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        revisions.markVerified(created.getProfileId(), revision.getRevisionId());
        new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot, revisions).activate(
                created.getProfileId(), revision.getRevisionId());
        return new Fixture(applicationId, candidate.getContract());
    }

    private static Map<String, String> treeHashes(Path profilesRoot, Path activeProfilesRoot)
            throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        addTreeHashes(result, "state", profilesRoot);
        addTreeHashes(result, "active", activeProfilesRoot);
        return result;
    }

    private static void addTreeHashes(Map<String, String> result, String prefix, Path root)
            throws Exception {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).sorted().forEach(path -> {
                try {
                    result.put(prefix + "/" + root.relativize(path).toString(),
                            MessageDigestUtils.sha256(Files.readAllBytes(path)));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static void writeApk(Path target, String applicationId) throws Exception {
        AxmlWriter writer = new AxmlWriter();
        writer.ns("android", "http://schemas.android.com/apk/res/android", 1);
        NodeVisitor manifest = writer.child(null, "manifest");
        manifest.attr(null, "package", -1, NodeVisitor.TYPE_STRING, applicationId);
        NodeVisitor application = manifest.child(null, "application");
        application.end();
        manifest.end();
        writer.end();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(target.toFile()))) {
            output.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
    }

    private static final class RecordingApk extends Apk {
        private final CountDownLatch stageReady;
        private final Path runRoot;
        private volatile String observedProxyApplication;

        private RecordingApk(Builder builder, CountDownLatch stageReady) {
            super(builder);
            this.stageReady = stageReady;
            runRoot = getWorkspaceDir().toPath().toAbsolutePath().normalize().getParent();
        }

        @Override
        public void saveApplicationName(String packageOutDir) {
            stageReady.countDown();
            try {
                stageReady.await(500L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            observedProxyApplication = ShellConfig.getInstance().getProxyApplicationClassName();
        }

        @Override
        public void writeProxyAppName(String manifestDir) {
        }

        @Override
        public void saveAppComponentFactory(String packageOutDir) {
        }

        @Override
        public void writeProxyComponentFactoryName(String manifestDir) {
        }

        @Override
        public void setExtractNativeLibs(String manifestDir) {
        }

        @Override
        public void setDebuggable(String manifestDir, boolean debuggable) {
        }

        @Override
        public void extractDexCode(String packageDir, String dexCodeSavePath, byte[] soKey) {
        }

        @Override
        protected void addJunkCodeDex(String packageDir) {
        }

        @Override
        public void compressDexFiles(String packageDir) {
        }

        @Override
        public void deleteAllDexFiles(String packageDir) {
        }

        @Override
        protected void combineDexZipWithShellDex(String packageMainProcessPath) {
        }

        @Override
        protected void addKeepDexes(String packageDir) {
        }

        @Override
        public void copyNativeLibs(String packageDir) {
        }

        @Override
        public void encryptSoFiles(String packageOutDir, byte[] rc4Key) {
        }

        @Override
        public void writeConfig(String packageDir, byte[] key) {
        }

        @Override
        protected void buildPackage(String originPackagePath, String unpackFilePath,
                                    String savePath) {
        }
    }

    private static final class Fixture {
        private final String applicationId;
        private final ProfileContract contract;

        private Fixture(String applicationId, ProfileContract contract) {
            this.applicationId = applicationId;
            this.contract = contract;
        }
    }
}
