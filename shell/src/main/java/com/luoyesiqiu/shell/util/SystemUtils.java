package com.luoyesiqiu.shell.util;

import android.os.Build;

public class SystemUtils {
    public static boolean is64Bits(){
        return Build.SUPPORTED_64_BIT_ABIS.length != 0;
    }
}
