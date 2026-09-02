package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.validation.ProfileValidationException;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ProfileRevisionStore {
    private static final String REVISIONS_DIRECTORY_NAME = "revisions";
    private static final String REVISION_MANIFEST_FILE_NAME = "revision.json";
    private static final String REVISION_HASH_FILE_NAME = "revision.sha256";
    private static final int MAX_REVISION_NUMBER = 999999;

    private final Path profilesRoot;

    public ProfileRevisionStore(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        this.profilesRoot = profilesRoot;
    }

    public ProfileRevisionManifest createCandidate(ProfileRevisionManifest input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("revision input must not be null");
        }
        ProfileModelValidator.validateProfileId(input.getProfileId());
        ProfileModelValidator.validateApplicationId(input.getApplicationId());

        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, input.getProfileId())) {
            String revisionId = nextRevisionId(input.getProfileId());
            ProfileRevisionManifest candidate = copyOf(input);
            candidate.setRevisionId(revisionId);
            candidate.setStatus(RevisionStatus.CANDIDATE);
            candidate.setCreatedAtEpochMillis(System.currentTimeMillis());
            validateRevision(candidate, input.getProfileId(), revisionId);
            writeRevision(getRevisionManifestPath(input.getProfileId(), revisionId), candidate);
            return copyOf(candidate);
        }
    }

    public ProfileRevisionManifest load(String profileId, String revisionId) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            return loadLocked(profileId, revisionId);
        }
    }

    public List<ProfileRevisionManifest> list(String profileId) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            Path revisionsRoot = getRevisionsRoot(profileId);
            if (!Files.exists(revisionsRoot)) {
                return Collections.emptyList();
            }
            if (!Files.isDirectory(revisionsRoot)) {
                throw new ProfileRevisionException("revisions path is not a directory: " + revisionsRoot);
            }

            List<Path> revisionDirectories;
            try (Stream<Path> paths = Files.list(revisionsRoot)) {
                revisionDirectories = paths.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .collect(java.util.stream.Collectors.toList());
            }

            List<ProfileRevisionManifest> revisions = new ArrayList<>();
            for (Path revisionDirectory : revisionDirectories) {
                String revisionId = revisionDirectory.getFileName().toString();
                try {
                    ProfileModelValidator.validateRevisionId(revisionId);
                } catch (ProfileValidationException e) {
                    throw new ProfileRevisionException("invalid revision directory: "
                            + revisionDirectory, e);
                }
                revisions.add(loadLocked(profileId, revisionId));
            }
            return revisions;
        }
    }

    public ProfileRevisionManifest markVerified(String profileId, String revisionId) throws IOException {
        return markVerified(profileId, revisionId, null);
    }

    public ProfileRevisionManifest updateGeneratedSourceSha256(String profileId, String revisionId,
                                                                String generatedSourceSha256)
            throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            ProfileRevisionManifest revision = loadLocked(profileId, revisionId);
            if (revision.getStatus() != RevisionStatus.CANDIDATE) {
                throw new ProfileRevisionException("revision " + revisionId + " is "
                        + revision.getStatus() + ", expected CANDIDATE");
            }
            revision.setGeneratedSourceSha256(generatedSourceSha256);
            validateRevision(revision, profileId, revisionId);
            writeRevision(getRevisionManifestPath(profileId, revisionId), revision);
            return copyOf(revision);
        }
    }

    public ProfileRevisionManifest bindStructuralDifferenceReport(String profileId,
                                                                   String revisionId,
                                                                   String reportSha256)
            throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            ProfileRevisionManifest revision = loadLocked(profileId, revisionId);
            if (revision.getStatus() != RevisionStatus.CANDIDATE) {
                throw new ProfileRevisionException("revision " + revisionId + " is "
                        + revision.getStatus() + ", expected CANDIDATE");
            }
            if (revision.getStructuralDifferenceReportSha256() != null) {
                throw new ProfileRevisionException("structural difference report is already bound: "
                        + revisionId);
            }
            revision.setStructuralDifferenceReportSha256(reportSha256);
            validateRevision(revision, profileId, revisionId);
            writeRevision(getRevisionManifestPath(profileId, revisionId), revision);
            return copyOf(revision);
        }
    }

    public ProfileRevisionManifest markVerified(String profileId, String revisionId,
                                                 String gateReportSha256) throws IOException {
        return transition(profileId, revisionId, RevisionStatus.CANDIDATE, RevisionStatus.VERIFIED,
                gateReportSha256);
    }

    public ProfileRevisionManifest markRejected(String profileId, String revisionId) throws IOException {
        return markRejected(profileId, revisionId, null);
    }

    public ProfileRevisionManifest markRejected(String profileId, String revisionId,
                                                 String gateReportSha256) throws IOException {
        return transition(profileId, revisionId, RevisionStatus.CANDIDATE, RevisionStatus.REJECTED,
                gateReportSha256);
    }

    public ProfileRevisionManifest markActive(String profileId, String revisionId) throws IOException {
        return transition(profileId, revisionId, RevisionStatus.VERIFIED, RevisionStatus.ACTIVE, null);
    }

    public String getManifestSha256(String profileId, String revisionId) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            Path manifestPath = getRevisionManifestPath(profileId, revisionId);
            readVerifiedManifestContent(profileId, revisionId, manifestPath);
            return Files.readString(getRevisionHashPath(profileId, revisionId), StandardCharsets.UTF_8).trim();
        }
    }

    public Path getRevisionManifestPath(String profileId, String revisionId) {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        return getRevisionsRoot(profileId).resolve(revisionId).resolve(REVISION_MANIFEST_FILE_NAME);
    }

    public Path getRevisionHashPath(String profileId, String revisionId) {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        return getRevisionsRoot(profileId).resolve(revisionId).resolve(REVISION_HASH_FILE_NAME);
    }

    private ProfileRevisionManifest transition(String profileId, String revisionId,
                                               RevisionStatus expected, RevisionStatus target,
                                               String gateReportSha256)
            throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            ProfileRevisionManifest revision = loadLocked(profileId, revisionId);
            if (revision.getStatus() != expected) {
                throw new ProfileRevisionException("revision " + revisionId + " is "
                        + revision.getStatus() + ", expected " + expected);
            }
            revision.setStatus(target);
            if (gateReportSha256 != null) {
                revision.setGateReportSha256(gateReportSha256);
            }
            validateRevision(revision, profileId, revisionId);
            writeRevision(getRevisionManifestPath(profileId, revisionId), revision);
            return copyOf(revision);
        }
    }

    private ProfileRevisionManifest loadLocked(String profileId, String revisionId) throws IOException {
        Path manifestPath = getRevisionManifestPath(profileId, revisionId);
        if (!Files.isRegularFile(manifestPath)) {
            throw new ProfileRevisionException("revision manifest is missing: " + manifestPath);
        }
        try {
            byte[] content = readVerifiedManifestContent(profileId, revisionId, manifestPath);
            ProfileRevisionManifest revision = JSON.parseObject(new String(content, StandardCharsets.UTF_8),
                    ProfileRevisionManifest.class);
            if (revision == null) {
                throw new ProfileRevisionException("revision manifest is empty: " + manifestPath);
            }
            validateRevision(revision, profileId, revisionId);
            return revision;
        } catch (ProfileRevisionException e) {
            throw e;
        } catch (ProfileValidationException e) {
            throw new ProfileRevisionException("revision manifest validation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ProfileRevisionException("cannot parse revision manifest: " + manifestPath, e);
        }
    }

    private String nextRevisionId(String profileId) throws IOException {
        Path revisionsRoot = getRevisionsRoot(profileId);
        Files.createDirectories(revisionsRoot);
        int maxRevisionNumber = 0;
        try (Stream<Path> paths = Files.list(revisionsRoot)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isDirectory)::iterator) {
                String revisionId = path.getFileName().toString();
                try {
                    ProfileModelValidator.validateRevisionId(revisionId);
                } catch (ProfileValidationException e) {
                    throw new ProfileRevisionException("invalid revision directory: " + path, e);
                }
                loadLocked(profileId, revisionId);
                maxRevisionNumber = Math.max(maxRevisionNumber,
                        Integer.parseInt(revisionId.substring(1)));
            }
        }
        if (maxRevisionNumber >= MAX_REVISION_NUMBER) {
            throw new ProfileRevisionException("revision sequence is exhausted for profileId: " + profileId);
        }
        return String.format("r%06d", maxRevisionNumber + 1);
    }

    private void writeRevision(Path manifestPath, ProfileRevisionManifest revision) throws IOException {
        byte[] content = JSON.toJSONString(revision).getBytes(StandardCharsets.UTF_8);
        writeAtomically(manifestPath, content);
        writeAtomically(getRevisionHashPath(revision.getProfileId(), revision.getRevisionId()),
                (MessageDigestUtils.sha256(content) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readVerifiedManifestContent(String profileId, String revisionId, Path manifestPath)
            throws IOException {
        Path hashPath = getRevisionHashPath(profileId, revisionId);
        if (!Files.isRegularFile(hashPath)) {
            throw new ProfileRevisionException("revision manifest sha256 is missing: " + hashPath);
        }
        byte[] content = Files.readAllBytes(manifestPath);
        String expectedHash = Files.readString(hashPath, StandardCharsets.UTF_8).trim();
        if (!expectedHash.matches("[0-9a-f]{64}")) {
            throw new ProfileRevisionException("revision manifest sha256 has invalid format: " + hashPath);
        }
        String actualHash = MessageDigestUtils.sha256(content);
        if (!expectedHash.equals(actualHash)) {
            throw new ProfileRevisionException("revision manifest sha256 mismatch: " + manifestPath);
        }
        return content;
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
                throw new ProfileRevisionException("atomic revision replacement is not supported: "
                        + targetPath, e);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void validateRevision(ProfileRevisionManifest revision, String profileId,
                                         String revisionId) throws IOException {
        try {
            ProfileModelValidator.validate(revision);
        } catch (ProfileValidationException e) {
            throw new ProfileRevisionException("revision manifest validation failed: " + e.getMessage(), e);
        }
        if (!profileId.equals(revision.getProfileId()) || !revisionId.equals(revision.getRevisionId())) {
            throw new ProfileRevisionException("revision manifest identity mismatch: " + revisionId);
        }
    }

    private Path getRevisionsRoot(String profileId) {
        return profilesRoot.resolve(REVISIONS_DIRECTORY_NAME).resolve(profileId);
    }

    private static ProfileRevisionManifest copyOf(ProfileRevisionManifest source) {
        return JSON.parseObject(JSON.toJSONString(source), ProfileRevisionManifest.class);
    }
}
