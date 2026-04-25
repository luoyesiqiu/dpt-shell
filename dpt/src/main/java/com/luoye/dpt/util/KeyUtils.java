package com.luoye.dpt.util;

import java.security.SecureRandom;

public class KeyUtils {

    public static byte[] generateIV(byte[] key) {
        byte[] newKey = new byte[key.length];
        System.arraycopy(key, 0, newKey, 0, newKey.length);
        newKey[3] = 0x2f;
        newKey[9] = 0x76;
        return newKey;
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
