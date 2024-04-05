package com.luoye.dpt.plugin.asm.util;

import java.util.Locale;

public class LogUtils {

    private static final String TAG = "JunkCodePlugin";

    private static boolean sOpenLog = true;

    public static void openLog(boolean openLog) {
        sOpenLog = openLog;
    }

    private static String format(String fmt, Object... args) {
        String format = String.format(Locale.US, "[*] %s %s", TAG, fmt);
        return String.format(Locale.US, format, args);
    }


    public static void debug(String fmt, Object ...args) {
        if(sOpenLog) {
            String msg = format(fmt, args);
            System.out.println(msg);
        }
    }

    public static void error(String fmt, Object ...args) {
        if(sOpenLog) {
            String msg = format(fmt, args);
            System.err.println(msg);
        }
    }
}
