package com.luoye.dpt.util;

/**
 * @author luoyesiqiu
 */
public class ShaUtils extends MessageDigestUtils{
    private static final String ALGORITHM = "sha-256";

    public static String sha256(byte[] input){
        return hash(ALGORITHM,input);
    }

    public static String shortSha256Left(byte[] input) {
        return sha256(input).substring(0,32);
    }

    public static String shortSha256Right(byte[] input) {
        return sha256(input).substring(32,64);
    }
}
