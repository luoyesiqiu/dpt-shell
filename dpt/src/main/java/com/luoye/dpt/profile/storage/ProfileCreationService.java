package com.luoye.dpt.profile.storage;

import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class ProfileCreationService {
    private final Path profilesRoot;
    private final ProfileRegistry registry;
    private final ProfileIdentityStore identityStore;
    private final ProfileIdGenerator profileIdGenerator;
    private final ProfileSourceConfigStore sourceConfigStore;

    public ProfileCreationService(Path profilesRoot) {
        this(profilesRoot, new ProfileRegistry(profilesRoot), new ProfileIdentityStore(profilesRoot),
                new ProfileIdGenerator(), new ProfileSourceConfigStore(profilesRoot));
    }

    ProfileCreationService(Path profilesRoot, ProfileRegistry registry,
                           ProfileIdentityStore identityStore, ProfileIdGenerator profileIdGenerator) {
        this(profilesRoot, registry, identityStore, profileIdGenerator,
                new ProfileSourceConfigStore(profilesRoot));
    }

    ProfileCreationService(Path profilesRoot, ProfileRegistry registry,
                           ProfileIdentityStore identityStore, ProfileIdGenerator profileIdGenerator,
                           ProfileSourceConfigStore sourceConfigStore) {
        if (profilesRoot == null || registry == null || identityStore == null || profileIdGenerator == null
                || sourceConfigStore == null) {
            throw new IllegalArgumentException("profile creation service arguments must not be null");
        }
        this.profilesRoot = profilesRoot;
        this.registry = registry;
        this.identityStore = identityStore;
        this.profileIdGenerator = profileIdGenerator;
        this.sourceConfigStore = sourceConfigStore;
    }

    public ProfileCreationResult create(String applicationId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        try (ApplicationCreationLock ignored = ApplicationCreationLock.acquire(profilesRoot, applicationId)) {
            Optional<String> existingProfileId = registry.findProfileId(applicationId);
            if (existingProfileId.isPresent()) {
                String profileId = existingProfileId.get();
                ProfileIdentityLock identity = identityStore.loadExisting(applicationId, profileId);
                return new ProfileCreationResult(profileId, identity, false);
            }

            String profileId = profileIdGenerator.generate();
            ProfileIdentityLock identity = identityStore.createIfAbsent(applicationId, profileId);
            sourceConfigStore.createIfAbsent(applicationId, profileId);
            try {
                registry.register(applicationId, profileId);
            } catch (ProfileRegistryException e) {
                Optional<String> concurrentProfileId = registry.findProfileId(applicationId);
                if (concurrentProfileId.isPresent()) {
                    String existingProfile = concurrentProfileId.get();
                    return new ProfileCreationResult(existingProfile,
                            identityStore.loadExisting(applicationId, existingProfile), false);
                }
                throw new IOException("cannot register generated profileId: " + profileId, e);
            }
            return new ProfileCreationResult(profileId, identity, true);
        }
    }

    private static final class ApplicationCreationLock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private ApplicationCreationLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        private static ApplicationCreationLock acquire(Path profilesRoot, String applicationId)
                throws IOException {
            Path lockDirectory = profilesRoot.resolve(".locks").resolve("applications");
            Files.createDirectories(lockDirectory);
            String fileName = MessageDigestUtils.sha256(applicationId.getBytes(StandardCharsets.UTF_8));
            FileChannel channel = FileChannel.open(lockDirectory.resolve(fileName + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                return new ApplicationCreationLock(channel, channel.lock());
            } catch (IOException | RuntimeException e) {
                channel.close();
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                if (lock.isValid()) {
                    lock.release();
                }
            } finally {
                channel.close();
            }
        }
    }
}
