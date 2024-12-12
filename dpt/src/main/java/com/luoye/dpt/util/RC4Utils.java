package com.luoye.dpt.util;


import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class RC4Utils {
    public static final String transform = "RC4";
    public static byte[] crypt(byte[] key,byte[] in) {
        try {
            Cipher cipher = Cipher.getInstance(transform);
            SecretKeySpec spec = new SecretKeySpec(key,transform);
            cipher.init(Cipher.ENCRYPT_MODE,spec);
            return cipher.doFinal(in);
        } catch (Exception e) {
        }

        return null;
    }

    public static byte[] generateRC4Key() {
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