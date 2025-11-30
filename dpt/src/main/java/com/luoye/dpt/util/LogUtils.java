package com.luoye.dpt.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author luoyesiqiu
 */
public class LogUtils {

    private static final String TAG = "dpt-processor";
    private static volatile boolean openLog = true;
    private static volatile boolean openNoisyLog = false;

    public static final String ANSI_RESET = "\033[0m";
    public static final String ANSI_RED = "\033[31m";
    public static final String ANSI_GREEN = "\033[32m";
    public static final String ANSI_YELLOW = "\033[33m";

    private enum LogType {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static void setOpenLog(boolean open) {
        openLog = open;
    }

    public static void setOpenNoisyLog(boolean open) {
        openNoisyLog = open;
    }

    public static void info(String fmt, Object... args) {
        println(LogType.INFO, TAG, String.format(Locale.US, fmt, args));
    }

    public static void debug(String fmt, Object... args) {
        println(LogType.DEBUG, TAG, String.format(Locale.US, fmt, args));
    }

    public static void warn(String fmt, Object... args) {
        println(LogType.WARN, TAG, String.format(Locale.US, fmt, args));
    }

    public static void error(String fmt, Object... args) {
        println(LogType.ERROR, TAG, String.format(Locale.US, fmt, args));
    }

    public static void noisy(String fmt, Object... args) {
        if (openNoisyLog) {
            println(LogType.INFO, TAG, String.format(Locale.US, fmt, args));
        }
    }

    private static void println(LogType type, String tag, String msg) {
        if (!openLog) {
            return;
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        String threadName = "[" + Thread.currentThread().getName() + "]";
        String timeOut = simpleDateFormat.format(new Date());

        msg = StringUtils.capitalizeFirstLetter(msg);
        String color = "";
        switch (type) {
            case WARN:
                color = ANSI_YELLOW;
                break;
            case ERROR:
                color = ANSI_RED;
                break;
        }

        System.out.println(color + timeOut + "\t" + threadName + "\t" + tag + "\t" + msg + ANSI_RESET);

    }

}