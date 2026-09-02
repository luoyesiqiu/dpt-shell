package com.luoye.dpt.profile.apk;

import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;

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
        return bundle.getContract();
    }

    public Path getBundleRoot() {
        return bundle.getRoot();
    }

    public Path getShellDexPath() {
        return bundle.getShellDexPath();
    }

    public Path getArm64ShellSoPath() {
        return bundle.getArm64SoPath();
    }

    public Path getShellNativeLibsRoot() {
        return bundle.getRoot().resolve(ProfileBundleAssembler.SHELL_FILES_DIRECTORY_NAME)
                .resolve("libs");
    }
}
