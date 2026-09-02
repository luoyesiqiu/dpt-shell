package com.luoye.dpt.profile.error;

import java.io.IOException;

public final class ProfileCommandException extends IOException {
    private final ProfileErrorCode errorCode;
    private final String displayMessage;

    public ProfileCommandException(ProfileErrorCode errorCode, String displayMessage) {
        this(errorCode, displayMessage, null);
    }

    public ProfileCommandException(ProfileErrorCode errorCode, String displayMessage, Throwable cause) {
        super("[" + errorCode.getCode() + "] " + displayMessage, cause);
        this.errorCode = errorCode;
        this.displayMessage = displayMessage;
    }

    public ProfileErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDisplayMessage() {
        return displayMessage;
    }
}
