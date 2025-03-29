package com.luoye.dpt.builder;

import com.android.apksigner.ApkSignerTool;
import com.iyxan23.zipalignjava.ZipAlign;
import com.luoye.dpt.Const;
import com.luoye.dpt.elf.ReadElf;
import com.luoye.dpt.model.Instruction;
import com.luoye.dpt.model.MultiDexCode;
import com.luoye.dpt.task.ThreadPool;
import com.luoye.dpt.util.DexUtils;
import com.luoye.dpt.util.FileUtils;
import com.luoye.dpt.util.HexUtils;
import com.luoye.dpt.util.IoUtils;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.util.ManifestUtils;
import com.luoye.dpt.util.MultiDexCodeUtils;
import com.luoye.dpt.util.RC4Utils;
import com.luoye.dpt.util.ZipUtils;
import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import java.nio.file.StandardCopyOption;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
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
    private List<String> excludedAbi;

    public static class Builder extends AndroidPackage.Builder {
    private boolean dumpCode;
    private List<String> excludedAbi = new ArrayList<>();
    @Override
    public Apk build() {
        return new Apk(this);
    }

    public Builder filePath(String path) {
        this.filePath = path;
        return this;
    }

    public Builder outputPath(String path) {
        this.outputPath = path;
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
    
    public Builder excludedAbi(List<String> excludedAbi) {
        this.excludedAbi = excludedAbi;
        return this;
    }
}

    protected Apk(Builder builder) {
    setFilePath(builder.filePath);
    setOutputPath(builder.outputPath);
    setDebuggable(builder.debuggable);
    setAppComponentFactory(builder.appComponentFactory);
    setSign(builder.sign);
    setPackageName(builder.packageName);
    setDumpCode(builder.dumpCode);
    setExcludedAbi(builder.excludedAbi);
    }

    public void setDumpCode(boolean dumpCode) {
        this.dumpCode = dumpCode;
    }

    public boolean isDumpCode() {
        return dumpCode;
    }
    
    public List<String> getExcludedAbi() {
        return excludedAbi;
    }

    public void setExcludedAbi(List<String> excludedAbi) {
        this.excludedAbi = excludedAbi;
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

        ZipUtils.unZip(apk.getFilePath(),apkMainProcessPath);
        String packageName = ManifestUtils.getPackageName(apkMainProcessPath + File.separator + "AndroidManifest.xml");
        apk.setPackageName(packageName);
        apk.extractDexCode(apkMainProcessPath);
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
        apk.addJunkCodeDex(apkMainProcessPath);
        apk.compressDexFiles(apkMainProcessPath);
        apk.deleteAllDexFiles(apkMainProcessPath);
        apk.combineDexZipWithShellDex(apkMainProcessPath);
        apk.copyNativeLibs(apkMainProcessPath);

        byte[] rc4key = RC4Utils.generateRC4Key();
        apk.encryptSoFiles(apkMainProcessPath,rc4key);

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
    /**
     * Combine the compressed dex file with the shell dex to create a new dex file.
     */
    private void combineDexZipWithShellDex(String apkMainProcessPath) {
        try {
            File shellDexFile = new File("shell-files/dex/classes.dex");
            File originalDexZipFile = new File(getOutAssetsDir(apkMainProcessPath).getAbsolutePath() + File.separator + "i11111i111.zip");
            byte[] zipData = com.android.dex.util.FileUtils.readFile(originalDexZipFile);// Read the zip file as binary data
            byte[] unShellDexArray =  com.android.dex.util.FileUtils.readFile(shellDexFile); // Read the dex file as binary data
            int zipDataLen = zipData.length;
            int unShellDexLen = unShellDexArray.length;
            LogUtils.info("zipDataLen: " + zipDataLen);
            LogUtils.info("unShellDexLen:" + unShellDexLen);
            int totalLen = zipDataLen + unShellDexLen + 4;// An additional 4 bytes are added to store the length
            byte[] newdex = new byte[totalLen]; // Allocate the new length

            // Add the shell code
            System.arraycopy(unShellDexArray, 0, newdex, 0, unShellDexLen);// First, copy the dex content
            // Add the unencrypted zip data
            System.arraycopy(zipData, 0, newdex, unShellDexLen, zipDataLen); // Then copy the APK content after the dex content
            // Add the length of the shell data
            System.arraycopy(FileUtils.intToByte(zipDataLen), 0, newdex, totalLen - 4, 4);// The last 4 bytes are for the length

            // Modify the DEX file size header
            FileUtils.fixFileSizeHeader(newdex);
            // Modify the DEX SHA1 header
            FileUtils.fixSHA1Header(newdex);
            // Modify the DEX CheckSum header
            FileUtils.fixCheckSumHeader(newdex);

            String str = apkMainProcessPath + File.separator+ "classes.dex";
            File file = new File(str);
            if (!file.exists()) {
                file.createNewFile();
            }

            // Output the new dex file
            FileOutputStream localFileOutputStream = new FileOutputStream(str);
            localFileOutputStream.write(newdex);
            localFileOutputStream.flush();
            localFileOutputStream.close();
            LogUtils.info("New Dex file generated: " + str);
            // Delete the dex zip package
            FileUtils.deleteRecurse(originalDexZipFile);
        }catch (Exception e){
            e.printStackTrace();
        }

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
        ZipUtils.compress(getDexFiles(apkDir),getOutAssetsDir(apkDir).getAbsolutePath() + File.separator + "i11111i111.zip");
    }

    private void copyNativeLibs(String apkDir) {
        File sourceDirRoot = new File(FileUtils.getExecutablePath(), "shell-files/libs");
        File destDirRoot = new File(getOutAssetsDir(apkDir).getAbsolutePath(), "vwwwwwvwww");
        
        if (!destDirRoot.exists()) {
            destDirRoot.mkdirs();
        }
        
        File[] abiDirs = sourceDirRoot.listFiles();
        if (abiDirs != null) {
            for (File abiDir : abiDirs) {
                if (abiDir.isDirectory()) {
                    String abiName = abiDir.getName();
                    
                    if (excludedAbi != null && excludedAbi.contains(abiName)) {
                        LogUtils.info("Skipping excluded ABI: " + abiName);
                        continue;
                    }
                    
                    File destAbiDir = new File(destDirRoot, abiName);
                    if (!destAbiDir.exists()) {
                        destAbiDir.mkdirs();
                    }
                    
                    File[] libFiles = abiDir.listFiles();
                    if (libFiles != null) {
                        for (File libFile : libFiles) {
                            if (libFile.isFile() && libFile.getName().endsWith(".so")) {
                                File destFile = new File(destAbiDir, libFile.getName());
                                try {
                                    Files.copy(libFile.toPath(), destFile.toPath(), 
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                } catch (IOException e) {
                                    LogUtils.error("Failed to copy library: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void encryptSoFiles(String apkDir, byte[] rc4Key){
        File obfDir = new File(getOutAssetsDir(apkDir).getAbsolutePath() + File.separator, "vwwwwwvwww");
        File[] soAbiDirs = obfDir.listFiles();
        if(soAbiDirs != null) {
            for (File soAbiDir : soAbiDirs) {
                File[] soFiles = soAbiDir.listFiles();
                if(soFiles != null) {
                    for (File soFile : soFiles) {
                        if(!soFile.getAbsolutePath().endsWith(".so")) {
                            continue;
                        }
                        encryptSoFile(soFile, rc4Key);
                        writeRC4Key(soFile, rc4Key);
                    }
                }
             }
        }

    }

    private void encryptSoFile(File soFile, byte[] rc4Key) {
        try {
            ReadElf readElf = new ReadElf(soFile);
            List<ReadElf.SectionHeader> sectionHeaders = readElf.getSectionHeaders();
            readElf.close();
            for (ReadElf.SectionHeader sectionHeader : sectionHeaders) {
                if(".bitcode".equals(sectionHeader.getName())) {
                    LogUtils.info("start encrypt %s section: %s, offset: %s, size: %s",
                            soFile.getAbsolutePath(),
                            sectionHeader.getName(),
                            HexUtils.toHexString(sectionHeader.getOffset()),
                            sectionHeader.getSize()
                    );

                    byte[] bitcode = IoUtils.readFile(soFile.getAbsolutePath(),
                            sectionHeader.getOffset(),
                            (int)sectionHeader.getSize()
                    );

                    byte[] enc = RC4Utils.crypt(rc4Key, bitcode);
                    IoUtils.writeFile(soFile.getAbsolutePath(),enc,sectionHeader.getOffset());
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeRC4Key(File soFile, byte[] rc4key) {
        try {
            ReadElf readElf = new ReadElf(soFile);
            ReadElf.Symbol symbol = readElf.getDynamicSymbol(Const.RC4_KEY_SYMBOL);
            if(symbol == null) {
                LogUtils.warn("cannot find symbol in %s, no need write key", soFile.getName());
                return;
            }
            else {
                LogUtils.info("find symbol(%s) in %s", HexUtils.toHexString(symbol.value), soFile.getName());
            }
            long value = symbol.value;
            int shndx = symbol.shndx;
            List<ReadElf.SectionHeader> sectionHeaders = readElf.getSectionHeaders();
            ReadElf.SectionHeader sectionHeader = sectionHeaders.get(shndx);
            long symbolDataOffset = sectionHeader.getOffset() + value - sectionHeader.getAddr();
            LogUtils.info("write symbol data to %s(%s)", soFile.getName(), HexUtils.toHexString(symbolDataOffset));

            readElf.close();
            IoUtils.writeFile(soFile.getAbsolutePath(),rc4key,symbolDataOffset);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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

    private void  extractDexCode(String apkOutDir){
        List<File> dexFiles = getDexFiles(apkOutDir);
        Map<Integer,List<Instruction>> instructionMap = new HashMap<>();
        String appNameNew = "OoooooOooo";
        String dataOutputPath = getOutAssetsDir(apkOutDir).getAbsolutePath() + File.separator + appNameNew;

        CountDownLatch countDownLatch = new CountDownLatch(dexFiles.size());
        for(File dexFile : dexFiles) {
            ThreadPool.getInstance().execute(() -> {
                final int dexNo = DexUtils.getDexNumber(dexFile.getName());
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

    private void buildApk(String originApkPath, String unpackFilePath, String savePath) {
        String outputPath = getOutputPath();
        File outputPathFile = null;
        String outputDir;
        String resultFileName = null;

        if (outputPath != null) {
            outputPathFile = new File(outputPath);
            if (outputPath.endsWith(".apk")) {
                outputDir = outputPathFile.getParent();
                resultFileName = outputPathFile.getName();
            } else {
                outputDir = outputPath;
            }
        } else {
            outputDir = savePath;
        }

        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }

        String originApkName = new File(originApkPath).getName();
        String apkLastProcessDir = getLastProcessDir().getAbsolutePath();

        String unzipalignApkPath = outputDir + File.separator +
            (resultFileName != null ? "temp_" + resultFileName : getUnzipalignApkName(originApkName));
        ZipUtils.zip(unpackFilePath, unzipalignApkPath);

        String keyStoreFilePath = apkLastProcessDir + File.separator + "dpt.jks";
        try {
            ZipUtils.readResourceFromRuntime("assets/dpt.jks", keyStoreFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String unsignedApkPath = outputDir + File.separator +
            (resultFileName != null ? "unsigned_" + resultFileName : getUnsignApkName(originApkName));

        boolean zipalignSuccess = false;
        try {
            zipalignApk(unzipalignApkPath, unsignedApkPath);
            zipalignSuccess = true;
            LogUtils.info("zipalign success.");
        } catch (Exception e) {
            LogUtils.error("zipalign failed");
        }

        String willSignApkPath = zipalignSuccess ? unsignedApkPath : unzipalignApkPath;
        String signedApkPath = outputDir + File.separator +
            (resultFileName != null ? resultFileName : getSignedApkName(originApkName));

        boolean signResult = false;
        if (isSign()) {
            signResult = signApkDebug(willSignApkPath, keyStoreFilePath, signedApkPath);
        } else if (resultFileName != null) {
            try {
                Files.copy(Paths.get(willSignApkPath), Paths.get(signedApkPath),
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }

        File willSignApkFile = new File(willSignApkPath);
        File signedApkFile = new File(signedApkPath);
        File idsigFile = new File(signedApkPath + ".idsig");
        File keyStoreFile = new File(keyStoreFilePath);

        LogUtils.info("willSignApkFile: %s ,exists: %s", willSignApkFile.getAbsolutePath(), willSignApkFile.exists());
        LogUtils.info("signedApkFile: %s ,exists: %s", signedApkFile.getAbsolutePath(), signedApkFile.exists());

        String resultPath = signedApkFile.exists() ? signedApkFile.getAbsolutePath() : willSignApkFile.getAbsolutePath();

        if (signedApkFile.exists() && willSignApkFile.exists()) {
            willSignApkFile.delete();
        }

        if (zipalignSuccess) {
            try {
                Files.deleteIfExists(Paths.get(unzipalignApkPath));
            } catch (Exception e) {
                LogUtils.debug("unzipalignApkPath err = %s", e);
            }
        }

        if (idsigFile.exists()) idsigFile.delete();
        if (keyStoreFile.exists()) keyStoreFile.delete();

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