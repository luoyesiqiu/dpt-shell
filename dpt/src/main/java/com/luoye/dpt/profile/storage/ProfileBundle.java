package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.ProfileContract;

import java.nio.file.Path;

/**
 * Immutable view of an assembled Profile candidate Bundle.
 */
public class ProfileBundle {
    private final Path root;
    private final ProfileContract contract;
    private final ArtifactHashManifest artifactHashes;

    ProfileBundle(Path root, ProfileContract contract, ArtifactHashManifest artifactHashes) {
        if (root == null || contract == null || artifactHashes == null) {
            throw new IllegalArgumentException("Profile Bundle result arguments must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        this.contract = ProfileContractCodec.deserialize(ProfileContractCodec.serialize(contract));
        this.artifactHashes = JSON.parseObject(JSON.toJSONString(artifactHashes),
                ArtifactHashManifest.class);
    }

    public Path getRoot() {
        return root;
    }

    public Path getBundleRoot() {
        return root;
    }

    public Path getRevisionManifestPath() {
        return root.resolve(ProfileBundleAssembler.REVISION_MANIFEST_FILE_NAME);
    }

    public Path getProfileContractPath() {
        return root.resolve(ProfileBundleAssembler.PROFILE_CONTRACT_FILE_NAME);
    }

    public Path getArtifactHashManifestPath() {
        return root.resolve(ProfileBundleAssembler.ARTIFACT_HASH_MANIFEST_FILE_NAME);
    }

    public Path getBuildKeyPath() {
        return root.resolve(ProfileBundleAssembler.BUILD_KEY_RELATIVE_PATH);
    }

    public Path getShellDexPath() {
        return root.resolve(ProfileBundleAssembler.SHELL_DEX_RELATIVE_PATH);
    }

    public Path getArm64SoPath() {
        return root.resolve(ProfileBundleAssembler.ARM64_SO_DIRECTORY)
                .resolve(contract.getSoName());
    }

    public Path getShellSoPath() {
        return getArm64SoPath();
    }

    public Path getArm64NativeSoPath() {
        return getArm64SoPath();
    }

    public ProfileContract getContract() {
        return ProfileContractCodec.deserialize(ProfileContractCodec.serialize(contract));
    }

    public ProfileContract getProfileContract() {
        return getContract();
    }

    public ArtifactHashManifest getArtifactHashes() {
        return JSON.parseObject(JSON.toJSONString(artifactHashes), ArtifactHashManifest.class);
    }

    public ArtifactHashManifest getArtifactHashManifest() {
        return getArtifactHashes();
    }

    public String getProfileId() {
        return contract.getProfileId();
    }

    public String getRevisionId() {
        return contract.getRevisionId();
    }
}
