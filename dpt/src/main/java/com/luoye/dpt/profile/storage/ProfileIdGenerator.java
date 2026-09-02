package com.luoye.dpt.profile.storage;

import java.security.SecureRandom;

public final class ProfileIdGenerator {
    private final SecureRandom secureRandom;

    public ProfileIdGenerator() {
        this(new SecureRandom());
    }

    ProfileIdGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] value = new byte[8];
        secureRandom.nextBytes(value);
        return "pf_" + toHex(value);
    }

    static String toHex(byte[] value) {
        char[] result = new char[value.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < value.length; i++) {
            int unsigned = value[i] & 0xff;
            result[i * 2] = digits[unsigned >>> 4];
            result[i * 2 + 1] = digits[unsigned & 0x0f];
        }
        return new String(result);
    }
}
