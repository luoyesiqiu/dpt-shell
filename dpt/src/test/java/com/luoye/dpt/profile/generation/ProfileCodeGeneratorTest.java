package com.luoye.dpt.profile.generation;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileCodeGeneratorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsIsolatedJavaCppR8AndCmakeDirectoriesPerRevision() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        ProfileCodeGenerator generator = new ProfileCodeGenerator(profilesRoot);

        ProfileCodeGenerator.GenerationLayout first = generator.prepare(
                "pf_0123456789abcdef", "r000001");
        ProfileCodeGenerator.GenerationLayout second = generator.prepare(
                "pf_0123456789abcdef", "r000002");
        ProfileCodeGenerator.GenerationLayout otherProfile = generator.prepare(
                "pf_fedcba9876543210", "r000001");

        Assert.assertEquals(profilesRoot.resolve("generated/pf_0123456789abcdef/r000001"),
                first.getRevisionRoot());
        Assert.assertTrue(Files.isDirectory(first.getJavaDirectory()));
        Assert.assertTrue(Files.isDirectory(first.getCppDirectory()));
        Assert.assertTrue(Files.isDirectory(first.getR8Directory()));
        Assert.assertTrue(Files.isDirectory(first.getCmakeDirectory()));
        Assert.assertNotEquals(first.getRevisionRoot(), second.getRevisionRoot());
        Assert.assertNotEquals(first.getRevisionRoot(), otherProfile.getRevisionRoot());
        Assert.assertFalse(ProfileCodeGenerator.GENERATOR_VERSION.contains("pending"));
        Assert.assertFalse(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION.contains("pending"));
        Assert.assertTrue(first.getGeneratedSourceSha256().matches("[0-9a-f]{64}"));
    }
}
