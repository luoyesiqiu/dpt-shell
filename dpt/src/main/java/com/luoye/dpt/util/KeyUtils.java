package com.luoye.dpt.util;

import java.security.SecureRandom;

public class KeyUtils {

    public static byte[] generateIV(byte[] key) {
        key[3] = 0x2f;
        key[9] = 0x76;
        return key;
    }

    public static byte[] generateKey() {
        byte[] rc4key = new byte[16];
        SecureRandom secureRandom = new SecureRandom();
        for(int i = 0;i < rc4key.length;i++) {
            secureRandom.nextBytes(rc4key);
        }

        rc4key[3] = 0x20;
        rc4key[9] = 0x74;
        return rc4key;
    }
}
