package com.luoye.dpt.config;

import com.luoye.dpt.util.StringUtils;

/**
 * @author luoyesiqiu
 */
public class Const {

    public static final String OPTION_OPEN_NOISY_LOG_LONG = "noisy-log";

    public static final String OPTION_NO_SIGN_PACKAGE_LONG = "no-sign";
    public static final String OPTION_NO_SIGN_PACKAGE = "x";

    public static final String OPTION_DUMP_CODE_LONG = "dump-code";

    public static final String OPTION_INPUT_FILE = "f";
    public static final String OPTION_INPUT_FILE_LONG = "package-file";

    public static final String OPTION_DEBUGGABLE_LONG = "debug";

    public static final String OPTION_DISABLE_APP_COMPONENT_FACTORY_LONG = "disable-acf";

    public static final String OPTION_OUTPUT_PATH = "o";
    public static final String OPTION_OUTPUT_PATH_LONG = "output";

    public static final String OPTION_EXCLUDE_ABI = "e";
    public static final String OPTION_EXCLUDE_ABI_LONG = "exclude-abi";

    public static final String OPTION_VERSION = "v";
    public static final String OPTION_VERSION_LONG = "version";

    public static final String OPTION_DO_NOT_PROTECT_CLASSES_RULES = "r";
    public static final String OPTION_DO_NOT_PROTECT_CLASSES_RULES_LONG = "rules-file";

    public static final String OPTION_KEEP_CLASSES = "K";
    public static final String OPTION_KEEP_CLASSES_LONG = "keep-classes";

    public static final String OPTION_SMALLER = "S";
    public static final String OPTION_SMALLER_LONG = "smaller";

    public static final String OPTION_PROTECT_CONFIG = "c";
    public static final String OPTION_PROTECT_CONFIG_LONG = "protect-config";

    public static final String OPTION_VERIFY_SIGN = "vs";
    public static final String OPTION_VERIFY_SIGN_LONG = "verify-sign";

    public static final String OPTION_DISABLE_FRIDA_DETECT_LONG = "disable-frida-detect";
    public static final String OPTION_DISABLE_CRC_DETECT_LONG = "disable-crc-detect";
    public static final String OPTION_DISABLE_ANTI_DEBUG_LONG = "disable-anti-debug";

    // Risk check flags: one int, each bit is a switch (1 = disable)
    public static final int FLAG_DISABLE_FRIDA_DETECT = 1;
    public static final int FLAG_DISABLE_CRC_DETECT = 1 << 1;
    public static final int FLAG_DISABLE_ANTI_DEBUG = 1 << 2;

    public static final String KEY_STORE_ASSET_NAME = "dpt.jks";
    public static final String KEY_STORE_ASSET_PATH = "assets/" + KEY_STORE_ASSET_NAME;
    public static final String STORE_PASSWORD = "android";
    public static final String KEY_PASSWORD = "android";
    public static final String KEY_ALIAS = "key0";
    public static final String DEFAULT_THREAD_NAME = "dpt";

    public static final String ROOT_OF_OUT_DIR = System.getProperty("java.io.tmpdir");

    public static final short MULTI_DEX_CODE_VERSION = 2;

    public static final String RC4_KEY_SYMBOL = "DPT_UNKNOWN_DATA";

    public static final String KEY_SHELL_CONFIG_STORE_NAME = "d_shell_data_001";
    public static final String KEY_BUILD_KEY_FILE_NAME = "build-key";
    public static final String KEY_DEXES_STORE_NAME = "i11111i111.zip";
    public static final String KEY_DEXES_STORE_UNALIGNED_NAME = "i11111i111_unaligned.zip";
    public static final String KEY_CODE_ITEM_STORE_NAME = "OoooooOooo";
    public static final String KEY_LIBS_DIR_NAME = "vwwwwwvwww";
    public static final String KEY_JNI_BASE_CLASS_NAME = "JniBridge";
    public static final String DEFAULT_SHELL_PACKAGE_NAME = "com/luoyesiqiu/shell";
    /** Auto shell package placeholder; resolved to "{appPackage}.shell" after package name is known. */
    public static final String SHELL_PACKAGE_NAME_AUTO = "<random>";
    public static final String RANDOM_DIR_NAME = StringUtils.generateIdentifier(16);

}