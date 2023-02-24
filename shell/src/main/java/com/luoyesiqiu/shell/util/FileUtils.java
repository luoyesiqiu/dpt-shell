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
