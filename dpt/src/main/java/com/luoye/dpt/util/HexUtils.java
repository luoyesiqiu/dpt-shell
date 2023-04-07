package com.luoye.dpt.util;

import java.util.Arrays;
import java.util.Locale;

public class HexUtils {
    public static String toHexArray(byte[] data){
        String[] array = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            int value = data[i];
            if(data[i] < 0){
                value = data[i] + 256;
            }
            array[i] = String.format(Locale.US,"%02x",value);
        }
        return Arrays.toString(array);
    }
}
