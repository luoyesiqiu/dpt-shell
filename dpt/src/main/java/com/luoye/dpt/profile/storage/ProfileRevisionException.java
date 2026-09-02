package com.luoye.dpt.profile.storage;

import java.io.IOException;

public class ProfileRevisionException extends IOException {
    public ProfileRevisionException(String message) {
        super(message);
    }

    public ProfileRevisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
