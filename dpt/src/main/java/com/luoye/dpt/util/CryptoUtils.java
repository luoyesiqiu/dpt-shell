package com.luoye.dpt.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    public static final String RC4Transform = "RC4";
    private static final String HMAC_SHA256 = "HmacSHA256";

    public static byte[] rc4Crypt(byte[] key, byte[] in) {
        try {
            Cipher cipher = Cipher.getInstance(RC4Transform);
            SecretKeySpec spec = new SecretKeySpec(key, RC4Transform);
            cipher.init(Cipher.ENCRYPT_MODE,spec);
            return cipher.doFinal(in);
        } catch (Exception e) {
        }

        return null;
    }

    /**
     * RC4 key for a code item: AES-256 config key followed by little-endian methodIdx.
     */
    public static byte[] buildInsnsRc4Key(byte[] aesKey, int methodIndex) {
        if (aesKey == null || aesKey.length == 0) {
            throw new IllegalArgumentException("aes key is empty");
        }
        ByteBuffer buf = ByteBuffer.allocate(aesKey.length + 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(aesKey);
        buf.putInt(methodIndex);
        return buf.array();
    }

    /**
     * Derive AES-256 key by HMAC-SHA256(randomKey, UTF-8(keyMaterial)).
     */
    public static byte[] hmacSha256(byte[] key, String keyMaterial) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("hmac key is empty");
        }
        if (keyMaterial == null || keyMaterial.isEmpty()) {
            throw new IllegalArgumentException("key material is empty");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            byte[] result = mac.doFinal(keyMaterial.getBytes(StandardCharsets.UTF_8));
            if (result == null || result.length != 32) {
                throw new IllegalStateException("unexpected hmac length");
            }
            return result;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("hmac-sha256 failed", e);
        }
    }

    public static byte[] aesEncrypt(byte[] key, byte[] iv, byte[] in) {
        try {
            Key secretKeySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE,secretKeySpec,ivParameterSpec);
            return cipher.doFinal(in);
        }
        catch (Exception e){
        }
        return null;
    }
}
