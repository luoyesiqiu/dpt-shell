package com.luoye.dpt.profile.archive;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only view of a validated, extracted .dptprofile archive. */
public final class ProfileArchiveBundle implements AutoCloseable {
    private final Path archivePath;
    private final Path root;
    private final ProfileArchiveManifest manifest;
    private final ProfileContract contract;
    private final ProfileRevisionManifest revision;
    private final ArtifactHashManifest artifactHashes;
    private final boolean deleteRootOnClose;

    ProfileArchiveBundle(Path archivePath, Path root, ProfileArchiveManifest manifest,
                         ProfileContract contract, ProfileRevisionManifest revision,
                         ArtifactHashManifest artifactHashes, boolean deleteRootOnClose) {
        this.archivePath = archivePath == null ? null : archivePath.toAbsolutePath().normalize();
        this.root = root.toAbsolutePath().normalize();
        this.manifest = manifest;
        this.contract = contract;
        this.revision = revision;
        this.artifactHashes = artifactHashes;
        this.deleteRootOnClose = deleteRootOnClose;
    }

    public Path getArchivePath() {
        return archivePath;
    }

    public Path getRoot() {
        return root;
    }

    public Path getBundleRoot() {
        return root;
    }

    public ProfileArchiveManifest getManifest() {
        return copyManifest(manifest);
    }

    public ProfileArchiveStatus getStatus() {
        return manifest.getStatus();
    }

    public ProfileContract getContract() {
        return ProfileContractCodec.deserialize(ProfileContractCodec.serialize(contract));
    }

    public ProfileRevisionManifest getRevision() {
        return JSON.parseObject(JSON.toJSONString(revision), ProfileRevisionManifest.class);
    }

    public ArtifactHashManifest getArtifactHashes() {
        return JSON.parseObject(JSON.toJSONString(artifactHashes), ArtifactHashManifest.class);
    }

    public Path getProfileManifestPath() {
        return root.resolve(ProfileArchiveReader.PROFILE_MANIFEST_ENTRY);
    }

    public Path getProfileContractPath() {
        return root.resolve(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY);
    }

    public Path getRevisionManifestPath() {
        return root.resolve(ProfileArchiveReader.REVISION_ENTRY);
    }

    public Path getArtifactHashManifestPath() {
        return root.resolve(ProfileArchiveReader.ARTIFACT_HASHES_ENTRY);
    }

    public Path getBuildKeyPath() {
        return root.resolve(ProfileArchiveReader.BUILD_KEY_ENTRY);
    }

    public Path getShellDexPath() {
        return root.resolve(ProfileArchiveReader.SHELL_DEX_ENTRY);
    }

    public Path getShellNativeLibsRoot() {
        return root.resolve(ProfileArchiveReader.SHELL_LIBS_ENTRY);
    }

    public Path getArm64SoPath() {
        return getShellNativeLibsRoot().resolve(contract.getSoName());
    }

    public String getApplicationId() {
        return manifest.getApplicationId();
    }

    public String getProfileId() {
        return manifest.getProfileId();
    }

    public String getRevisionId() {
        return manifest.getRevisionId();
    }

    @Override
    public void close() throws IOException {
        if (!deleteRootOnClose) {
            return;
        }
        deleteTree(root);
    }

    private static ProfileArchiveManifest copyManifest(ProfileArchiveManifest source) {
        return ProfileArchiveManifestCodec.deserialize(ProfileArchiveManifestCodec.serialize(source));
    }

    private static void deleteTree(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
            java.util.List<Path> paths = stream.sorted(java.util.Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList());
            IOException failure = null;
            for (Path child : paths) {
                try {
                    Files.deleteIfExists(child);
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
}
