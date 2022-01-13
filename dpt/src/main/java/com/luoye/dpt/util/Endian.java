package com.luoye.dpt.util;

/**
 * @author luoyesiqiu
 */
public class Endian {
    public static byte[] makeLittleEndian(int number){
        byte[] bytes = new byte[4];
        bytes[0] = (byte)(number & 0xff);
        bytes[1] = (byte)((number & 0xff00) >> 8);
        bytes[2] = (byte)((number & 0xff0000) >> 16);
        bytes[3] = (byte)((number & 0xff000000) >> 24);

        return bytes;
    }

    public static byte[] makeLittleEndian(short number){
        byte[] bytes = new byte[2];
        bytes[0] = (byte)(number & 0xff);
        bytes[1] = (byte)((number & 0xff00) >> 8);

        return bytes;
    }
}
