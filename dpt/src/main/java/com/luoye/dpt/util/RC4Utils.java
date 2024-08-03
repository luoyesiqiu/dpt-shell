package com.luoye.dpt.util;


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
}