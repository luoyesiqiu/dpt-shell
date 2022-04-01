package com.luoye.dpt;

import com.luoye.dpt.task.BuildAndSignApkTask;
import com.luoye.dpt.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Dpt {
    private static final Logger logger = LoggerFactory.getLogger(Dpt.class.getSimpleName());
    private static final String UNZIP_APK_FILE_NAME = "apk-unzip-files";

    public static void main(String[] args) {
        if(args.length < 1){
            usage();
            return;
        }
        try {
            processApk(args[0]);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static String currentTimeStr() {
        @SuppressWarnings("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");//设置日期格式
        return df.format(new Date());
    }

    private static void usage(){
        System.err.println("Usage:\n\tjava -jar dpt.jar [--log] <ApkFile>");
    }

    private static void processApk(String apkPath){
        File apkFile = new File(apkPath);
        String apkFileName = apkFile.getName();


        String currentDir = new File(".").getAbsolutePath();  // 当前命令行所在的目录
        if (currentDir.endsWith("/.")){
            currentDir = currentDir.substring(0, currentDir.lastIndexOf("/."));
        }
        String output = FileUtils.getNewFileName(apkFileName,"signed");
        System.err.println("output: " + output);


        File outputFile = new File(currentDir, output);
        String outputApkFileParentPath = outputFile.getParent();
        System.err.println("outputApkFileParentPath: " + outputApkFileParentPath);

        // 中间文件临时存储的位置
        String tempFilePath = outputApkFileParentPath + File.separator +
                currentTimeStr() + "-tmp" + File.separator;

        // apk文件解压的目录
        String unzipApkFilePath = tempFilePath + apkFileName + "-" + UNZIP_APK_FILE_NAME;

        System.err.println("unzipApkFilePath: " + unzipApkFilePath);

        ApkUtils.extract(apkPath,unzipApkFilePath);
        Global.packageName = ManifestUtils.getPackageName(unzipApkFilePath + File.separator + "AndroidManifest.xml");
        ApkUtils.extractDexCode(unzipApkFilePath);

        ApkUtils.saveApplicationName(unzipApkFilePath);
        ApkUtils.writeProxyAppName(unzipApkFilePath);
        ApkUtils.saveAppComponentFactory(unzipApkFilePath);
        ApkUtils.writeProxyComponentFactoryName(unzipApkFilePath);

        ApkUtils.setExtractNativeLibs(unzipApkFilePath);
        ApkUtils.addProxyDex(unzipApkFilePath);

        ApkUtils.deleteMetaData(unzipApkFilePath);
        ApkUtils.copyShellLibs(unzipApkFilePath, new File(outputApkFileParentPath,"shell-files/libs"));

        new BuildAndSignApkTask(false, unzipApkFilePath, output, apkPath).run();

        File unzipApkFile = new File(unzipApkFilePath);
        if (unzipApkFile.exists()) {
            WindFileUtils.deleteDir(unzipApkFile);
        }

        File tempFile = new File(tempFilePath.replaceAll("\\.",""));
        if (tempFile.exists()) {
            FileUtils.deleteRecurse(tempFile);
        }
    }
}
