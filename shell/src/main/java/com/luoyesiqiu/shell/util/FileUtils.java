 package com.luoyesiqiu.shell.util;

import android.content.Context;
import android.util.Log;

import com.luoyesiqiu.shell.Global;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

 public class FileUtils {
     private static final String TAG = "dpt";

     public static void unzipLibs(String sourceDir,String dataDir) {
         String abiName = EnvUtils.getAbiDirName(sourceDir);

         File libsOutDir = new File(dataDir + File.separator + Global.LIB_DIR + File.separator + abiName);
         File shellSoFile = new File(libsOutDir.getAbsolutePath(),Global.SHELL_SO_NAME);
         if(!shellSoFile.exists()) {
             FileUtils.unzip(sourceDir,
                     "assets/" + Global.ZIP_LIB_DIR + "/" + abiName + "/", /* 注意最后一个斜杠 */
                     libsOutDir.getAbsolutePath());
         }
     }
     public static void unzip(String zipFilePath,String entryName,String outDir){
         long start = System.currentTimeMillis();
         File out = new File(outDir);
         if(!out.exists()){
             out.mkdirs();
         }
         try {
             ZipFile zip = new ZipFile(zipFilePath);
             Enumeration<? extends ZipEntry> entries = zip.entries();
             while(entries.hasMoreElements()){
                 ZipEntry entry = entries.nextElement();

                 if(entry.getName().startsWith(entryName)) {
                     byte[] buf = new byte[4096];
                     int len = -1;
                     File entryFile = new File(outDir + File.separator  + Global.SHELL_SO_NAME);
                     BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(entryFile));
                     BufferedInputStream bufferedInputStream = new BufferedInputStream(zip.getInputStream(entry));
                     while ((len = bufferedInputStream.read(buf)) != -1) {
                         bufferedOutputStream.write(buf, 0, len);
                     }
                     FileUtils.close(bufferedOutputStream);
                     break;
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
