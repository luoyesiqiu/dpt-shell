 package com.luoyesiqiu.shell.util;

import android.content.Context;
import android.os.Process;
import android.system.Os;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {
    public static byte[] readFile(String file){
        FileInputStream fileInputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            fileInputStream = new FileInputStream(file);
            int len = -1;
            byte[] buf = new byte[4096];
            while((len = fileInputStream.read(buf)) != -1){
                byteArrayOutputStream.write(buf,0,len);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            close(fileInputStream);
            close(byteArrayOutputStream);
        }
        return byteArrayOutputStream.toByteArray();
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

    public static byte[] readFromZip(String apkPath,String fileName){

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ZipInputStream zipInputStream = null;
        try {
            zipInputStream =  new ZipInputStream(new FileInputStream(apkPath));
            ZipEntry entry = null;
            while((entry = zipInputStream.getNextEntry())!= null){

                if(entry.getName().equals(fileName)){
                    byte[] buf = new byte[1024];
                    int len = -1;
                    while ((len = zipInputStream.read(buf)) != -1){
                        byteArrayOutputStream.write(buf,0,len);
                    }
                }
            }
        }
        catch (Exception e) {
        }
        finally {
            close(zipInputStream);
        }

        return byteArrayOutputStream.toByteArray();
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
        return new String(byteArrayOutputStream.toByteArray());
    }

    public static String readMaps(Context context){
        StringWriter stringWriter = new StringWriter();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader("/proc/" + Process.myPid() + "/maps"));
            String line = null;
            while((line = in.readLine()) != null){
                if(line.contains(context.getPackageName())) {
                    stringWriter.write(line + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            close(stringWriter);
            close(in);
        }
        return stringWriter.toString();
    }



}
