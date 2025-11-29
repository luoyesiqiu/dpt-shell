package com.luoye.dpt.builder;

import com.iyxan23.zipalignjava.ZipAlign;
import com.luoye.dpt.config.Const;
import com.luoye.dpt.config.ProtectRules;
import com.luoye.dpt.config.ShellConfig;
import com.luoye.dpt.dex.JunkCodeGenerator;
import com.luoye.dpt.elf.ReadElf;
import com.luoye.dpt.model.Instruction;
import com.luoye.dpt.model.MultiDexCode;
import com.luoye.dpt.task.ThreadPool;
import com.luoye.dpt.util.CryptoUtils;
import com.luoye.dpt.util.DexUtils;
import com.luoye.dpt.util.FileUtils;
import com.luoye.dpt.util.HexUtils;
import com.luoye.dpt.util.IoUtils;
import com.luoye.dpt.util.KeyUtils;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.util.MultiDexCodeUtils;
import com.luoye.dpt.util.StringUtils;
import com.luoye.dpt.util.ZipUtils;

import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AndroidPackage {

    public static abstract class Builder {
        public String filePath = null;
        public String outputPath = null;
        public String packageName = null;
        public boolean debuggable = false;
        public boolean sign = true;
        public boolean appComponentFactory = true;
        public boolean dumpCode = false;
        public List<String> excludedAbi;
        public String rulesFilePath;
        public boolean keepClasses = false;
        public boolean smaller = false;

        public Builder filePath(String path) {
            this.filePath = path;
            return this;
        }

        public Builder outputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder excludedAbi(List<String> excludedAbi) {
            this.excludedAbi = excludedAbi;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder smaller(boolean smaller) {
            this.smaller = smaller;
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

        public Builder rulesFile(String rulesFilePath) {
            this.rulesFilePath = rulesFilePath;
            return this;
        }

        public Builder keepClasses(boolean keepClasses) {
            this.keepClasses = keepClasses;
            return this;
        }

        public abstract AndroidPackage build();
    } // Builder

    public String filePath = null;
    public String packageName = null;
    public boolean debuggable = false;
    public boolean sign = true;
    public boolean appComponentFactory = true;
    private boolean dumpCode = false;
    private boolean smaller = false;
    private List<String> excludedAbi;

    public String outputPath = null;
    public String rulesFilePath = null;

    public boolean keepClasses = false;

    public AndroidPackage(Builder builder) {
        setFilePath(builder.filePath);
        setDebuggable(builder.debuggable);
        setAppComponentFactory(builder.appComponentFactory);
        setSign(builder.sign);
        setPackageName(builder.packageName);
        setDumpCode(builder.dumpCode);
        setExcludedAbi(builder.excludedAbi);
        setOutputPath(builder.outputPath);
        setRulesFilePath(builder.rulesFilePath);
        setKeepClasses(builder.keepClasses);
        setSmaller(builder.smaller);
    }

    public boolean isSmaller() {
        return smaller;
    }

    public void setSmaller(boolean smaller) {
        this.smaller = smaller;
    }

    private void setKeepClasses(boolean keepClasses) {
        this.keepClasses = keepClasses;
    }

    public boolean isKeepClasses() {
        return keepClasses;
    }

    private void setRulesFilePath(String rulesFilePath) {
        this.rulesFilePath = rulesFilePath;
    }

    public String getRulesFilePath() {
        return rulesFilePath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public void setExcludedAbi(List<String> excludedAbi) {
        this.excludedAbi = excludedAbi;
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
            File shellDexFile = new File(getProxyDexPath());
            File renameDexFile = new File(getRenameDexPath());

            DexUtils.renamePackageName(shellDexFile, renameDexFile);

            File originalDexZipFile = new File(getOutAssetsDir(packageMainProcessPath).getAbsolutePath() + File.separator + Const.KEY_DEXES_STORE_NAME);
            byte[] zipData = com.android.dex.util.FileUtils.readFile(originalDexZipFile);// Read the zip file as binary data
            byte[] unShellDexArray =  com.android.dex.util.FileUtils.readFile(renameDexFile); // Read the dex file as binary data
            int zipDataLen = zipData.length;
            int unShellDexLen = unShellDexArray.length;
            LogUtils.info("Dexes zip file size: %s", zipDataLen);
            LogUtils.info("Proxy dex file size: %s", unShellDexLen);
            int totalLen = zipDataLen + unShellDexLen + 4;// An additional 4 bytes are added to store the length
            byte[] newDexBytes = new byte[totalLen]; // Allocate the new length

            // Add the shell code
            System.arraycopy(unShellDexArray, 0, newDexBytes, 0, unShellDexLen);// First, copy the dex content
            // Add the unencrypted zip data
            System.arraycopy(zipData, 0, newDexBytes, unShellDexLen, zipDataLen); // Then copy the APK content after the dex content
            // Add the length of the shell data
            System.arraycopy(FileUtils.intToByte(zipDataLen), 0, newDexBytes, totalLen - 4, 4);// The last 4 bytes are for the length

            // Modify the DEX file size header
            FileUtils.fixFileSizeHeader(newDexBytes);
            // Modify the DEX SHA1 header
            FileUtils.fixSHA1Header(newDexBytes);
            // Modify the DEX CheckSum header
            FileUtils.fixCheckSumHeader(newDexBytes);

            String targetDexFile = getDexDir(packageMainProcessPath) + File.separator + "classes.dex";


            File file = new File(targetDexFile);
            if (!file.exists()) {
                file.createNewFile();
            }

            // Output the new dex file
            FileOutputStream localFileOutputStream = new FileOutputStream(targetDexFile);
            localFileOutputStream.write(newDexBytes);
            localFileOutputStream.flush();
            localFileOutputStream.close();
            LogUtils.info("New Dex file generated: " + targetDexFile);
            // Delete the dex zip package
            FileUtils.deleteRecurse(originalDexZipFile);
            FileUtils.deleteRecurse(renameDexFile);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private String getUnsignPackageName(String packageFileName){
        return FileUtils.getNewFileName(packageFileName,"unsign");
    }

    private String getUnzipalignPackageName(String packageFileName){
        return FileUtils.getNewFileName(packageFileName,"unzipalign");
    }

    private String getSignedPackageName(String packageFileName){
        return FileUtils.getNewFileName(packageFileName,"signed");
    }

    public void writeConfig(String packageDir, byte[] key) {
        File configFile = new File(getOutAssetsDir(packageDir).getAbsolutePath() + File.separator + Const.KEY_SHELL_CONFIG_STORE_NAME);
        ShellConfig shellConfig = ShellConfig.getInstance();
        String json = shellConfig.toJson();
        LogUtils.info("Write config: " + json);
        byte[] iv = KeyUtils.generateIV(key);
        byte[] secData = CryptoUtils.aesEncrypt(key, iv, json.getBytes(StandardCharsets.UTF_8));
        IoUtils.writeFile(configFile.getAbsolutePath(), secData);
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

    public File getKeepDexTempDir(String packageDir) {
        return FileUtils.getDir(getDexDir(packageDir), "keep-dex-dir");
    }

    public String getProxyApplicationName() {
        return String.format(Locale.US, "%s.%s", ShellConfig.getInstance().getShellPackageName(), "ProxyApplication");
    }

    public String getProxyComponentFactory() {
        return String.format(Locale.US, "%s.%s", ShellConfig.getInstance().getShellPackageName(), "ProxyComponentFactory");
    }

    protected String getProxyDexPath() {
        return FileUtils.getExecutablePath() + File.separator + "shell-files" + File.separator + "dex" + File.separator + "classes.dex";
    }

    protected String getRenameDexPath() {
        return FileUtils.getExecutablePath() + File.separator + "shell-files" + File.separator + "dex" + File.separator + "rename_classes.dex";
    }

    private void addProxyDex(String packageOutDir) {
        addDex(getProxyDexPath(), packageOutDir);
    }

    protected String getJunkCodeDexPath() {
        return FileUtils.getExecutablePath() + File.separator + "shell-files" + File.separator + "dex" + File.separator + "junkcode.dex";
    }

    protected void addJunkCodeDex(String packageDir) {
        addDex(getJunkCodeDexPath(), getDexDir(packageDir));
    }

    protected void addKeepDexes(String packageDir) {
        File keepDexTempDir = getKeepDexTempDir(packageDir);
        File[] files = keepDexTempDir.listFiles();

        if(files != null) {
            for (File file : files) {
                if(file.getName().endsWith(".dex")) {
                    addDex(file.getAbsolutePath(), getDexDir(packageDir));
                }
            }
        }
    }

    public void compressDexFiles(String packageDir) {
        Map<String, CompressionMethod> rulesMap = new HashMap<>();
        rulesMap.put("classes\\d*.dex", CompressionMethod.STORE);
        String unalignedFilePath = getOutAssetsDir(packageDir).getAbsolutePath() + File.separator + Const.KEY_DEXES_STORE_UNALIGNED_NAME;
        String alignedFilePath = getOutAssetsDir(packageDir).getAbsolutePath() + File.separator + Const.KEY_DEXES_STORE_NAME;
        ZipUtils.compress(getDexFiles(getDexDir(packageDir))
                , unalignedFilePath
                , rulesMap
        );
        RandomAccessFile randomAccessFile = null;
        FileOutputStream out = null;
        boolean isAligned = false;
        try {
            randomAccessFile = new RandomAccessFile(unalignedFilePath, "r");
            out = new FileOutputStream(alignedFilePath);
            ZipAlign.alignZip(randomAccessFile, out);
            IoUtils.close(randomAccessFile);
            IoUtils.close(out);
            org.apache.commons.io.FileUtils.forceDelete(new File(unalignedFilePath));
            LogUtils.info("zip aligned: " + alignedFilePath);
            isAligned = true;
        }
        catch (Exception e) {
            LogUtils.warn("WARNING: ZipAlign failed: %s", unalignedFilePath);
        }
        finally {
            IoUtils.close(randomAccessFile);
            IoUtils.close(out);
        }

        if(!isAligned) {
            try {
                Files.move(Paths.get(unalignedFilePath), Paths.get(alignedFilePath), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    public void copyNativeLibs(String packageDir) {
        File sourceDirRoot = new File(FileUtils.getExecutablePath(), "shell-files" + File.separator + "libs");
        File destDirRoot = new File(getOutAssetsDir(packageDir).getAbsolutePath(), Const.KEY_LIBS_DIR_NAME);

        if (!destDirRoot.exists()) {
            destDirRoot.mkdirs();
        }

        File[] abiDirs = sourceDirRoot.listFiles();
        if (abiDirs == null) {
            return;
        }

        for (File abiDir : abiDirs) {
            if (!abiDir.isDirectory()) {
                continue;
            }

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
            if (libFiles == null) {
                continue;
            }

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

    public void encryptSoFiles(String packageOutDir, byte[] rc4Key){
        File obfDir = new File(getOutAssetsDir(packageOutDir).getAbsolutePath() + File.separator, Const.KEY_LIBS_DIR_NAME);
        File[] soAbiDirs = obfDir.listFiles();
        if(soAbiDirs == null) {
            return;
        }

        for (File soAbiDir : soAbiDirs) {
            File[] soFiles = soAbiDir.listFiles();
            if(soFiles == null) {
                continue;
            }

            for (File soFile : soFiles) {
                if(!soFile.getAbsolutePath().endsWith(".so")) {
                    continue;
                }
                encryptSoFile(soFile, rc4Key);
                writeSoFileCryptKey(soFile, rc4Key);
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

                    byte[] enc = CryptoUtils.rc4Crypt(rc4Key, bitcode);
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

    private void addDex(String dexFilePath, String dexFilesSavePath){
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

    public boolean isAndroidPackageFile(File f) {
        return f.getAbsolutePath().endsWith(".apk")
                || f.getAbsolutePath().endsWith(".aab");
    }

    public void extractDexCode(String packageDir, String dexCodeSavePath) {
        List<File> dexFiles = getDexFiles(getDexDir(packageDir));
        Map<Integer,List<Instruction>> instructionMap = new HashMap<>();
        String appNameNew = Const.KEY_CODE_ITEM_STORE_NAME;
        String dataOutputPath = dexCodeSavePath + File.separator + appNameNew;

        CountDownLatch countDownLatch = new CountDownLatch(dexFiles.size());
        AtomicInteger totalClassesCount = new AtomicInteger(0);
        AtomicInteger keepClassesCount = new AtomicInteger(0);
        for(File dexFile : dexFiles) {
            ThreadPool.getInstance().execute(() -> {
                final int dexNo = DexUtils.getDexNumber(dexFile.getName());
                if(dexNo < 0) {
                    return;
                }

                if(isKeepClasses()) {
                    File keepDex = new File(getKeepDexTempDir(packageDir).getAbsolutePath() + File.separator + dexFile.getName());
                    File splitDex = new File(dexFile.getAbsolutePath() + "_split.dex");

                    try {
                        Pair<Integer, Integer> classesCountPair = DexUtils.splitDex(dexFile, keepDex, splitDex);

                        keepClassesCount.set(keepClassesCount.get() + classesCountPair.getKey());
                        totalClassesCount.set(totalClassesCount.get() + classesCountPair.getValue());

                        dexFile.delete();

                        splitDex.renameTo(dexFile);
                    } catch (Exception e) {
                        LogUtils.warn("WARNING: split %s fail", dexFile.getName());
                        keepDex.delete();
                        splitDex.delete();
                    }

                }

                String extractedDexName = dexFile.getName().endsWith(".dex") ? dexFile.getName().replaceAll("\\.dex$", "_extracted.dat") : "_extracted.dat";
                File extractedDexFile = new File(dexFile.getParent(), extractedDexName);

                boolean obfuscate = !isSmaller();
                List<Instruction> ret = DexUtils.extractAllMethods(dexFile, extractedDexFile, getPackageName(), isDumpCode(), obfuscate);
                instructionMap.put(dexNo,ret);

                File dexFileRightHashes = new File(dexFile.getParent(), FileUtils.getNewFileSuffix(dexFile.getName(),"dat"));
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

        if(isKeepClasses()) {
            LogUtils.info("Keep classes: %d, total classes: %d", keepClassesCount.get(), totalClassesCount.get());
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
        String outputPath = getOutputPath();
        File outputPathFile;
        String outputDir;
        String resultFileName = null;

        if (outputPath != null) {
            outputPathFile = new File(outputPath);
            if (isAndroidPackageFile(outputPathFile)) {
                outputPathFile = new File(outputPath.contains(File.separator) ? outputPath : "." + File.separator + outputPath);

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

        String originPackageName = new File(originPackagePath).getName();
        String packageLastProcessDir = getLastProcessDir().getAbsolutePath();

        String unzipalignPackagePath = outputDir
                + File.separator
                + (resultFileName != null ? "temp_" + resultFileName : getUnzipalignPackageName(originPackageName));

        if(isSmaller()) {
            LogUtils.info("Used smaller option");
        }
        ZipUtils.zip(unpackFilePath, unzipalignPackagePath, isSmaller());

        String keyStoreFilePath = packageLastProcessDir + File.separator + "dpt.jks";

        String keyStoreAssetPath = "assets/dpt.jks";

        try {
            ZipUtils.readResourceFromRuntime(keyStoreAssetPath, keyStoreFilePath);
        }
        catch (IOException e){
            e.printStackTrace();
        }

        String unsignedPackagePath = outputDir
                + File.separator
                + (resultFileName != null ? "unsigned_" + resultFileName : getUnsignPackageName(originPackageName));

        boolean zipalignSuccess = false;

        try {
            zipalign(unzipalignPackagePath, unsignedPackagePath);
            zipalignSuccess = true;
            LogUtils.info("zipalign success.");
        } catch (Exception e) {
            LogUtils.error("zipalign failed!");
        }

        String willSignPackagePath = zipalignSuccess ? unsignedPackagePath : unzipalignPackagePath;

        boolean signResult = false;

        String signedPackagePath = outputDir
                + File.separator
                + (resultFileName != null ? resultFileName : getSignedPackageName(originPackageName));

        if(isSign()) {
            signResult = signPackageDebug(willSignPackagePath, keyStoreFilePath, signedPackagePath);
        }
        else {
            try {
                if(outputPath != null) {
                    Files.copy(Paths.get(willSignPackagePath), Paths.get(signedPackagePath),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {}
        }

        File willSignPackageFile = new File(willSignPackagePath);
        File signedPackageFile = new File(signedPackagePath);
        File keyStoreFile = new File(keyStoreFilePath);
        File idsigFile = new File(signedPackagePath + ".idsig");

        LogUtils.info("unsign package file: %s, exists: %s", willSignPackageFile.getAbsolutePath(), willSignPackageFile.exists());

        String resultPath = signedPackageFile.exists() ? signedPackageFile.getAbsolutePath() : willSignPackageFile.getAbsolutePath();

        if (signedPackageFile.exists() && willSignPackageFile.exists()) {
            willSignPackageFile.delete();
        }

        if(signResult) {
            LogUtils.info("signed package file: " + signedPackageFile.getAbsolutePath());
        }

        if(zipalignSuccess) {
            try {
                Files.deleteIfExists(Paths.get(unzipalignPackagePath));
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

    private boolean signPackageDebug(String packagePath, String keyStorePath, String signedPackagePath) {
        return sign(packagePath, keyStorePath, signedPackagePath,
                Const.KEY_ALIAS,
                Const.STORE_PASSWORD,
                Const.KEY_PASSWORD);
    }

    protected abstract boolean sign(String packagePath, String keyStorePath, String signedPackagePath,
                                String keyAlias,
                                String storePassword,
                                String KeyPassword);

    private static void zipalign(String inputPackagePath, String outputPackagePath) throws Exception{
        RandomAccessFile in = new RandomAccessFile(inputPackagePath, "r");
        FileOutputStream out = new FileOutputStream(outputPackagePath);
        ZipAlign.alignZip(in, out);
        IoUtils.close(in);
        IoUtils.close(out);
    }

    public void protect() throws IOException {

        String path = "shell-files";
        File shellFiles = new File(FileUtils.getExecutablePath() + File.separator + path);
        if(!shellFiles.exists()) {
            String msg = "Cannot find directory: shell-files!" + shellFiles;
            LogUtils.error(msg);
            throw new FileNotFoundException(msg);
        }

        File willProtectFile = new File(getFilePath());

        if(!willProtectFile.exists()){
            String msg = String.format(Locale.US, "File not exists: %s", getFilePath());
            throw new FileNotFoundException(msg);
        }

        try {
            if(getRulesFilePath() != null) {
                File file = new File(getRulesFilePath());
                LogUtils.debug("Exclude rules file: %s", file);
                List<String> strings = com.google.common.io.Files.readLines(file, StandardCharsets.UTF_8);
                ProtectRules protectRules = ProtectRules.getInstance();
                if (!strings.isEmpty()) {
                    protectRules.setExcludeRules(strings.toArray(String[]::new));
                } else {
                    LogUtils.debug("Exclude rules file is empty", file);

                }
            }
        }
        catch (IOException e) {
            LogUtils.info("Exclude rules file is unavailable: %s", e.getMessage());
        }

        String randomPackageName = StringUtils.generateIdentifier(10);
        ShellConfig.getInstance().init(randomPackageName);

        JunkCodeGenerator.generateJunkCodeDex(new File(getJunkCodeDexPath()));
    }

}
