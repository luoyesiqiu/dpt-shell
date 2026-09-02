package com.luoye.dpt.profile.storage;

import java.io.IOException;

public class ProfileActiveRevisionException extends IOException {
    public ProfileActiveRevisionException(String message) {
        super(message);
    }

    public ProfileActiveRevisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
