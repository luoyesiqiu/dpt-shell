 package com.luoyesiqiu.shell.util;

import android.content.Context;
import android.util.Log;

import com.luoyesiqiu.shell.Global;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

 public class FileUtils {
     private static final String TAG = "dpt";

     public static void unzipLibs(String sourceDir,String dataDir) {
         String abiName = EnvUtils.getAbiDirName(sourceDir);

         File libsOutDir = new File(dataDir + File.separator + Global.LIB_DIR + File.separator + abiName);
         FileUtils.unzipInNeeded(sourceDir,
                 "assets/" + Global.ZIP_LIB_DIR + "/" + abiName + "/" + Global.SHELL_SO_NAME,
                 libsOutDir.getAbsolutePath());
     }

     public static long getCrc32(File f) {
         FileInputStream fileInputStream = null;
         CheckedInputStream checkedInputStream = null;
         long crcResult = 0L;
         try {
             fileInputStream = new FileInputStream(f);
             checkedInputStream = new CheckedInputStream(fileInputStream,new CRC32());
             int len = -1;
             byte[] buf = new byte[4096];
             while((len = checkedInputStream.read(buf)) != -1) {
             }
             crcResult = checkedInputStream.getChecksum().getValue();
         }
         catch (Throwable e){
         }
         finally {
             FileUtils.close(checkedInputStream);
         }
         return crcResult;
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
         try {
             ZipFile zip = new ZipFile(zipFilePath);
             Enumeration<? extends ZipEntry> entries = zip.entries();
             while(entries.hasMoreElements()){
                 ZipEntry entry = entries.nextElement();

                 if(entry.getName().equals(entryName)) {
                     if(localFileCrc != entry.getCrc()) {
                         byte[] buf = new byte[4096];
                         int len = -1;

                         FileOutputStream fileOutputStream = new FileOutputStream(entryFile);
                         BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                         BufferedInputStream bufferedInputStream = new BufferedInputStream(zip.getInputStream(entry));
                         while ((len = bufferedInputStream.read(buf)) != -1) {
                             bufferedOutputStream.write(buf, 0, len);
                         }
                         Log.d(TAG, "unzip '" + entry.getName() + "' success. local = " + localFileCrc + ", zip = " + entry.getCrc());

                         FileUtils.close(bufferedOutputStream);
                         break;
                     }
                     else {
                         Log.w(TAG, "no need unzip");
                     }
                 }
             }
         }
         catch (Exception e) {
             e.printStackTrace();
         }
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

    public static String readAppName(Context context){
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        InputStream in = null;
        try {
            in = context.getAssets().open("app_name");
            byte[] buf = new byte[1024];
            int len = -1;
            while((len = in.read(buf)) != -1){
                byteArrayOutputStream.write(buf,0,len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            close(byteArrayOutputStream);
            close(in);
        }
        return byteArrayOutputStream.toString();
    }

}
