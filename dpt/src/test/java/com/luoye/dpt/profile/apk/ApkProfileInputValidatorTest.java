package com.luoye.dpt.profile.apk;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class ApkProfileInputValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsApkWithMoreThanLegacyEntryLimit() throws Exception {
        Path apk = temporaryFolder.newFile("large-entry-count.apk").toPath();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(apk.toFile()))) {
            putEntry(output, "AndroidManifest.xml", binaryManifest("com.example.large"));
            for (int index = 0; index < 5000; index++) {
                putEntry(output, String.format("assets/payload-%05d.bin", index), new byte[]{1});
            }
        }

        ApkProfileInputValidator.Metadata metadata =
                ApkProfileInputValidator.inspectArchive(apk);

        Assert.assertEquals("com.example.large", metadata.getApplicationId());
        Assert.assertTrue(metadata.getBusinessNativeAbis().isEmpty());
    }

    private static void putEntry(ZipOutputStream output, String name, byte[] content)
            throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
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
}
