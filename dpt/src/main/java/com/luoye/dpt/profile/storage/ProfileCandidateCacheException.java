package com.luoye.dpt.profile.storage;

import java.io.IOException;

/** Indicates that a candidate cache entry is present but cannot be trusted. */
public final class ProfileCandidateCacheException extends IOException {
    public ProfileCandidateCacheException(String message) {
        super(message);
    }

    public ProfileCandidateCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
