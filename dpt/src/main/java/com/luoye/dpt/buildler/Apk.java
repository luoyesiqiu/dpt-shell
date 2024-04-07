package com.luoye.dpt.buildler;

import com.android.apksigner.ApkSignerTool;
import com.iyxan23.zipalignjava.ZipAlign;
import com.luoye.dpt.Const;
import com.luoye.dpt.model.Instruction;
import com.luoye.dpt.model.MultiDexCode;
import com.luoye.dpt.task.ThreadPool;
import com.luoye.dpt.util.DexUtils;
import com.luoye.dpt.util.FileUtils;
import com.luoye.dpt.util.IoUtils;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.util.ManifestUtils;
import com.luoye.dpt.util.MultiDexCodeUtils;
import com.luoye.dpt.util.ZipUtils;
import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Apk extends AndroidPackage {

    private boolean dumpCode;

    public static class Builder extends AndroidPackage.Builder {
        private boolean dumpCode;
        @Override
        public Apk build() {
            return new Apk(this);
        }

        public Builder filePath(String path) {
            this.filePath = path;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder debuggable(boolean debuggable) {
            this.debuggable = debuggable;
            return this;
        }

        public Builder sign(boolean sign) {
            this.sign = sign;
            return this;
        }

        public Builder dumpCode(boolean dumpCode) {
            this.dumpCode = dumpCode;
            return this;
        }

        public Builder appComponentFactory(boolean appComponentFactory) {
            this.appComponentFactory = appComponentFactory;
            return this;
        }
    }

    protected Apk(Builder builder) {
        setFilePath(builder.filePath);
        setDebuggable(builder.debuggable);
        setAppComponentFactory(builder.appComponentFactory);
        setSign(builder.sign);
        setPackageName(builder.packageName);
        setDumpCode(builder.dumpCode);
    }

    public void setDumpCode(boolean dumpCode) {
        this.dumpCode = dumpCode;
    }

    public boolean isDumpCode() {
        return dumpCode;
    }

    private static void process(Apk apk){
        if(!new File("shell-files").exists()) {
            LogUtils.error("Cannot find shell files!");
            return;
        }
        File apkFile = new File(apk.getFilePath());

        if(!apkFile.exists()){
            LogUtils.error("Apk not exists!");
            return;
        }

        //apk extract path
        String apkMainProcessPath = apk.getWorkspaceDir().getAbsolutePath();

        LogUtils.info("Apk main process path: " + apkMainProcessPath);

        ZipUtils.extractAPK(apk.getFilePath(),apkMainProcessPath);
        String packageName = ManifestUtils.getPackageName(apkMainProcessPath + File.separator + "AndroidManifest.xml");
        apk.setPackageName(packageName);
        apk.extractDexCode(apkMainProcessPath);

        apk.addJunkCodeDex(apkMainProcessPath);
        apk.compressDexFiles(apkMainProcessPath);
        apk.deleteAllDexFiles(apkMainProcessPath);

        apk.saveApplicationName(apkMainProcessPath);
        apk.writeProxyAppName(apkMainProcessPath);
        if(apk.isAppComponentFactory()){
            apk.saveAppComponentFactory(apkMainProcessPath);
            apk.writeProxyComponentFactoryName(apkMainProcessPath);
        }
        if(apk.isDebuggable()) {
            LogUtils.info("Make apk debuggable.");
            apk.setDebuggable(apkMainProcessPath, true);
        }

        apk.setExtractNativeLibs(apkMainProcessPath);

        apk.addProxyDex(apkMainProcessPath);
        apk.copyNativeLibs(apkMainProcessPath);

        apk.buildApk(apkFile.getAbsolutePath(),apkMainProcessPath, FileUtils.getExecutablePath());

        File apkMainProcessFile = new File(apkMainProcessPath);
        if (apkMainProcessFile.exists()) {
            FileUtils.deleteRecurse(apkMainProcessFile);
        }
        LogUtils.info("All done.");
    }
    public void protect() {
        process(this);
    }

    private String getUnsignApkName(String apkName){
        return FileUtils.getNewFileName(apkName,"unsign");
    }

    private String getUnzipalignApkName(String apkName){
        return FileUtils.getNewFileName(apkName,"unzipalign");
    }

    private String getSignedApkName(String apkName){
        return FileUtils.getNewFileName(apkName,"signed");
    }

    /**
     * Write proxy ApplicationName
     */
    private void writeProxyAppName(String filePath){
        String inManifestPath = filePath + File.separator + "AndroidManifest.xml";
        String outManifestPath = filePath + File.separator + "AndroidManifest_new.xml";
        ManifestUtils.writeApplicationName(inManifestPath,outManifestPath, Const.PROXY_APPLICATION_NAME);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    private void writeProxyComponentFactoryName(String filePath){
        String inManifestPath = filePath + File.separator + "AndroidManifest.xml";
        String outManifestPath = filePath + File.separator + "AndroidManifest_new.xml";
        ManifestUtils.writeAppComponentFactory(inManifestPath,outManifestPath, Const.PROXY_COMPONENT_FACTORY);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    private void setExtractNativeLibs(String filePath){

        String inManifestPath = filePath + File.separator + "AndroidManifest.xml";
        String outManifestPath = filePath + File.separator + "AndroidManifest_new.xml";
        ModificationProperty property = new ModificationProperty();

        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.EXTRACTNATIVELIBS, "true"));

        FileProcesser.processManifestFile(inManifestPath, outManifestPath, property);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    private void setDebuggable(String filePath,boolean debuggable){
        String inManifestPath = filePath + File.separator + "AndroidManifest.xml";
        String outManifestPath = filePath + File.separator + "AndroidManifest_new.xml";
        ManifestUtils.writeDebuggable(inManifestPath,outManifestPath, debuggable ? "true" : "false");

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    private File getWorkspaceDir(){
        return FileUtils.getDir(Const.ROOT_OF_OUT_DIR,"dptOut");
    }

    /**
     * Get last process（zipalign，sign）dir
     */
    private File getLastProcessDir(){
        return FileUtils.getDir(Const.ROOT_OF_OUT_DIR,"dptLastProcess");
    }

    private File getOutAssetsDir(String filePath){
        return FileUtils.getDir(filePath,"assets");
    }

    private void addProxyDex(String apkDir){
        String proxyDexPath = "shell-files/dex/classes.dex";
        addDex(proxyDexPath,apkDir);
    }

    protected void addJunkCodeDex(String apkDir) {
        String junkCodeDexPath = "shell-files/dex/junkcode.dex";
        addDex(junkCodeDexPath,apkDir);
    }

    private void compressDexFiles(String apkDir){
        ZipUtils.compress(getDexFiles(apkDir),getOutAssetsDir(apkDir).getAbsolutePath()+File.separator + "i11111i111");
    }

    private void copyNativeLibs(String apkDir){
        File file = new File(FileUtils.getExecutablePath(), "shell-files/libs");
        FileUtils.copy(file.getAbsolutePath(),getOutAssetsDir(apkDir).getAbsolutePath() + File.separator + "vwwwwwvwww");
    }

    private void deleteAllDexFiles(String dir){
        List<File> dexFiles = getDexFiles(dir);
        for (File dexFile : dexFiles) {
            dexFile.delete();
        }
    }

    private void addDex(String dexFilePath,String apkDir){
        File dexFile = new File(dexFilePath);
        List<File> dexFiles = getDexFiles(apkDir);
        int newDexNameNumber = dexFiles.size() + 1;
        String newDexPath = apkDir + File.separator + "classes.dex";
        if(newDexNameNumber > 1) {
            newDexPath = apkDir + File.separator + String.format(Locale.US, "classes%d.dex", newDexNameNumber);
        }
        byte[] dexData = IoUtils.readFile(dexFile.getAbsolutePath());
        IoUtils.writeFile(newDexPath,dexData);
    }

    private static String getManifestFilePath(String apkOutDir){
        return apkOutDir + File.separator + "AndroidManifest.xml";
    }

    private void saveApplicationName(String apkOutDir){
        String androidManifestFile = getManifestFilePath(apkOutDir);
        File appNameOutFile = new File(getOutAssetsDir(apkOutDir),"app_name");
        String appName = ManifestUtils.getApplicationName(androidManifestFile);

        appName = appName == null ? "" : appName;

        IoUtils.writeFile(appNameOutFile.getAbsolutePath(),appName.getBytes());
    }

    private void saveAppComponentFactory(String apkOutDir){
        String androidManifestFile = getManifestFilePath(apkOutDir);
        File appNameOutFile = new File(getOutAssetsDir(apkOutDir),"app_acf");
        String appName = ManifestUtils.getAppComponentFactory(androidManifestFile);

        appName = appName == null ? "" : appName;

        IoUtils.writeFile(appNameOutFile.getAbsolutePath(),appName.getBytes());
    }

    private boolean isSystemComponentFactory(String name){
        if(name.equals("androidx.core.app.CoreComponentFactory") || name.equals("android.support.v4.app.CoreComponentFactory")){
            return true;
        }
        return false;
    }

    /**
     * Get dex file number
     * ex：classes2.dex return 1
     */
    private int getDexNumber(String dexName){
        Pattern pattern = Pattern.compile("classes(\\d*)\\.dex$");
        Matcher matcher = pattern.matcher(dexName);
        if(matcher.find()){
            String dexNo = matcher.group(1);
            return (dexNo == null || "".equals(dexNo)) ? 0 : Integer.parseInt(dexNo) - 1;
        }
        else{
            return  -1;
        }
    }

    private void  extractDexCode(String apkOutDir){
        List<File> dexFiles = getDexFiles(apkOutDir);
        Map<Integer,List<Instruction>> instructionMap = new HashMap<>();
        String appNameNew = "OoooooOooo";
        String dataOutputPath = getOutAssetsDir(apkOutDir).getAbsolutePath() + File.separator + appNameNew;

        CountDownLatch countDownLatch = new CountDownLatch(dexFiles.size());
        for(File dexFile : dexFiles) {
            ThreadPool.getInstance().execute(() -> {
                final int dexNo = getDexNumber(dexFile.getName());
                if(dexNo < 0){
                    return;
                }
                String extractedDexName = dexFile.getName().endsWith(".dex") ? dexFile.getName().replaceAll("\\.dex$", "_extracted.dat") : "_extracted.dat";
                File extractedDexFile = new File(dexFile.getParent(), extractedDexName);

                List<Instruction> ret = DexUtils.extractAllMethods(dexFile, extractedDexFile, getPackageName(), isDumpCode());
                instructionMap.put(dexNo,ret);

                File dexFileRightHashes = new File(dexFile.getParent(),FileUtils.getNewFileSuffix(dexFile.getName(),"dat"));
                DexUtils.writeHashes(extractedDexFile,dexFileRightHashes);
                dexFile.delete();
                extractedDexFile.delete();
                dexFileRightHashes.renameTo(dexFile);
                countDownLatch.countDown();
            });

        }

        ThreadPool.getInstance().shutdown();

        try {
            countDownLatch.await();
        }
        catch (Exception ignored){
        }

        MultiDexCode multiDexCode = MultiDexCodeUtils.makeMultiDexCode(instructionMap);

        MultiDexCodeUtils.writeMultiDexCode(dataOutputPath,multiDexCode);

    }
    /**
     * Get all dex files
     */
    private List<File> getDexFiles(String dir){
        List<File> dexFiles = new ArrayList<>();
        File dirFile = new File(dir);
        File[] files = dirFile.listFiles();
        if(files != null) {
            Arrays.stream(files).filter(it -> it.getName().endsWith(".dex")).forEach(dexFiles::add);
        }
        return dexFiles;
    }

    private void buildApk(String originApkPath,String unpackFilePath,String savePath) {

        String originApkName = new File(originApkPath).getName();
        String apkLastProcessDir = getLastProcessDir().getAbsolutePath();

        String unzipalignApkPath = savePath + File.separator + getUnzipalignApkName(originApkName);
        ZipUtils.compressToApk(unpackFilePath, unzipalignApkPath);

        String keyStoreFilePath = apkLastProcessDir + File.separator + "debug.keystore";

        String keyStoreAssetPath = "assets/debug.keystore";

        try {
            ZipUtils.readResourceFromRuntime(keyStoreAssetPath, keyStoreFilePath);
        }
        catch (IOException e){
            e.printStackTrace();
        }

        String unsignedApkPath = savePath + File.separator + getUnsignApkName(originApkName);
        boolean zipalignSuccess = false;
        try {
            zipalignApk(unzipalignApkPath, unsignedApkPath);
            zipalignSuccess = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        String willSignApkPath = null;
        if (zipalignSuccess) {
            LogUtils.info("zipalign success.");
            willSignApkPath = unsignedApkPath;

        }
        else{
            LogUtils.error("warning: zipalign failed!");
            willSignApkPath = unzipalignApkPath;
        }

        boolean signResult = false;

        String signedApkPath = savePath + File.separator + getSignedApkName(originApkName);

        if(isSign()) {
            signResult = signApkDebug(willSignApkPath, keyStoreFilePath, signedApkPath);
        }

        File willSignApkFile = new File(willSignApkPath);
        File signedApkFile = new File(signedApkPath);
        File keyStoreFile = new File(keyStoreFilePath);
        File idsigFile = new File(signedApkPath + ".idsig");


        LogUtils.info("willSignApkFile: %s ,exists: %s",willSignApkFile.getAbsolutePath(),willSignApkFile.exists());
        LogUtils.info("signedApkFile: %s ,exists: %s",signedApkFile.getAbsolutePath(),signedApkFile.exists());

        String resultPath = signedApkFile.getAbsolutePath();
        if (!signedApkFile.exists() || !signResult) {
            resultPath = willSignApkFile.getAbsolutePath();
        }
        else{
            if(willSignApkFile.exists()){
                willSignApkFile.delete();
            }
        }

        if(zipalignSuccess) {
            File unzipalignApkFile = new File(unzipalignApkPath);
            try {
                Path filePath = Paths.get(unzipalignApkFile.getAbsolutePath());
                Files.deleteIfExists(filePath);
            }catch (Exception e){
                LogUtils.debug("unzipalignApkPath err = %s", e);
            }
        }

        if (idsigFile.exists()) {
            idsigFile.delete();
        }

        if (keyStoreFile.exists()) {
            keyStoreFile.delete();
        }
        LogUtils.info("protected apk output path: " + resultPath + "\n");
    }

    private static boolean signApkDebug(String apkPath, String keyStorePath, String signedApkPath) {
        if (signApk(apkPath, keyStorePath, signedApkPath,
                Const.KEY_ALIAS,
                Const.STORE_PASSWORD,
                Const.KEY_PASSWORD)) {
            return true;
        }
        return false;
    }

    private static boolean signApk(String apkPath, String keyStorePath, String signedApkPath,
                                   String keyAlias,
                                   String storePassword,
                                   String KeyPassword) {
        ArrayList<String> commandList = new ArrayList<>();

        commandList.add("sign");
        commandList.add("--ks");
        commandList.add(keyStorePath);
        commandList.add("--ks-key-alias");
        commandList.add(keyAlias);
        commandList.add("--ks-pass");
        commandList.add("pass:" + storePassword);
        commandList.add("--key-pass");
        commandList.add("pass:" + KeyPassword);
        commandList.add("--out");
        commandList.add(signedApkPath);
        commandList.add("--v1-signing-enabled");
        commandList.add("true");
        commandList.add("--v2-signing-enabled");
        commandList.add("true");
        commandList.add("--v3-signing-enabled");
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

    private static void zipalignApk(String inputApkPath, String outputApkPath) throws Exception{
        RandomAccessFile in = new RandomAccessFile(inputApkPath, "r");
        FileOutputStream out = new FileOutputStream(outputApkPath);
        ZipAlign.alignZip(in, out);
        IoUtils.close(in);
        IoUtils.close(out);
    }
}
