package com.luoye.dpt.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author luoyesiqiu
 */
public class MessageDigestUtils {
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
                String hex = String.format("%02x",val);
                ret.append(hex);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return ret.toString();
    }
}
