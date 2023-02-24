package com.luoye.dpt.task;

import com.android.apksigner.ApkSignerTool;
import com.iyxan23.zipalignjava.ZipAlign;
import com.luoye.dpt.util.ApkUtils;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.util.WindFileUtils;
import java.io.File;
import java.util.ArrayList;
import java.io.RandomAccessFile;
import java.io.FileOutputStream;

/**
 * Created by Wind
 */
public class BuildAndSignApkTask{

    private boolean keepUnsignedApkFile;

    private String signedApkPath;

    private String unzipApkFilePath;

    public BuildAndSignApkTask(boolean keepUnsignedApkFile, String unzipApkFilePath, String signedApkPath) {
        this.keepUnsignedApkFile = keepUnsignedApkFile;
        this.unzipApkFilePath = unzipApkFilePath;
        this.signedApkPath = signedApkPath;
    }

    public void run() {
        String apkLastProcessDir = ApkUtils.getLastProcessDir().getAbsolutePath();
        //将文件压缩到当前apk文件的上一级目录上
        String unsignedApkPath = apkLastProcessDir + File.separator + "unsigned.apk";
        WindFileUtils.compressToZip(unzipApkFilePath, unsignedApkPath);

        //将签名文件复制从assets目录下复制出来
        String keyStoreFilePath = apkLastProcessDir + File.separator + "keystore";

        File keyStoreFile = new File(keyStoreFilePath);
        // assets/keystore分隔符不能使用File.separator，否则在windows上抛出IOException !!!
        String keyStoreAssetPath = "assets/keystore";

        WindFileUtils.copyFileFromJar(keyStoreAssetPath, keyStoreFilePath);

        String unsignedZipAlignedApkPath = apkLastProcessDir + File.separator + "unsigned_zipaligned.apk";
        try {
            zipalignApk(unsignedApkPath, unsignedZipAlignedApkPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String apkPath = unsignedZipAlignedApkPath;
        if (!(new File(apkPath).exists())) {
            apkPath = unsignedApkPath;
            LogUtils.info("zipalign apk failed, just sign not zipaligned apk !!!");
        }

        boolean signResult = signApk(apkPath, keyStoreFilePath, signedApkPath);

        File unsignedApkFile = new File(unsignedApkPath);
        File signedApkFile = new File(signedApkPath);
        // delete unsigned apk file
        if (!keepUnsignedApkFile && unsignedApkFile.exists() && signedApkFile.exists() && signResult) {
            unsignedApkFile.delete();
        }

        File unsign_zipaligned_file = new File(unsignedZipAlignedApkPath);
        if (!keepUnsignedApkFile && unsign_zipaligned_file.exists() && signedApkFile.exists() && signResult) {
            unsign_zipaligned_file.delete();
        }

        File idsigFile = new File(signedApkPath + ".idsig");
        if (idsigFile.exists()) {
            idsigFile.delete();
        }

        // delete the keystore file
        if (keyStoreFile.exists()) {
            keyStoreFile.delete();
        }
        LogUtils.info("signResult: " + signResult + ",output: " + signedApkPath + "\n");
    }

    private boolean signApk(String apkPath, String keyStorePath, String signedApkPath) {
        if (signApkUsingAndroidApksigner(apkPath, keyStorePath, signedApkPath, "123456")) {
            return true;
        }
        return false;
    }

    /**
     * 使用Android build-tools里自带的apksigner工具进行签名
     */
    private boolean signApkUsingAndroidApksigner(String apkPath, String keyStorePath, String signedApkPath, String keyStorePassword) {
        ArrayList<String> commandList = new ArrayList<>();

        commandList.add("sign");
        commandList.add("--ks");
        commandList.add(keyStorePath);
        commandList.add("--ks-key-alias");
        commandList.add("key0");
        commandList.add("--ks-pass");
        commandList.add("pass:" + keyStorePassword);
        commandList.add("--key-pass");
        commandList.add("pass:" + keyStorePassword);
        commandList.add("--out");
        commandList.add(signedApkPath);
        commandList.add("--v1-signing-enabled");
        commandList.add("true");
        commandList.add("--v2-signing-enabled");   // v2签名不兼容android 6
        commandList.add("true");
        commandList.add("--v3-signing-enabled");   // v3签名不兼容android 6
        commandList.add("true");
        commandList.add(apkPath);

        int size = commandList.size();
        String[] commandArray = new String[size];
        commandArray = commandList.toArray(commandArray);

        try {
            ApkSignerTool.main(commandArray);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void zipalignApk(String inputApkPath, String outputApkPath) throws Exception{
        RandomAccessFile in = new RandomAccessFile(inputApkPath, "r");
        FileOutputStream out = new FileOutputStream(outputApkPath);
        ZipAlign.alignZip(in, out);
    }

}
