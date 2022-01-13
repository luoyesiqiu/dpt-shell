package com.luoye.dpt.util;

/**
 * @author luoyesiqiu
 */
public class ShaUtils extends MessageDigestUtils{
    public static String sha256(byte[] input){
        return hash("sha-256",input);
    }

    public static String shortSha256Left(byte[] input) {
        return sha256(input).substring(0,32);
    }

    public static String shortSha256Right(byte[] input) {
        return sha256(input).substring(32,64);
    }
}
