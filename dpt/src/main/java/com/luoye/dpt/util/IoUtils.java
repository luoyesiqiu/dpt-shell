package com.luoye.dpt.util;

/**
 * @author luoyesiqiu
 */
import java.io.*;

public class IoUtils {
    /**
     * 读取一个文件
     * @param file 要读取的文件
     * @return
     */
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

    /**
     * 将字节数组写入文件
     * @param dest 要写入的文件路径
     * @param data 要写入的字节数据
     */
    public static void writeFile(String dest,byte[] data){
        writeFile(dest,data,false);
    }

    /**
     * 将字节数组追加到文件末尾
     * @param dest 要写入的文件路径
     * @param data 要追加的字节数据
     */
    public static void appendFile(String dest,byte[] data){
        writeFile(dest,data,true);
    }

    /**
     * 将字节数组写入文件
     * @param dest 要写入的文件路径
     * @param data 要写入的字节数据
     * @param append 是否使用追加模式
     */
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

    /**
     * 关闭流
     */
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