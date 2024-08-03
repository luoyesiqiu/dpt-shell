package com.luoye.dpt.util;

/**
 * @author luoyesiqiu
 */
import java.io.*;

public class IoUtils {

    public static byte[] readFile(String file, long offset ,int len){
        FileInputStream fileInputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            fileInputStream = new FileInputStream(file);
            byte[] buf = new byte[len];
            fileInputStream.skip(offset);
            fileInputStream.read(buf);
            byteArrayOutputStream.write(buf);
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

    public static void writeFile(String dest ,byte[] data ,long offset){
        RandomAccessFile randomAccessFile = null;
        try{
            randomAccessFile = new RandomAccessFile(new File(dest),"rw");
            randomAccessFile.seek(offset);
            randomAccessFile.write(data);
        }
        catch (IOException e){
            e.printStackTrace();
        }
        finally {
            close(randomAccessFile);
        }
    }

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

    public static void writeFile(String dest,byte[] data){
        writeFile(dest,data,false);
    }

    public static void appendFile(String dest,byte[] data){
        writeFile(dest,data,true);
    }

    public static void writeFile(String dest,byte[] data,boolean append){
        FileOutputStream fileOutputStream = null;
        try{
            fileOutputStream = new FileOutputStream(dest,append);
            fileOutputStream.write(data);
        }
        catch (IOException e){
            e.printStackTrace();
        }
        finally {
            close(fileOutputStream);
        }
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