package com.luoyesiqiu.shell.util;

public class StringUtils {
    public static boolean isEmpty(String str){
        if(str == null || "".equals(str)){
            return true;
        }
        return false;
    }
}
