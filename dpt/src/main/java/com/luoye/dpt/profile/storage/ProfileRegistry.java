package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

public final class ProfileRegistry {
    private static final int INDEX_SCHEMA_VERSION = 1;
    private static final String INDEX_FILE_NAME = "index.json";
    private static final String INDEX_LOCK_FILE_NAME = ".index.lock";

    private final Path profilesRoot;
    private final Path indexPath;

    public ProfileRegistry(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        this.profilesRoot = profilesRoot;
        this.indexPath = profilesRoot.resolve(INDEX_FILE_NAME);
    }

    public String register(String applicationId, String profileId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        ProfileModelValidator.validateProfileId(profileId);

        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId);
             IndexFileLock indexLock = acquireIndexLock()) {
            IndexDocument index = readIndex();
            String existingProfileId = index.getBindings().get(applicationId);
            if (existingProfileId != null) {
                if (!existingProfileId.equals(profileId)) {
                    throw new ProfileRegistryException("applicationId already bound: "
                            + applicationId + " -> " + existingProfileId);
                }
                return existingProfileId;
            }

            String existingApplicationId = findApplicationId(index.getBindings(), profileId);
            if (existingApplicationId != null) {
                throw new ProfileRegistryException("profileId already bound: "
                        + profileId + " -> " + existingApplicationId);
            }

            index.getBindings().put(applicationId, profileId);
            writeIndex(index);
            return profileId;
        }
    }

    public Optional<String> findProfileId(String applicationId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        return Optional.ofNullable(readIndex().getBindings().get(applicationId));
    }

    public Optional<String> findApplicationId(String profileId) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        return Optional.ofNullable(findApplicationId(readIndex().getBindings(), profileId));
    }

    public Map<String, String> listBindings() throws IOException {
        return Collections.unmodifiableMap(new LinkedHashMap<>(readIndex().getBindings()));
    }

    public Path getIndexPath() {
        return indexPath;
    }

    private IndexFileLock acquireIndexLock() throws IOException {
        Files.createDirectories(profilesRoot);
        Path lockPath = profilesRoot.resolve(INDEX_LOCK_FILE_NAME);
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            return new IndexFileLock(channel, channel.lock());
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private IndexDocument readIndex() throws IOException {
        if (!Files.exists(indexPath)) {
            return new IndexDocument();
        }

        try {
            IndexDocument index = JSON.parseObject(Files.readString(indexPath, StandardCharsets.UTF_8),
                    IndexDocument.class);
            if (index == null) {
                throw new ProfileRegistryException("index.json is empty");
            }
            validateIndex(index);
            return index;
        } catch (ProfileRegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new ProfileRegistryException("cannot parse profile registry: " + indexPath, e);
        }
    }

    private void writeIndex(IndexDocument index) throws IOException {
        validateIndex(index);
        Files.createDirectories(profilesRoot);
        Path temporaryPath = Files.createTempFile(profilesRoot, ".index.json.", ".tmp");
        try {
            String document = JSON.toJSONString(index);
            Files.writeString(temporaryPath, document, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            moveAtomically(temporaryPath, indexPath);
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            throw new ProfileRegistryException("atomic index replacement is not supported: " + target, e);
        }
    }

    private static void validateIndex(IndexDocument index) {
        if (index.getSchemaVersion() != INDEX_SCHEMA_VERSION) {
            throw new ProfileRegistryException("unsupported index schemaVersion: "
                    + index.getSchemaVersion());
        }
        if (index.getBindings() == null) {
            throw new ProfileRegistryException("index bindings must not be null");
        }

        Set<String> profileIds = new HashSet<>();
        for (Map.Entry<String, String> entry : index.getBindings().entrySet()) {
            try {
                ProfileModelValidator.validateApplicationId(entry.getKey());
                ProfileModelValidator.validateProfileId(entry.getValue());
            } catch (RuntimeException e) {
                throw new ProfileRegistryException("invalid registry binding: " + entry, e);
            }
            if (!profileIds.add(entry.getValue())) {
                throw new ProfileRegistryException("profileId has multiple applicationId bindings: "
                        + entry.getValue());
            }
        }
    }

    private static String findApplicationId(Map<String, String> bindings, String profileId) {
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            if (profileId.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static final class IndexDocument {
        private int schemaVersion = INDEX_SCHEMA_VERSION;
        private Map<String, String> bindings = new LinkedHashMap<>();

        public int getSchemaVersion() {
            return schemaVersion;
        }

        public void setSchemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
        }

        public Map<String, String> getBindings() {
            return bindings;
        }

        public void setBindings(Map<String, String> bindings) {
            this.bindings = bindings == null ? null : new LinkedHashMap<>(new TreeMap<>(bindings));
        }
    }

    private static final class IndexFileLock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private IndexFileLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
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
