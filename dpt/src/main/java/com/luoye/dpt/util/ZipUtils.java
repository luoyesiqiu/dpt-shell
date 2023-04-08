package com.luoye.dpt.util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {

    private static final String RENAME_SUFFIX = ".renamed";
    /**
     * 读取资源
     */
    public static void readResourceFromRuntime(String resourcePath,String distPath) throws IOException{
        InputStream inputStream = ZipUtils.class.getClassLoader().getResourceAsStream(resourcePath);
        if(inputStream == null){
            throw new IOException("cannot get resource:" + resourcePath);
        }
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        File distFile = new File(distPath);
        if(!distFile.getParentFile().exists()){
            distFile.getParentFile().mkdirs();
        }
        try {
            in = new BufferedInputStream(inputStream);
            out = new BufferedOutputStream(new FileOutputStream(distFile));

            int len = -1;
            byte[] b = new byte[1024];
            while ((len = in.read(b)) != -1) {
                out.write(b, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IoUtils.close(out);
            IoUtils.close(in);
        }
    }
    /**
     * 压缩文件/文件夹为apk
     */
    public static void compressToApk(String srcDir,String destFile)  {
        ZipFile zipFile =  null;
        try {
             zipFile = new ZipFile(destFile);
            File dir = new File(srcDir);
            File[] list = dir.listFiles();
            if(list == null){
                return;
            }
            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
            for(File f : list){
                if(f.isDirectory()){
                    zipFile.addFolder(f.getAbsoluteFile(),zipParameters);
                }
                else{
                    if(f.getName().equals("resources.arsc")) {
                        zipParameters.setCompressionMethod(CompressionMethod.STORE);
                    }
                    zipFile.addFile(f.getAbsoluteFile(),zipParameters);
                }
            }

            List<FileHeader> fileHeaders = zipFile.getFileHeaders();
            for (FileHeader fileHeader : fileHeaders) {
                String fileName = fileHeader.getFileName();
                if (fileName.contains(RENAME_SUFFIX)) {
                    String newFileName = fileName.replaceAll(RENAME_SUFFIX + "\\d+$", "");
                    zipFile.renameFile(fileHeader, newFileName);
                    LogUtils.noisy("compress file name restore: %s -> %s", fileName, newFileName);

                }
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            IoUtils.close(zipFile);
        }
    }

    private static void writeZipEntry(ZipInputStream zipInputStream,String targetFilePath){
        FileOutputStream fos = null;
        try {
            File targetFile = new File(targetFilePath);
            if(!targetFile.getParentFile().exists()){
                targetFile.getParentFile().mkdirs();
            }
            fos = new FileOutputStream(targetFile);
            int len = 0;
            byte[] buf = new byte[1024];
            while ((len = zipInputStream.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
        catch (IOException e){
            LogUtils.error("writeZipEntry err = %s",e);
        }
        finally {
            IoUtils.close(fos);
        }
    }

    /**
     * 解压apk所有文件
     */
    public static void extractAPK(String zipFilePath, String destDir)  {
        ZipInputStream zipInputStream = null;
        Map<String,Integer> zipEntryNameMap = new HashMap<>();
        try {
            zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath));
            ZipEntry zipEntry = null;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {

                String zipEntryName = zipEntry.getName();

                String lowerCase = zipEntryName.toLowerCase(Locale.US);
                String finalFileName = zipEntryName;
                if(zipEntryNameMap.get(lowerCase) != null){
                    finalFileName = zipEntryName + RENAME_SUFFIX + (zipEntryNameMap.get(lowerCase) + 1);
                    String finalFileNameLowerCase = finalFileName.toLowerCase(Locale.US);
                    zipEntryNameMap.put(finalFileNameLowerCase,0);
                    LogUtils.noisy("extract file rename: %s -> %s",zipEntryName,finalFileName);
                }
                else{
                    zipEntryNameMap.put(lowerCase,0);
                }
                writeZipEntry(zipInputStream,destDir + File.separator + finalFileName);

            }
        }
        catch (Exception e){
        }
        finally {
            IoUtils.close(zipInputStream);
        }
    }

    /**
     * 解压单个文件
     */
    public static void extractFile(String zipFilePath,String fileName, String destDir)  {
        ZipFile zipFile = new ZipFile(zipFilePath);
        try {
            FileHeader fileHeader = zipFile.getFileHeader(fileName);
            zipFile.extractFile(fileHeader,destDir);
        }
        catch (ZipException e){
            e.printStackTrace();
        }
    }


    /**
     * 正常压缩文件
     */
    public static void compress(List<File> files,String destFile)  {
        ZipFile zipFile = new ZipFile(destFile);
        if(files == null){
            return;
        }
        try {
            for(File f : files){
                if(f.isDirectory()){
                    zipFile.addFolder(f.getAbsoluteFile());
                }
                else{
                    zipFile.addFile(f.getAbsoluteFile());
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
