package com.luoye.dpt.util;

/**
 * @author luoyesiqiu
 */
public class Md5Utils extends MessageDigestUtils {
    public static String md5(byte[] input){
        return hash("md5",input);
    }

    public static String shortMd5(byte[] input){
        return md5(input).substring(8,24);
    }
}
