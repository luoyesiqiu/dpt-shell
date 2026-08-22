package com.luoye.dpt;

import com.luoye.dpt.util.CryptoUtils;
import com.luoye.dpt.util.KeyUtils;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ConfigKeyDerivationTest {

    @Test
    public void testHmacSha256Rfc4231() {
        // RFC 4231 Test Case 1
        byte[] key = new byte[20];
        for (int i = 0; i < key.length; i++) {
            key[i] = 0x0b;
        }
        byte[] expected = hexToBytes("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
        byte[] actual = CryptoUtils.hmacSha256(key, "Hi There");
        Assert.assertArrayEquals(expected, actual);
        Assert.assertEquals(32, actual.length);
    }

    @Test
    public void testDeriveAes256KeyAndEncryptConfig() {
        byte[] randomKey = new byte[16];
        for (int i = 0; i < randomKey.length; i++) {
            randomKey[i] = (byte) (0x10 + i);
        }
        randomKey[3] = 0x20;
        randomKey[9] = 0x74;

        String packageName = "com.example.app";
        String keyMaterial = packageName + "_" + "a1b2c3d4e5f67890";
        byte[] aesKey = CryptoUtils.hmacSha256(randomKey, keyMaterial);
        Assert.assertEquals(32, aesKey.length);

        byte[] iv = KeyUtils.generateIV(randomKey);
        Assert.assertEquals(16, iv.length);
        Assert.assertEquals((byte) 0x2f, iv[3]);
        Assert.assertEquals((byte) 0x76, iv[9]);

        byte[] plain = "{\"app_name\":\"com.example.App\"}".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = CryptoUtils.aesEncrypt(aesKey, iv, plain);
        Assert.assertNotNull(cipher);
        Assert.assertTrue(cipher.length > 0);
        Assert.assertEquals(0, cipher.length % 16);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHmacRejectEmptyKeyMaterial() {
        CryptoUtils.hmacSha256(new byte[16], "");
    }

    @Test
    public void testGetBuildKeyWithoutJarManifest() {
        // Without shell-files/build-key, unit tests usually fall back to jar manifest (often absent).
        String buildKey = Dpt.getBuildKey();
        Assert.assertTrue(buildKey == null || !buildKey.isEmpty());
    }

    @Test
    public void testInsnsRc4KeyAppendsLittleEndianMethodId() {
        byte[] aesKey = new byte[32];
        for (int i = 0; i < aesKey.length; i++) {
            aesKey[i] = (byte) i;
        }

        byte[] key = CryptoUtils.buildInsnsRc4Key(aesKey, 0x01020304);
        Assert.assertEquals(36, key.length);
        Assert.assertArrayEquals(aesKey, Arrays.copyOf(key, 32));
        Assert.assertEquals((byte) 0x04, key[32]);
        Assert.assertEquals((byte) 0x03, key[33]);
        Assert.assertEquals((byte) 0x02, key[34]);
        Assert.assertEquals((byte) 0x01, key[35]);

        byte[] otherKey = CryptoUtils.buildInsnsRc4Key(aesKey, 0x01020305);
        Assert.assertFalse(Arrays.equals(key, otherKey));

        byte[] plain = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] enc = CryptoUtils.rc4Crypt(key, plain);
        byte[] otherEnc = CryptoUtils.rc4Crypt(otherKey, plain);
        Assert.assertNotNull(enc);
        Assert.assertNotNull(otherEnc);
        Assert.assertFalse(Arrays.equals(enc, otherEnc));
        Assert.assertArrayEquals(plain, CryptoUtils.rc4Crypt(key, enc));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildInsnsRc4KeyRejectsEmptyAesKey() {
        CryptoUtils.buildInsnsRc4Key(new byte[0], 1);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
