package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.validation.ProfileValidationException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class ProfileActiveRevisionStore {
    private static final String ACTIVE_POINTER_FILE_NAME = "active.json";

    private final Path profilesRoot;
    private final Path activeProfilesRoot;
    private final ProfileRevisionStore revisionStore;
    private final ProfileRegistry registry;
    private final ProfilePublishedBundleStore publishedBundleStore;

    public ProfileActiveRevisionStore(Path profilesRoot, Path activeProfilesRoot,
                                      ProfileRevisionStore revisionStore) {
        if (profilesRoot == null || activeProfilesRoot == null || revisionStore == null) {
            throw new IllegalArgumentException("profile active revision store arguments must not be null");
        }
        this.profilesRoot = profilesRoot;
        this.activeProfilesRoot = activeProfilesRoot;
        this.revisionStore = revisionStore;
        this.registry = new ProfileRegistry(profilesRoot);
        this.publishedBundleStore = new ProfilePublishedBundleStore(profilesRoot,
                activeProfilesRoot);
    }

    public ActiveRevisionPointer activate(String profileId, String revisionId) throws IOException {
        ProfileRevisionManifest revision = loadEligibleRevision(profileId, revisionId, false);
        validatePublishedBundle(profileId, revisionId);
        if (revision.getStatus() == RevisionStatus.VERIFIED) {
            try {
                revision = revisionStore.markActive(profileId, revisionId);
            } catch (ProfileRevisionException e) {
                throw new ProfileActiveRevisionException("cannot activate revision " + revisionId, e);
            }
        }
        return writeActivePointer(profileId, revisionId);
    }

    public ActiveRevisionPointer rollback(String profileId, String revisionId) throws IOException {
        loadEligibleRevision(profileId, revisionId, true);
        validatePublishedBundle(profileId, revisionId);
        return writeActivePointer(profileId, revisionId);
    }

    public ActiveRevisionPointer loadActive(String profileId) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ActiveRevisionPointer pointer;
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            Path pointerPath = getActivePointerPath(profileId);
            if (!Files.isRegularFile(pointerPath)) {
                throw new ProfileActiveRevisionException("active revision is missing: " + pointerPath);
            }
            pointer = readPointer(pointerPath, profileId);
        }
        verifyPointerIntegrity(pointer);
        validatePublishedBundle(pointer.getProfileId(), pointer.getRevisionId());
        return pointer;
    }

    public Path getActivePointerPath(String profileId) {
        ProfileModelValidator.validateProfileId(profileId);
        return activeProfilesRoot.resolve(profileId).resolve(ACTIVE_POINTER_FILE_NAME);
    }

    private ProfileRevisionManifest loadEligibleRevision(String profileId, String revisionId,
                                                         boolean rollbackOnly) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        try {
            ProfileRevisionManifest revision = revisionStore.load(profileId, revisionId);
            if (rollbackOnly && revision.getStatus() != RevisionStatus.ACTIVE) {
                throw new ProfileActiveRevisionException("revision " + revisionId + " is "
                        + revision.getStatus() + ", expected ACTIVE for rollback");
            }
            if (!rollbackOnly && revision.getStatus() != RevisionStatus.VERIFIED
                    && revision.getStatus() != RevisionStatus.ACTIVE) {
                throw new ProfileActiveRevisionException("revision " + revisionId + " is "
                        + revision.getStatus() + ", expected VERIFIED or ACTIVE");
            }
            revisionStore.getManifestSha256(profileId, revisionId);
            return revision;
        } catch (ProfileActiveRevisionException e) {
            throw e;
        } catch (ProfileRevisionException e) {
            throw new ProfileActiveRevisionException("revision integrity failed: " + e.getMessage(), e);
        }
    }

    private ActiveRevisionPointer writeActivePointer(String profileId, String revisionId) throws IOException {
        String manifestSha256;
        try {
            manifestSha256 = revisionStore.getManifestSha256(profileId, revisionId);
        } catch (ProfileRevisionException e) {
            throw new ProfileActiveRevisionException("revision integrity failed: " + e.getMessage(), e);
        }

        ActiveRevisionPointer pointer = new ActiveRevisionPointer();
        pointer.setProfileId(profileId);
        pointer.setRevisionId(revisionId);
        pointer.setProfileManifestSha256(manifestSha256);
        try {
            ProfileModelValidator.validate(pointer);
        } catch (ProfileValidationException e) {
            throw new ProfileActiveRevisionException("active pointer validation failed: " + e.getMessage(), e);
        }

        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            writeAtomically(getActivePointerPath(profileId), JSON.toJSONString(pointer)
                    .getBytes(StandardCharsets.UTF_8));
        }
        return pointer;
    }

    private ActiveRevisionPointer readPointer(Path pointerPath, String profileId) throws IOException {
        try {
            ActiveRevisionPointer pointer = JSON.parseObject(
                    Files.readString(pointerPath, StandardCharsets.UTF_8), ActiveRevisionPointer.class);
            if (pointer == null) {
                throw new ProfileActiveRevisionException("active pointer is empty: " + pointerPath);
            }
            ProfileModelValidator.validate(pointer);
            if (!profileId.equals(pointer.getProfileId())) {
                throw new ProfileActiveRevisionException("active pointer profileId mismatch: " + pointerPath);
            }
            return pointer;
        } catch (ProfileActiveRevisionException e) {
            throw e;
        } catch (ProfileValidationException e) {
            throw new ProfileActiveRevisionException("active pointer validation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ProfileActiveRevisionException("cannot parse active pointer: " + pointerPath, e);
        }
    }

    private void verifyPointerIntegrity(ActiveRevisionPointer pointer) throws IOException {
        try {
            ProfileRevisionManifest revision = revisionStore.load(pointer.getProfileId(),
                    pointer.getRevisionId());
            if (revision.getStatus() != RevisionStatus.ACTIVE) {
                throw new ProfileActiveRevisionException("active pointer references revision status: "
                        + revision.getStatus());
            }
            String actualHash = revisionStore.getManifestSha256(pointer.getProfileId(),
                    pointer.getRevisionId());
            if (!pointer.getProfileManifestSha256().equals(actualHash)) {
                throw new ProfileActiveRevisionException("active pointer manifest sha256 mismatch: "
                        + pointer.getRevisionId());
            }
        } catch (ProfileActiveRevisionException e) {
            throw e;
        } catch (ProfileRevisionException e) {
            throw new ProfileActiveRevisionException("active revision integrity failed: " + e.getMessage(), e);
        }
    }

    private void validatePublishedBundle(String profileId, String revisionId)
            throws ProfileActiveRevisionException {
        try {
            String applicationId = registry.findApplicationId(profileId)
                    .orElseThrow(() -> new IOException(
                            "Profile 注册表缺少绑定：profileId=" + profileId));
            publishedBundleStore.load(applicationId, profileId, revisionId);
        } catch (IOException | RuntimeException exception) {
            throw new ProfileActiveRevisionException("活动 Revision Bundle 完整性校验失败："
                    + exception.getMessage(), exception);
        }
    }

    private static void writeAtomically(Path targetPath, byte[] content) throws IOException {
        Files.createDirectories(targetPath.getParent());
        Path temporaryPath = Files.createTempFile(targetPath.getParent(),
                "." + targetPath.getFileName() + ".", ".tmp");
        try {
            Files.write(temporaryPath, content, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporaryPath, targetPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new ProfileActiveRevisionException("atomic active pointer replacement is not supported: "
                        + targetPath, e);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }
}
