package com.luoye.dpt.profile.apk;

import com.luoye.dpt.profile.archive.ProfileArchiveBundle;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable Profile inputs resolved for one APK protection run. */
public final class ApkProfileProtectionContext {
    private final String applicationId;
    private final String profileId;
    private final String revisionId;
    private final Set<String> businessNativeAbis;
    private final ProfileBundle bundle;
    private final ProfileArchiveBundle archiveBundle;

    ApkProfileProtectionContext(String applicationId, String profileId, String revisionId,
                                Set<String> businessNativeAbis, ProfileBundle bundle) {
        if (applicationId == null || profileId == null || revisionId == null
                || businessNativeAbis == null || bundle == null) {
            throw new IllegalArgumentException("APK Profile context arguments must not be null");
        }
        this.applicationId = applicationId;
        this.profileId = profileId;
        this.revisionId = revisionId;
        this.businessNativeAbis = Collections.unmodifiableSet(
                new LinkedHashSet<>(businessNativeAbis));
        this.bundle = bundle;
        this.archiveBundle = null;
    }

    private ApkProfileProtectionContext(String applicationId, String profileId, String revisionId,
                                        Set<String> businessNativeAbis,
                                        ProfileArchiveBundle archiveBundle) {
        if (applicationId == null || profileId == null || revisionId == null
                || businessNativeAbis == null || archiveBundle == null) {
            throw new IllegalArgumentException("APK Profile context arguments must not be null");
        }
        this.applicationId = applicationId;
        this.profileId = profileId;
        this.revisionId = revisionId;
        this.businessNativeAbis = Collections.unmodifiableSet(
                new LinkedHashSet<>(businessNativeAbis));
        this.bundle = null;
        this.archiveBundle = archiveBundle;
    }

    public static ApkProfileProtectionContext fromArchive(String applicationId, String profileId,
                                                           String revisionId,
                                                           Set<String> businessNativeAbis,
                                                           ProfileArchiveBundle archiveBundle) {
        return new ApkProfileProtectionContext(applicationId, profileId, revisionId,
                businessNativeAbis, archiveBundle);
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public Set<String> getBusinessNativeAbis() {
        return businessNativeAbis;
    }

    public ProfileContract getContract() {
        return bundle != null ? bundle.getContract() : archiveBundle.getContract();
    }

    public Path getBundleRoot() {
        return bundle != null ? bundle.getRoot() : archiveBundle.getRoot();
    }

    public Path getShellDexPath() {
        return bundle != null ? bundle.getShellDexPath() : archiveBundle.getShellDexPath();
    }

    public Path getArm64ShellSoPath() {
        return bundle != null ? bundle.getArm64SoPath() : archiveBundle.getArm64SoPath();
    }

    public Path getShellNativeLibsRoot() {
        if (bundle != null) {
            return bundle.getRoot().resolve(ProfileBundleAssembler.SHELL_FILES_DIRECTORY_NAME)
                    .resolve("libs");
        }
        // Archive entries use the canonical arm64-v8a child under this root.
        return archiveBundle.getRoot().resolve(ProfileArchiveBundlePath.SHELL_FILES_LIBS_ROOT);
    }

    public void close() throws IOException {
        if (archiveBundle != null) {
            archiveBundle.close();
        }
    }

    private static final class ProfileArchiveBundlePath {
        private static final String SHELL_FILES_LIBS_ROOT = "shell-files/libs";
    }
}
