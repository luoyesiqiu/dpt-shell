package com.luoye.dpt.builder;

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
import com.luoye.dpt.util.MultiDexCodeUtils;
import com.luoye.dpt.util.RC4Utils;
import com.luoye.dpt.util.ZipUtils;

import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AndroidPackage {

    public static abstract class Builder {
        public String filePath = null;
        public String packageName = null;
        public boolean debuggable = false;
        public boolean sign = true;
        public boolean appComponentFactory = true;
        public boolean dumpCode = false;

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

        public abstract AndroidPackage build();
    }
    public String filePath = null;
    public String packageName = null;
    public boolean debuggable = false;
    public boolean sign = true;
    public boolean appComponentFactory = true;
    private boolean dumpCode;

    public AndroidPackage(Builder builder) {
        setFilePath(builder.filePath);
        setDebuggable(builder.debuggable);
        setAppComponentFactory(builder.appComponentFactory);
        setSign(builder.sign);
        setPackageName(builder.packageName);
        setDumpCode(builder.dumpCode);
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    public boolean isSign() {
        return sign;
    }

    public void setSign(boolean sign) {
        this.sign = sign;
    }

    public boolean isAppComponentFactory() {
        return appComponentFactory;
    }

    public void setAppComponentFactory(boolean appComponentFactory) {
        this.appComponentFactory = appComponentFactory;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setDumpCode(boolean dumpCode) {
        this.dumpCode = dumpCode;
    }

    public boolean isDumpCode() {
        return dumpCode;
    }

    /**
     * Combine the compressed dex file with the shell dex to create a new dex file.
     */
    protected void combineDexZipWithShellDex(String packageMainProcessPath) {
        try {
            File shellDexFile = new File("shell-files/dex/classes.dex");
            File originalDexZipFile = new File(getOutAssetsDir(packageMainProcessPath).getAbsolutePath() + File.separator + "i11111i111.zip");
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

            String str = getDexDir(packageMainProcessPath) + File.separator + "classes.dex";
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

    private String getUnsignApkName(String packageFileName){
        return FileUtils.getNewFileName(packageFileName,"unsign");
    }

    private String getUnzipalignApkName(String packageFileName){
        return FileUtils.getNewFileName(packageFileName,"unzipalign");
    }

    private String getSignedApkName(String packageFileName){
        return FileUtils.getNewFileName(packageFileName,"signed");
    }

    /**
     * Write proxy ApplicationName
     */
    public abstract void writeProxyAppName(String manifestDir);

    public abstract void writeProxyComponentFactoryName(String manifestDir);

    public abstract void setExtractNativeLibs(String manifestDir);

    public abstract void setDebuggable(String manifestDir,boolean debuggable);

    public File getWorkspaceDir() {
        return FileUtils.getDir(Const.ROOT_OF_OUT_DIR,"dptOut");
    }

    /**
     * Get last process（zipalign，sign）dir
     */
    public File getLastProcessDir() {
        return FileUtils.getDir(Const.ROOT_OF_OUT_DIR,"dptLastProcess");
    }

    protected abstract File getOutAssetsDir(String packageDir);

    public abstract String getLibDir(String packageDir);

    public abstract String getDexDir(String packageDir);

    private void addProxyDex(String packageOutDir) {
        String proxyDexPath = "shell-files/dex/classes.dex";
        addDex(proxyDexPath,packageOutDir);
    }

    protected void addJunkCodeDex(String packageDir) {
        String junkCodeDexPath = "shell-files/dex/junkcode.dex";
        addDex(junkCodeDexPath, getDexDir(packageDir));
    }

    public abstract void compressDexFiles(String packageDir);


    public void copyNativeLibs(String packageDir) {
        try {
            File src = new File(FileUtils.getExecutablePath(), "shell-files/libs");
            //FileUtils.copy(file.getAbsolutePath(),getOutAssetsDir(packageDir).getAbsolutePath() + File.separator + "vwwwwwvwww", false);
            File dest = new File(getOutAssetsDir(packageDir).getAbsolutePath(), "vwwwwwvwww");
            org.apache.commons.io.FileUtils.copyDirectory(src, dest);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void encryptSoFiles(String packageOutDir, byte[] rc4Key){
        File obfDir = new File(getOutAssetsDir(packageOutDir).getAbsolutePath() + File.separator, "vwwwwwvwww");
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
                        writeSoFileCryptKey(soFile, rc4Key);
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

    private void writeSoFileCryptKey(File soFile, byte[] rc4key) {
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

    public void deleteAllDexFiles(String packageDir){
        List<File> dexFiles = getDexFiles(getDexDir(packageDir));
        for (File dexFile : dexFiles) {
            dexFile.delete();
        }
    }

    private void addDex(String dexFilePath,String dexFilesSavePath){
        File dexFile = new File(dexFilePath);
        List<File> dexFiles = getDexFiles(dexFilesSavePath);
        int newDexNameNumber = dexFiles.size() + 1;
        String newDexPath = dexFilesSavePath + File.separator + "classes.dex";
        if(newDexNameNumber > 1) {
            newDexPath = dexFilesSavePath + File.separator + String.format(Locale.US, "classes%d.dex", newDexNameNumber);
        }
        byte[] dexData = IoUtils.readFile(dexFile.getAbsolutePath());
        IoUtils.writeFile(newDexPath,dexData);
    }

    protected abstract String getManifestFilePath(String packageOutDir);

    public abstract void saveApplicationName(String packageOutDir);

    public abstract void saveAppComponentFactory(String packageOutDir);

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
            return (dexNo == null || dexNo.isEmpty()) ? 0 : Integer.parseInt(dexNo) - 1;
        }
        else{
            return  -1;
        }
    }

    public void extractDexCode(String packageDir, String dexCodeSavePath) {
        List<File> dexFiles = getDexFiles(packageDir);
        Map<Integer,List<Instruction>> instructionMap = new HashMap<>();
        String appNameNew = "OoooooOooo";
        String dataOutputPath = dexCodeSavePath + File.separator + appNameNew;

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
    public List<File> getDexFiles(String dir) {
        List<File> dexFiles = new ArrayList<>();
        File dirFile = new File(dir);
        File[] files = dirFile.listFiles();
        if(files != null) {
            Arrays.stream(files).filter(it -> it.getName().endsWith(".dex")).forEach(dexFiles::add);
        }
        return dexFiles;
    }

    protected void buildPackage(String originPackagePath, String unpackFilePath, String savePath) {

        String originApkName = new File(originPackagePath).getName();
        String packageLastProcessDir = getLastProcessDir().getAbsolutePath();

        String unzipalignApkPath = savePath + File.separator + getUnzipalignApkName(originApkName);


        ZipUtils.zip(unpackFilePath, unzipalignApkPath);

        String keyStoreFilePath = packageLastProcessDir + File.separator + "dpt.jks";

        String keyStoreAssetPath = "assets/dpt.jks";

        try {
            ZipUtils.readResourceFromRuntime(keyStoreAssetPath, keyStoreFilePath);
        }
        catch (IOException e){
            e.printStackTrace();
        }

        String unsignedApkPath = savePath + File.separator + getUnsignApkName(originApkName);
        boolean zipalignSuccess = false;
        try {
            zipalign(unzipalignApkPath, unsignedApkPath);
            zipalignSuccess = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        String willSignApkPath = null;
        if (zipalignSuccess) {
            LogUtils.info("zipalign success.");
            willSignApkPath = unsignedApkPath;

        }
        else {
            LogUtils.error("warning: zipalign failed!");
            willSignApkPath = unzipalignApkPath;
        }

        boolean signResult = false;

        String signedApkPath = savePath + File.separator + getSignedApkName(originApkName);

        if(isSign()) {
            signResult = signPackageDebug(willSignApkPath, keyStoreFilePath, signedApkPath);
        }

        File willSignApkFile = new File(willSignApkPath);
        File signedApkFile = new File(signedApkPath);
        File keyStoreFile = new File(keyStoreFilePath);
        File idsigFile = new File(signedApkPath + ".idsig");


        LogUtils.info("Will sign package file: %s, exists: %s", willSignApkFile.getAbsolutePath(), willSignApkFile.exists());
        LogUtils.info("Signed package file: %s, exists: %s", signedApkFile.getAbsolutePath(), signedApkFile.exists());

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
                LogUtils.debug("unzipalign package path err = %s", e);
            }
        }

        if (idsigFile.exists()) {
            idsigFile.delete();
        }

        if (keyStoreFile.exists()) {
            keyStoreFile.delete();
        }
        LogUtils.info("protected package output path: " + resultPath + "\n");
    }

    private boolean signPackageDebug(String packagePath, String keyStorePath, String signedApkPath) {
        return sign(packagePath, keyStorePath, signedApkPath,
                Const.KEY_ALIAS,
                Const.STORE_PASSWORD,
                Const.KEY_PASSWORD);
    }

    protected abstract boolean sign(String packagePath, String keyStorePath, String signedPackagePath,
                                String keyAlias,
                                String storePassword,
                                String KeyPassword);

    private static void zipalign(String inputApkPath, String outputApkPath) throws Exception{
        RandomAccessFile in = new RandomAccessFile(inputApkPath, "r");
        FileOutputStream out = new FileOutputStream(outputApkPath);
        ZipAlign.alignZip(in, out);
        IoUtils.close(in);
        IoUtils.close(out);
    }

    public void protect() throws FileNotFoundException {

        String path = "shell-files";
        if(!new File(path).exists()) {
            String msg = "Cannot find directory: shell-files!";
            LogUtils.error(msg);
            throw new FileNotFoundException(msg);
        }

        File willProtectFile = new File(getFilePath());

        if(!willProtectFile.exists()){
            String msg = String.format(Locale.US, "File not exists: %s", getFilePath());
            throw new FileNotFoundException(msg);
        }

    }

}
