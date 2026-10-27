package com.luoye.dpt.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;

public final class DexVersionUtils {

    public static final int DEX_VERSION_OFFSET = 4;
    public static final int DEX_MAGIC_SIZE = 8;
    public static final int DEX_HEADER_SIZE = 112;

    public static final int INVALID_DEX_VERSION = -1;


    private DexVersionUtils() {
    }

    public static int readDexVersion(File dexFile) {
        try (RandomAccessFile raf = new RandomAccessFile(dexFile, "r")) {
            byte[] magic = new byte[DEX_MAGIC_SIZE];
            if (raf.read(magic) != DEX_MAGIC_SIZE) {
                return INVALID_DEX_VERSION;
            }
            if (magic[0] != 'd' || magic[1] != 'e' || magic[2] != 'x' || magic[3] != '\n') {
                return INVALID_DEX_VERSION;
            }
            String ver = new String(magic, DEX_VERSION_OFFSET, 3, java.nio.charset.StandardCharsets.US_ASCII);
            return Integer.parseInt(ver);
        } catch (Exception e) {
            LogUtils.warn("read dex version failed for %s: %s", dexFile.getName(), e);
            return INVALID_DEX_VERSION;
        }
    }


    public static void writeDexVersion(File dexFile, int version) {

        if(version == INVALID_DEX_VERSION) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(dexFile, "rw")) {
            long len = raf.length();
            if (len < DEX_HEADER_SIZE) {
                LogUtils.warn("dex too short, skip version restore: %s", dexFile.getName());
                return;
            }

            String ver = String.format(Locale.US, "%03d", version);
            raf.seek(DEX_VERSION_OFFSET);
            raf.write(ver.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        } catch (IOException e) {
            LogUtils.warn("write dex version failed for %s: %s", dexFile.getName(), e);
        }
    }

}
