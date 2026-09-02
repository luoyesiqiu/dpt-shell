package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileCreationServiceTest {
    private static final String APPLICATION_ID = "com.example.alpha";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void explicitlyCreatesOnceAndDoesNotBuildOrPublishAnything() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        ProfileCreationService service = new ProfileCreationService(profilesRoot);

        ProfileCreationResult created = service.create(APPLICATION_ID);
        String identitySnapshot = JSON.toJSONString(created.getIdentity());
        Path sourceConfigPath = profilesRoot.resolve("source").resolve(created.getProfileId())
                .resolve("profile.yaml");
        Assert.assertTrue(Files.isRegularFile(sourceConfigPath));
        String sourceConfigSnapshot = Files.readString(sourceConfigPath);
        ProfileCreationResult repeated = service.create(APPLICATION_ID);

        Assert.assertTrue(created.isCreated());
        Assert.assertFalse(repeated.isCreated());
        Assert.assertEquals(created.getProfileId(), repeated.getProfileId());
        Assert.assertEquals(identitySnapshot, JSON.toJSONString(repeated.getIdentity()));
        Assert.assertEquals("schemaVersion: 1\napplicationId: com.example.alpha\nabis:\n"
                + "  - arm64-v8a\n", sourceConfigSnapshot);
        Assert.assertEquals(sourceConfigSnapshot, Files.readString(sourceConfigPath));
        Assert.assertEquals(created.getProfileId(), new ProfileRegistry(profilesRoot)
                .findProfileId(APPLICATION_ID).orElse(null));
        Assert.assertTrue(Files.isRegularFile(new ProfileIdentityStore(profilesRoot)
                .getIdentityLockPath(created.getProfileId())));
        Assert.assertFalse(Files.exists(profilesRoot.resolve("revisions")));
        Assert.assertFalse(Files.exists(profilesRoot.resolve("candidates")));
    }

    @Test
    public void rejectsExistingBindingWhoseIdentityLockIsDamagedWithoutGeneratingAnotherIdentity()
            throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        ProfileCreationService service = new ProfileCreationService(profilesRoot);
        ProfileCreationResult created = service.create(APPLICATION_ID);
        ProfileIdentityStore identities = new ProfileIdentityStore(profilesRoot);
        Files.writeString(identities.getIdentityLockPath(created.getProfileId()), "{}\n");

        try {
            service.create(APPLICATION_ID);
            Assert.fail("Expected strict existing identity failure");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("sha256"));
        }
        Assert.assertEquals(created.getProfileId(), new ProfileRegistry(profilesRoot)
                .findProfileId(APPLICATION_ID).orElse(null));
    }
}
