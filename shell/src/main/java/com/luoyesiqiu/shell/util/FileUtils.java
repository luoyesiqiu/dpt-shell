package com.luoyesiqiu.shell.util;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.luoyesiqiu.shell.Global;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtils {
    private static final String TAG = "dpt";

    public static void unzipLibs(String sourceDir, String dataDir) {
        String abiName = EnvUtils.getAbiDirName();

        File libsOutDir = new File(dataDir + File.separator + Global.LIB_DIR + File.separator + abiName);
        FileUtils.unzipInNeeded(sourceDir,
                "assets/" + Global.ZIP_LIB_DIR + "/" + abiName + "/" + Global.SHELL_SO_NAME,
                libsOutDir.getAbsolutePath());
    }

    public static long getCrc32(File f) {
        long crcResult = 0L;
        try (FileInputStream fileInputStream = new FileInputStream(f);
             CheckedInputStream checkedInputStream = new CheckedInputStream(fileInputStream, new CRC32())) {
            byte[] buf = new byte[4096];
            while (checkedInputStream.read(buf) != -1) {
            }
            crcResult = checkedInputStream.getChecksum().getValue();
        }
        catch (Throwable e){
        }
        return crcResult;
    }

    /**
     * Android linker refuses to load writable ELF files (e.g. mode 0600 from FileOutputStream).
     * Keep shell libs read-only, consistent with dex extract chmod(..., 0444).
     */
    static void makeShellLibLoadable(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            Os.chmod(file.getAbsolutePath(), 0444);
        } catch (ErrnoException e) {
            Log.w(TAG, "chmod shell lib failed: " + file.getAbsolutePath(), e);
            //noinspection ResultOfMethodCallIgnored
            file.setReadable(true, false);
            //noinspection ResultOfMethodCallIgnored
            file.setWritable(false, false);
        }
    }

    public static void unzipInNeeded(String zipFilePath, String entryName, String outDir){
        long start = System.currentTimeMillis();
        File out = new File(outDir);
        if(!out.exists()){
            out.mkdirs();
        }

        long localFileCrc = 0L;
        File entryFile = new File(outDir + File.separator  + Global.SHELL_SO_NAME);
        if(entryFile.exists()){
            localFileCrc = getCrc32(entryFile);
        }
        try (ZipFile zip = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();

                if(!entry.getName().equals(entryName)) {
                    continue;
                }

                if(localFileCrc != entry.getCrc()) {
                    byte[] buf = new byte[4096];
                    int len = -1;

                    // Previous extract may have left the library read-only.
                    if(entryFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        entryFile.setWritable(true);
                    }

                    try (FileOutputStream fileOutputStream = new FileOutputStream(entryFile);
                         BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                         BufferedInputStream bufferedInputStream = new BufferedInputStream(zip.getInputStream(entry))) {
                        while ((len = bufferedInputStream.read(buf)) != -1) {
                            bufferedOutputStream.write(buf, 0, len);
                        }
                    }
                    Log.d(TAG, "unzip '" + entry.getName() + "' success. local = " + localFileCrc + ", zip = " + entry.getCrc());
                    break;
                }
                else {
                    Log.w(TAG, "no need unzip");
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        // Always enforce read-only mode so System.load succeeds on Android 14+/HyperOS.
        makeShellLibLoadable(entryFile);
        Log.d(TAG, "unzip libs took: " + (System.currentTimeMillis() - start) + "ms" );
    }

    public static void close(Closeable closeable){
        if(closeable != null){
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
