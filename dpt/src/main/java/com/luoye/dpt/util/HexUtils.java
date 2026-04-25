package com.luoye.dpt.util;

import java.util.Arrays;
import java.util.Locale;

public class HexUtils {
    public static String toHexArray(byte[] data){
        return Arrays.toString(toHexStringArray(data));
    }

    public static String toHexString(long l) {
        return "0x" + Long.toHexString(l);
    }

    public static String toHexString(byte[] data) {
        String[] hexStringArray = toHexStringArray(data);
        StringBuilder result = new StringBuilder();
        for (String s : hexStringArray) {
            result.append(s);
        }
        return result.toString();
    }

    private static String[] toHexStringArray(byte[] data) {
        String[] array = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            int value = data[i];
            if(data[i] < 0){
                value = data[i] + 256;
            }
            array[i] = String.format(Locale.US,"%02x",value);
        }

        return array;
    }

}
