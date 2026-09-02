package com.luoye.dpt.profile.storage;

import com.luoye.dpt.profile.model.ProfileIdentityLock;

public final class ProfileCreationResult {
    private final String profileId;
    private final ProfileIdentityLock identity;
    private final boolean created;

    public ProfileCreationResult(String profileId, ProfileIdentityLock identity, boolean created) {
        this.profileId = profileId;
        this.identity = identity;
        this.created = created;
    }

    public String getProfileId() {
        return profileId;
    }

    public ProfileIdentityLock getIdentity() {
        return identity;
    }

    public boolean isCreated() {
        return created;
    }
}
