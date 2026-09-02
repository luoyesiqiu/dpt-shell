package com.luoye.dpt.profile;

import com.luoye.dpt.profile.storage.ProfileFileLock;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfileRegistryException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ProfileRegistryTest {
    private static final String APPLICATION_A = "com.example.alpha";
    private static final String APPLICATION_B = "com.example.beta";
    private static final String PROFILE_A = "pf_0123456789abcdef";
    private static final String PROFILE_B = "pf_fedcba9876543210";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void registersOneToOneBindingAndReloadsItFromIndex() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        ProfileRegistry registry = new ProfileRegistry(profilesRoot);

        Assert.assertEquals(PROFILE_A, registry.register(APPLICATION_A, PROFILE_A));
        Assert.assertEquals(PROFILE_A, registry.findProfileId(APPLICATION_A).orElse(null));
        Assert.assertEquals(APPLICATION_A, registry.findApplicationId(PROFILE_A).orElse(null));
        Assert.assertTrue(Files.exists(registry.getIndexPath()));

        ProfileRegistry reloaded = new ProfileRegistry(profilesRoot);
        Assert.assertEquals(PROFILE_A, reloaded.findProfileId(APPLICATION_A).orElse(null));
        Assert.assertEquals(APPLICATION_A, reloaded.findApplicationId(PROFILE_A).orElse(null));
    }

    @Test
    public void keepsExistingBindingAndRejectsBothKindsOfConflict() throws IOException {
        ProfileRegistry registry = new ProfileRegistry(temporaryFolder.newFolder("profiles").toPath());
        registry.register(APPLICATION_A, PROFILE_A);

        Assert.assertEquals(PROFILE_A, registry.register(APPLICATION_A, PROFILE_A));
        assertConflict(() -> registry.register(APPLICATION_A, PROFILE_B), APPLICATION_A);
        assertConflict(() -> registry.register(APPLICATION_B, PROFILE_A), PROFILE_A);

        Assert.assertEquals(PROFILE_A, registry.findProfileId(APPLICATION_A).orElse(null));
        Assert.assertFalse(registry.findProfileId(APPLICATION_B).isPresent());
        Assert.assertEquals(APPLICATION_A, registry.findApplicationId(PROFILE_A).orElse(null));
        Assert.assertFalse(registry.findApplicationId(PROFILE_B).isPresent());
    }

    @Test
    public void replacesIndexFromTemporaryFileAndExposesPerProfileLock() throws IOException {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        ProfileRegistry registry = new ProfileRegistry(profilesRoot);

        registry.register(APPLICATION_A, PROFILE_A);
        try (ProfileFileLock lock = ProfileFileLock.acquire(profilesRoot, PROFILE_A)) {
            Assert.assertTrue(lock.isValid());
            Assert.assertTrue(Files.exists(lock.getPath()));
        }

        try (Stream<Path> entries = Files.list(profilesRoot)) {
            Assert.assertFalse(entries.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".index.json.") && path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    public void listsBindingsAsReadOnlySnapshotForCrossProfileSelection() throws IOException {
        ProfileRegistry registry = new ProfileRegistry(temporaryFolder.newFolder("profiles").toPath());
        registry.register(APPLICATION_A, PROFILE_A);
        registry.register(APPLICATION_B, PROFILE_B);

        Map<String, String> bindings = registry.listBindings();

        Assert.assertEquals(PROFILE_A, bindings.get(APPLICATION_A));
        Assert.assertEquals(PROFILE_B, bindings.get(APPLICATION_B));
        try {
            bindings.put("com.example.changed", PROFILE_A);
            Assert.fail("Expected immutable binding snapshot");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void serializesSameProfileLocksAcrossThreadsInOneJvm() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("concurrent-locks").toPath();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> second;
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, PROFILE_A)) {
            second = executor.submit(() -> {
                try (ProfileFileLock lock = ProfileFileLock.acquire(profilesRoot, PROFILE_A)) {
                    return lock.isValid();
                }
            });
            Thread.sleep(100L);
            Assert.assertFalse("second lock must wait for the first lock", second.isDone());
        } finally {
            executor.shutdown();
        }
        Assert.assertTrue(second.get(5, TimeUnit.SECONDS));
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static void assertConflict(ThrowingRunnable runnable, String expectedText) throws IOException {
        try {
            runnable.run();
            Assert.fail("Expected ProfileRegistryException");
        } catch (ProfileRegistryException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(expectedText));
        }
    }

    private interface ThrowingRunnable {
        void run() throws IOException;
    }
}
