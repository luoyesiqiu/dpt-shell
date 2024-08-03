package com.luoye.dpt.util;

import java.util.Arrays;
import java.util.List;
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

    public static String toHexString(long l) {
        return "0x" + Long.toHexString(l);
    }

    public static String toHexString(byte[] array) {
        Byte[] bytes = new Byte[array.length];
        for (int i = 0; i < array.length; i++) {
            bytes[i] = array[i];
        }
        return toHexString(bytes);
    }

    public static <T> String toHexString(T[] array) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        int len = 0;
        if(array instanceof Byte[]) {
            len = 2;
        }
        else if(array instanceof Short[]) {
            len = 4;
        }
        else if(array instanceof Integer[]) {
            len = 8;
        }
        else if(array instanceof Long[]) {
            len = 16;
        }
        else {
            throw new IllegalArgumentException("unsupport type: " + array.getClass());
        }

        for (int i = 0; i < array.length; i++) {
            stringBuilder.append(String.format(Locale.US,"%0" + len + "x",array[i]));
            if(i < array.length - 1) {
                stringBuilder.append(" ");
            }
        }
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    public static <T> String toHexString(List<T> list) {
        if(list == null) {
            return "";
        }
        T t = list.get(0);
        if(t instanceof Byte) {
            Byte[] array = new Byte[list.size()];
            return toHexString(list.toArray(array));

        }
        else if(t instanceof Short) {
            Short[] array = new Short[list.size()];
            return toHexString(list.toArray(array));
        }
        else if(t instanceof Integer) {
            Integer[] array = new Integer[list.size()];
            return toHexString(list.toArray(array));
        }
        else if(t instanceof Long) {
            Long[] array = new Long[list.size()];
            return toHexString(list.toArray(array));
        }
        else {
            throw new IllegalArgumentException("unsupport type: " + list.getClass());
        }

    }
}
