package com.luoye.dpt.profile.generation.overlay;

import java.io.IOException;

public class ProfileOverlayException extends IOException {
    public ProfileOverlayException(String message) {
        super(message);
    }

    public ProfileOverlayException(String message, Throwable cause) {
        super(message, cause);
    }
}
