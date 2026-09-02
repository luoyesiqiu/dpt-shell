package com.luoye.dpt.profile.generation;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ProfileStableNameDeriver {
    private ProfileStableNameDeriver() {
    }

    public static String identifier(String identitySeed, String label, String prefix,
                                    int hexLength) {
        if (identitySeed == null || !identitySeed.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("identity seed must be 128-bit lowercase hex");
        }
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("derivation label must not be blank");
        }
        if (prefix == null || !prefix.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("identifier prefix is invalid: " + prefix);
        }
        if (hexLength <= 0 || hexLength > 64) {
            throw new IllegalArgumentException("identifier hex length is invalid: " + hexLength);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fromHex(identitySeed), "HmacSHA256"));
            byte[] value = mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
            return prefix + toHex(value).substring(0, hexLength);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot derive stable profile name", e);
        }
    }

    private static byte[] fromHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    private static String toHex(byte[] value) {
        char[] result = new char[value.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < value.length; index++) {
            int current = value[index] & 0xff;
            result[index * 2] = digits[current >>> 4];
            result[index * 2 + 1] = digits[current & 0x0f];
        }
        return new String(result);
    }
}
