package com.luoye.dpt.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * @author luoyesiqiu
 */
public class MessageDigestUtils {
    private static final String ALGORITHM_MD5 = "md5";
    private static final String ALGORITHM_SHA256 = "sha-256";
    public static String hash(String algorithm,byte[] input){
        StringBuilder ret = new StringBuilder();
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            byte[] buf = messageDigest.digest(input);
            for (byte b : buf){
                int val = b;
                if(val < 0){
                    val += 256;
                }
                String hex = String.format(Locale.US,"%02x",val);
                ret.append(hex);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return ret.toString();
    }

    public static String md5(byte[] input){
        return hash(ALGORITHM_MD5,input);
    }

    public static String shortMd5(byte[] input){
        return md5(input).substring(8,24);
    }

    public static String sha256(byte[] input){
        return hash(ALGORITHM_SHA256,input);
    }

    public static String shortSha256Left(byte[] input) {
        return sha256(input).substring(0,32);
    }

    public static String shortSha256Right(byte[] input) {
        return sha256(input).substring(32,64);
    }
}
