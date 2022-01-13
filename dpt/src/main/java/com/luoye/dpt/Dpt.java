package com.luoye.dpt;

import com.luoye.dpt.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

public class Dpt {
    private static final Logger logger = LoggerFactory.getLogger(Dpt.class.getSimpleName());
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
        System.err.println("Usage:\n\tjava -jar dpt.jar [--log] <ApkFile>");
    }

    private static void processApk(String apkPath){
        ApkUtils.deleteOutDir();
        ApkUtils.extract(apkPath,ApkUtils.getOutDir().getAbsolutePath());
        Global.packageName = ManifestUtils.getPackageName(ApkUtils.getOutDir() + File.separator + "AndroidManifest.xml");
        ApkUtils.extractDexCode(ApkUtils.getOutDir().getAbsolutePath());
        ApkUtils.saveApplicationName(ApkUtils.getOutDir().getAbsolutePath());
        ApkUtils.writeProxyAppName();
        boolean needWrite = ApkUtils.saveAppComponentFactory(ApkUtils.getOutDir().getAbsolutePath());
        if(needWrite) {
            ApkUtils.writeProxyComponentFactoryName();
        }
        ApkUtils.setExtractNativeLibs();
        ApkUtils.addProxyDex(ApkUtils.getOutDir().getAbsolutePath());

        ApkUtils.deleteMetaData();
        ApkUtils.copyShellLibs(new File("shell/libs"));
        File apkFile = new File(apkPath);
        File newApkFile = new File(apkFile.getParent() , ApkUtils.getNewApkName(apkFile.getName()));
        ApkUtils.compress(ApkUtils.getOutDir().getAbsolutePath(),newApkFile.getAbsolutePath());
    }
}
