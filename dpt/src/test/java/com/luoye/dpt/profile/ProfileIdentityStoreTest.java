package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfileIdGenerator;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileIdentityStoreTest {
    private static final String APPLICATION_ID = "com.example.alpha";
    private static final String PROFILE_ID = "pf_0123456789abcdef";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatesOpaqueProfileIdsWithoutDerivingThemFromApplicationId() {
        ProfileIdGenerator generator = new ProfileIdGenerator();

        String first = generator.generate();
        String second = generator.generate();

        ProfileModelValidator.validateProfileId(first);
        ProfileModelValidator.validateProfileId(second);
        Assert.assertNotEquals(first, second);
        Assert.assertFalse(first.contains("example"));
        Assert.assertFalse(second.contains("example"));
    }

    @Test
    public void createsCompleteStableIdentityLockOutsideBuildDirectory() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        ProfileIdentityStore store = new ProfileIdentityStore(profilesRoot);

        ProfileIdentityLock identity = store.createIfAbsent(APPLICATION_ID, PROFILE_ID);

        ProfileModelValidator.validate(identity);
        Assert.assertEquals(PROFILE_ID, identity.getProfileId());
        Assert.assertEquals(APPLICATION_ID, identity.getApplicationId());
        Assert.assertTrue(Files.isRegularFile(store.getIdentityLockPath(PROFILE_ID)));
        Assert.assertTrue(store.getIdentityLockPath(PROFILE_ID).toString()
                .contains("profiles/source/" + PROFILE_ID));
        Assert.assertFalse(store.getIdentityLockPath(PROFILE_ID).toString().contains("/build/"));
    }

    @Test
    public void reusesExistingIdentityWithoutRotatingAnyStableIdentifier() throws IOException {
        ProfileIdentityStore store = new ProfileIdentityStore(temporaryFolder.newFolder("profiles").toPath());

        ProfileIdentityLock created = store.createIfAbsent(APPLICATION_ID, PROFILE_ID);
        String before = JSON.toJSONString(created);
        ProfileIdentityLock reused = store.createIfAbsent(APPLICATION_ID, PROFILE_ID);

        Assert.assertEquals(before, JSON.toJSONString(reused));
        Assert.assertEquals(created.getIdentitySeed(), reused.getIdentitySeed());
        Assert.assertEquals(created.getBuildKey(), reused.getBuildKey());
        Assert.assertEquals(created.getSoName(), reused.getSoName());
        Assert.assertEquals(created.getJniMethods().get(0).getMethodName(),
                reused.getJniMethods().get(0).getMethodName());
    }

    @Test
    public void rejectsReusingAnExistingProfileForAnotherApplication() throws IOException {
        ProfileIdentityStore store = new ProfileIdentityStore(temporaryFolder.newFolder("profiles").toPath());
        store.createIfAbsent(APPLICATION_ID, PROFILE_ID);

        try {
            store.createIfAbsent("com.example.beta", PROFILE_ID);
            Assert.fail("Expected identity applicationId conflict");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(PROFILE_ID));
        }
    }

    @Test
    public void strictlyRejectsMissingOrMalformedExistingIdentityLock() throws IOException {
        ProfileIdentityStore missingStore = new ProfileIdentityStore(
                temporaryFolder.newFolder("missing-profiles").toPath());
        assertLoadFails(missingStore, "identity lock is missing");

        ProfileIdentityStore malformedStore = new ProfileIdentityStore(
                temporaryFolder.newFolder("malformed-profiles").toPath());
        malformedStore.createIfAbsent(APPLICATION_ID, PROFILE_ID);
        writeIdentityWithMatchingHash(malformedStore, "{not-json");
        assertLoadFails(malformedStore, "cannot parse identity lock");
    }

    @Test
    public void strictlyRejectsUnsupportedVersionDamagedFieldAndHashMismatch() throws IOException {
        ProfileIdentityStore unsupportedStore = new ProfileIdentityStore(
                temporaryFolder.newFolder("unsupported-profiles").toPath());
        ProfileIdentityLock unsupported = unsupportedStore.createIfAbsent(APPLICATION_ID, PROFILE_ID);
        unsupported.setSchemaVersion(99);
        writeIdentityWithMatchingHash(unsupportedStore, JSON.toJSONString(unsupported));
        assertLoadFails(unsupportedStore, "schemaVersion");

        ProfileIdentityStore damagedStore = new ProfileIdentityStore(
                temporaryFolder.newFolder("damaged-profiles").toPath());
        ProfileIdentityLock damaged = damagedStore.createIfAbsent(APPLICATION_ID, PROFILE_ID);
        damaged.setShellPackageName("invalid");
        writeIdentityWithMatchingHash(damagedStore, JSON.toJSONString(damaged));
        assertLoadFails(damagedStore, "shellPackageName");

        ProfileIdentityStore mismatchedStore = new ProfileIdentityStore(
                temporaryFolder.newFolder("mismatched-profiles").toPath());
        mismatchedStore.createIfAbsent(APPLICATION_ID, PROFILE_ID);
        Files.writeString(mismatchedStore.getIdentityLockPath(PROFILE_ID), "{}\n");
        assertLoadFails(mismatchedStore, "sha256");
    }

    @Test
    public void separatesGeneratedIdentifiersBetweenProfiles() throws IOException {
        ProfileIdentityStore store = new ProfileIdentityStore(temporaryFolder.newFolder("profiles").toPath());

        ProfileIdentityLock first = store.createIfAbsent(APPLICATION_ID, PROFILE_ID);
        ProfileIdentityLock second = store.createIfAbsent("com.example.beta", "pf_fedcba9876543210");

        Assert.assertNotEquals(first.getShellPackageName(), second.getShellPackageName());
        Assert.assertNotEquals(first.getSoName(), second.getSoName());
        Assert.assertNotEquals(first.getBuildKey(), second.getBuildKey());
        Assert.assertNotEquals(first.getJniMethods().get(0).getMethodName(),
                second.getJniMethods().get(0).getMethodName());
    }

    private static void writeIdentityWithMatchingHash(ProfileIdentityStore store, String content)
            throws IOException {
        Files.writeString(store.getIdentityLockPath(PROFILE_ID), content);
        Files.writeString(store.getIdentityHashPath(PROFILE_ID),
                MessageDigestUtils.sha256(content.getBytes(StandardCharsets.UTF_8)) + "\n");
    }

    private static void assertLoadFails(ProfileIdentityStore store, String expectedMessage)
            throws IOException {
        try {
            store.loadExisting(APPLICATION_ID, PROFILE_ID);
            Assert.fail("Expected strict identity load failure");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }
}
