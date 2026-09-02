package com.luoye.dpt.profile.storage;

public class ProfileRegistryException extends IllegalStateException {
    public ProfileRegistryException(String message) {
        super(message);
    }

    public ProfileRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
