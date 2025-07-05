package com.luoye.dpt.util;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.Adler32;

/**
 * @author luoyesiqiu
 */
public class FileUtils {

    private static final String PIPE_PREFIX = "│   ";
    private static final String ELBOW_PREFIX = "└── ";
    private static final String T_PREFIX = "├── ";


    public static String getNewFileName(String fileName, String tag){
        String fileSuffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        return fileName.replaceAll("\\." + fileSuffix + "$","_" + tag + "." + fileSuffix) ;
    }

    public static String getNewFileSuffix(String fileName, String newSuffix){
        String fileSuffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        return fileName.replaceAll("\\." + fileSuffix + "$",  "." + newSuffix) ;
    }

    /**
     * Get a directory and create it if it doesn't exist
     */
    public static File getDir(String path, String dirName){
        File dirFile = new File(path,dirName);
        if(!dirFile.exists()){
            dirFile.mkdirs();
        }
        return dirFile;
    }

    /**
     * Recursively delete directories or files
     */
    public static void deleteRecurse(File file) {
        try {
            if(file.isFile()) {
                file.delete();
            }
            else {
                org.apache.commons.io.FileUtils.deleteDirectory(file);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the directory where the current command tool is located
     */
    public static String getExecutablePath(){
        return System.getProperty("user.dir");
    }

    /**
     * fixCheckSumHeader
     * @param dexBytes
     */
    public static void fixCheckSumHeader(byte[] dexBytes) {
        Adler32 adler = new Adler32();
        adler.update(dexBytes, 12, dexBytes.length - 12);
        long value = adler.getValue();
        int va = (int) value;
        byte[] newcs = intToByte(va);
        byte[] recs = new byte[4];
        for (int i = 0; i < 4; i++) {
            recs[i] = newcs[newcs.length - 1 - i];
        }
        System.arraycopy(recs, 0, dexBytes, 8, 4);
    }


    /**
     * intToByte
     * @param number
     * @return
     */
    public static byte[] intToByte(int number) {
        byte[] b = new byte[4];
        for (int i = 3; i >= 0; i--) {
            b[i] = (byte) (number % 256);
            number >>= 8;
        }
        return b;
    }

    /**
     * fixSHA1Header
     * @param dexBytes
     * @throws NoSuchAlgorithmException
     */
    public static void fixSHA1Header(byte[] dexBytes)
            throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(dexBytes, 32, dexBytes.length - 32);
        byte[] newdt = md.digest();
        System.arraycopy(newdt, 0, dexBytes, 12, 20);
        String hexstr = "";
        for (int i = 0; i < newdt.length; i++) {
            hexstr += Integer.toString((newdt[i] & 0xff) + 0x100, 16)
                    .substring(1);
        }
    }

    /**
     * fixFileSizeHeader
     * @param dexBytes
     */
    public static void fixFileSizeHeader(byte[] dexBytes) {
        byte[] newfs = intToByte(dexBytes.length);
        byte[] refs = new byte[4];
        for (int i = 0; i < 4; i++) {
            refs[i] = newfs[newfs.length - 1 - i];
        }
        System.arraycopy(refs, 0, dexBytes, 32, 4);//修改（32-35）
    }

    public static String getJarSignerCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "jarsigner.exe";
        } else {
            return "jarsigner";
        }
    }

    public synchronized static void printDirectoryTree(File directory, String prefix) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                boolean isLast = i == files.length - 1;
                String currentPrefix = isLast ? ELBOW_PREFIX : T_PREFIX;
                System.out.println(prefix + currentPrefix + file.getName());
                if (file.isDirectory()) {
                    String newPrefix = prefix + (isLast ? "    " : PIPE_PREFIX);
                    printDirectoryTree(file, newPrefix);
                }
            }
        }
    }

    public synchronized static void printDirectoryTree(File directory) {
        System.out.println(directory.getAbsolutePath());
        printDirectoryTree(directory, "");
    }

    public synchronized static void printDirectoryTreeSimple(File directory) {
        System.out.println(directory.getAbsolutePath());
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                boolean isLast = i == files.length - 1;
                String currentPrefix = isLast ? ELBOW_PREFIX : T_PREFIX;
                String suffix = file.isDirectory() ? File.separator : "";
                System.out.println(currentPrefix + file.getName() + suffix);
            }
        }
    }
}
