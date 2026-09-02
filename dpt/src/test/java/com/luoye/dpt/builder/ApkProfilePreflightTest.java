package com.luoye.dpt.builder;

import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class ApkProfilePreflightTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsUnregisteredApkBeforeAnyManifestWrite() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        byte[] manifest = binaryManifest("com.example.preflight");
        Path apkPath = temporaryFolder.newFile("input.apk").toPath();
        writeApk(apkPath, manifest);

        Apk.Builder builder = new Apk.Builder();
        builder.filePath(apkPath.toString());
        builder.profileRoots(profilesRoot, activeProfilesRoot);
        RecordingApk apk = new RecordingApk(builder);
        Path runRoot = apk.runRoot();

        try {
            apk.protect();
            Assert.fail("Expected unregistered Profile failure");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(ProfileErrorCode.PROFILE_NOT_FOUND, expected.getErrorCode());
        }

        Assert.assertFalse(apk.manifestWriteAttempted);
        Assert.assertFalse("Failed APK run workspace must be cleaned: " + runRoot,
                Files.exists(runRoot));
    }

    private static byte[] binaryManifest(String applicationId) throws Exception {
        AxmlWriter writer = new AxmlWriter();
        writer.ns("android", "http://schemas.android.com/apk/res/android", 1);
        NodeVisitor manifest = writer.child(null, "manifest");
        manifest.attr(null, "package", -1, NodeVisitor.TYPE_STRING, applicationId);
        NodeVisitor application = manifest.child(null, "application");
        application.end();
        manifest.end();
        writer.end();
        return writer.toByteArray();
    }

    private static void writeApk(Path apkPath, byte[] manifest) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(apkPath.toFile()))) {
            output.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            output.write(manifest);
            output.closeEntry();
        }
    }

    private static final class RecordingApk extends Apk {
        private boolean manifestWriteAttempted;

        private RecordingApk(Builder builder) {
            super(builder);
        }

        private Path runRoot() {
            return getWorkspaceDir().toPath().toAbsolutePath().normalize().getParent();
        }

        @Override
        public void writeProxyAppName(String manifestDir) {
            manifestWriteAttempted = true;
        }

        @Override
        public void writeProxyComponentFactoryName(String manifestDir) {
            manifestWriteAttempted = true;
        }

        @Override
        public void setExtractNativeLibs(String manifestDir) {
            manifestWriteAttempted = true;
        }

        @Override
        public void setDebuggable(String manifestDir, boolean debuggable) {
            manifestWriteAttempted = true;
        }
    }
}
