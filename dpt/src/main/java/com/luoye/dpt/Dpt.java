package com.luoye.dpt;

import com.luoye.dpt.task.BuildAndSignApkTask;
import com.luoye.dpt.util.*;
import java.io.File;

public class Dpt {

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

    private static void usage(){
        LogUtils.error("Usage:\n\tjava -jar dpt.jar [--log] <ApkFile>");
    }

    private static void processApk(String apkPath){
        if(!new File("shell-files").exists()) {
            LogUtils.error("Cannot find shell files!");
            return;
        }
        File apkFile = new File(apkPath);

        if(!apkFile.exists()){
            LogUtils.error("Apk not exists!");
            return;
        }
        String apkFileName = apkFile.getName();

        String currentDir = new File(".").getAbsolutePath();  // 当前命令行所在的目录
        if (currentDir.endsWith("/.")){
            currentDir = currentDir.substring(0, currentDir.lastIndexOf("/."));
        }
        String output = FileUtils.getNewFileName(apkFileName,"signed");
        LogUtils.info("output: " + output);


        File outputFile = new File(currentDir, output);
        String outputApkFileParentPath = outputFile.getParent();

        //apk文件解压的目录
        String apkMainProcessPath = ApkUtils.getWorkspaceDir().getAbsolutePath();

        LogUtils.info("Apk main process path: " + apkMainProcessPath);

        ApkUtils.extract(apkPath,apkMainProcessPath);
        Global.packageName = ManifestUtils.getPackageName(apkMainProcessPath + File.separator + "AndroidManifest.xml");
        ApkUtils.extractDexCode(apkMainProcessPath);

        ApkUtils.compressDexFiles(apkMainProcessPath);
        ApkUtils.deleteAllDexFiles(apkMainProcessPath);

        ApkUtils.saveApplicationName(apkMainProcessPath);
        ApkUtils.writeProxyAppName(apkMainProcessPath);
        ApkUtils.saveAppComponentFactory(apkMainProcessPath);
        ApkUtils.writeProxyComponentFactoryName(apkMainProcessPath);

        //ApkUtils.setDebuggable(apkMainProcessPath,true);

        ApkUtils.addProxyDex(apkMainProcessPath);

        ApkUtils.deleteMetaData(apkMainProcessPath);
        ApkUtils.copyShellLibs(apkMainProcessPath, new File(outputApkFileParentPath,"shell-files/libs"));

        new BuildAndSignApkTask(false, apkMainProcessPath, output).run();

        File apkMainProcessFile = new File(apkMainProcessPath);
        if (apkMainProcessFile.exists()) {
            FileUtils.deleteRecurse(apkMainProcessFile);
        }
        LogUtils.info("All done.");
    }
}
