package com.luoye.dpt.profile.storage;

import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Stores complete Bundle trees under immutable published Revision directories. */
public final class ProfilePublishedBundleStore {
    public static final String REVISIONS_DIRECTORY_NAME = "revisions";

    private static final Set<PosixFilePermission> READ_ONLY_FILE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ);
    private static final Set<PosixFilePermission> READ_ONLY_DIRECTORY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);

    private final Path activeProfilesRoot;
    private final ProfileBundleValidator validator;

    public ProfilePublishedBundleStore(Path profilesRoot, Path activeProfilesRoot) {
        if (profilesRoot == null || activeProfilesRoot == null) {
            throw new IllegalArgumentException("published Bundle roots must not be null");
        }
        this.activeProfilesRoot = activeProfilesRoot.toAbsolutePath().normalize();
        validator = new ProfileBundleValidator(profilesRoot);
    }

    public ProfileBundle publishCandidate(String applicationId, String profileId,
                                          String revisionId) throws IOException {
        ProfileBundle candidate = validator.validateCandidate(applicationId, profileId, revisionId);
        Path target = getRevisionRoot(profileId, revisionId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Profile Revision Bundle 已存在，拒绝覆盖：" + target);
        }
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempDirectory(parent, "." + revisionId + ".publish-");
        boolean moved = false;
        try {
            copyTree(candidate.getRoot(), temporary);
            validator.validate(temporary, applicationId, profileId, revisionId);
            moveAtomically(temporary, target);
            moved = true;
            makeReadOnly(target);
            return validator.validate(target, applicationId, profileId, revisionId);
        } catch (IOException | RuntimeException failure) {
            Path cleanupTarget = moved ? target : temporary;
            try {
                deleteTree(cleanupTarget);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    public ProfileBundle load(String applicationId, String profileId, String revisionId)
            throws IOException {
        return validator.validate(getRevisionRoot(profileId, revisionId), applicationId,
                profileId, revisionId);
    }

    public Path getRevisionRoot(String profileId, String revisionId) {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        return activeProfilesRoot.resolve(profileId).resolve(REVISIONS_DIRECTORY_NAME)
                .resolve(revisionId);
    }

    void deletePublishedCandidate(String profileId, String revisionId) throws IOException {
        deleteTree(getRevisionRoot(profileId, revisionId));
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList())) {
                if (path.equals(source)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("候选 Bundle 不得包含符号链接：" + path);
                }
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(destination);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    throw new IOException("候选 Bundle 包含非常规文件：" + path);
                }
            }
        }
    }

    private static void makeReadOnly(Path root) throws IOException {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        }
        for (Path path : paths) {
            boolean directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
            try {
                Files.setPosixFilePermissions(path, directory
                        ? READ_ONLY_DIRECTORY_PERMISSIONS : READ_ONLY_FILE_PERMISSIONS);
            } catch (UnsupportedOperationException exception) {
                if (!path.toFile().setWritable(false, false)) {
                    throw new IOException("无法把发布 Revision 设置为只读：" + path,
                            exception);
                }
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Profile Revision Bundle 不支持原子发布：" + target,
                    exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        }
        for (int index = paths.size() - 1; index >= 0; index--) {
            paths.get(index).toFile().setWritable(true, false);
        }
        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
