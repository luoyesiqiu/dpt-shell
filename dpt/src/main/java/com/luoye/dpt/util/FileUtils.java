package com.luoye.dpt.util;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

/**
 * @author luoyesiqiu
 */
public class FileUtils {

    public static void copy(String src,String dest){
        File srcFile = new File(src);
        File destFile = new File(dest);
        Queue<File> queue = new LinkedList<>();
        queue.offer(srcFile);
        while(!queue.isEmpty()) {
            File origin = queue.poll();
            File target = null;
            if (origin.isDirectory()) {
                target = new File(destFile, origin.getName());
                File[] subOrigin = origin.listFiles();
                if(subOrigin != null) {
                    for (File f : subOrigin) {
                        queue.offer(f);
                    }
                }
                if (!target.exists()) {
                    target.mkdirs();
                }
            } else {

                target = new File(dest,origin.getParentFile().getName());
                File targetFile = new File(target,origin.getName());
                byte[] data = IoUtils.readFile(origin.getAbsolutePath());
                IoUtils.writeFile(targetFile.getAbsolutePath(),data);
            }
        }
    }

    public static String getNewFileName(String fileName,String tag){
        String fileSuffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        return fileName.replaceAll("\\." + fileSuffix + "$","_" + tag + "." + fileSuffix) ;
    }

    public static String getNewFileSuffix(String fileName,String newSuffix){
        String fileSuffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        return fileName.replaceAll("\\." + fileSuffix + "$",  "." + newSuffix) ;
    }

    /**
     * Get a directory and create it if it doesn't exist
     */
    public static File getDir(String path,String dirName){
        File dirFile = new File(path,dirName);
        if(!dirFile.exists()){
            dirFile.mkdirs();
        }
        return dirFile;
    }

    /**
     * Recursively delete directories or files
     */
    public static void deleteRecurse(File file){
        if(file.isDirectory()){
            File[] files = file.listFiles();
            if(files != null){
                for (File f : files) {
                    deleteRecurse(f);
                }
            }
        }
        file.delete();
    }

    /**
     * Gets the directory where the current command tool is located
     */
    public static String getExecutablePath(){
        return System.getProperty("user.dir");
    }
}
