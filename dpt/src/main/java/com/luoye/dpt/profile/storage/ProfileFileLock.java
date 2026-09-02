package com.luoye.dpt.profile.storage;

import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class ProfileFileLock implements AutoCloseable {
    private static final ConcurrentHashMap<Path, ReentrantLock> PROCESS_LOCKS =
            new ConcurrentHashMap<>();

    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;
    private final ReentrantLock processLock;

    private ProfileFileLock(Path path, FileChannel channel, FileLock lock,
                            ReentrantLock processLock) {
        this.path = path;
        this.channel = channel;
        this.lock = lock;
        this.processLock = processLock;
    }

    public static ProfileFileLock acquire(Path profilesRoot, String profileId) throws IOException {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        ProfileModelValidator.validateProfileId(profileId);

        Path lockDirectory = profilesRoot.resolve(".locks");
        Files.createDirectories(lockDirectory);
        Path lockPath = lockDirectory.resolve(profileId + ".lock").toAbsolutePath().normalize();
        ReentrantLock processLock = PROCESS_LOCKS.computeIfAbsent(lockPath,
                ignored -> new ReentrantLock());
        processLock.lock();
        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            return new ProfileFileLock(lockPath, channel, channel.lock(), processLock);
        } catch (IOException | RuntimeException e) {
            if (channel != null) {
                channel.close();
            }
            processLock.unlock();
            throw e;
        }
    }

    public Path getPath() {
        return path;
    }

    public boolean isValid() {
        return lock.isValid();
    }

    @Override
    public void close() throws IOException {
        try {
            try {
                if (lock.isValid()) {
                    lock.release();
                }
            } finally {
                channel.close();
            }
        } finally {
            processLock.unlock();
        }
    }
}
