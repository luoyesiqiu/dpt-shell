package com.luoye.dpt.builder;

import com.luoye.dpt.config.Const;
import com.luoye.dpt.res.AabManifestEditor;
import com.luoye.dpt.util.FileUtils;
import com.luoye.dpt.util.IoUtils;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.util.RC4Utils;
import com.luoye.dpt.util.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Aab extends AndroidPackage {

    public static class Builder extends AndroidPackage.Builder {
        @Override
        public Aab build() {
            return new Aab(this);
        }
    }

    protected Aab(Aab.Builder builder) {
        super(builder);
    }

    @Override
    public void writeProxyAppName(String manifestDir) {
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        AabManifestEditor.writeApplicationName(inManifestPath, outManifestPath, getProxyApplicationName());

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void writeProxyComponentFactoryName(String manifestDir) {
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        AabManifestEditor.writeAppComponentFactory(inManifestPath, outManifestPath, getProxyComponentFactory());

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void setExtractNativeLibs(String manifestDir) {
        // extractNativeLibs
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        AabManifestEditor.writeApplicationExtractNativeLibs(inManifestPath, outManifestPath, "true");

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void setDebuggable(String manifestDir, boolean debuggable) {
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        AabManifestEditor.writeDebuggable(inManifestPath, outManifestPath, String.valueOf(debuggable));

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    protected File getOutAssetsDir(String packageDir) {
        return FileUtils.getDir(getBaseDir(packageDir),"assets");
    }


    protected String getManifestFileDir(String packageOutDir) {
        return getBaseDir(packageOutDir) + File.separator + "manifest";
    }

    @Override
    protected String getManifestFilePath(String packageOutDir) {
        return getManifestFileDir(packageOutDir)  + File.separator + "AndroidManifest.xml";
    }

    @Override
    public void saveApplicationName(String packageOutDir) {
        String androidManifestFile = getManifestFilePath(packageOutDir);
        File appNameOutFile = new File(getOutAssetsDir(packageOutDir),"app_name");
        String appName = AabManifestEditor.getApplicationName(androidManifestFile);

        appName = appName == null ? "" : appName;
        appName = appName.startsWith(".") ? appName.substring(1) : appName;

        IoUtils.writeFile(appNameOutFile.getAbsolutePath(),appName.getBytes());
    }

    @Override
    public void saveAppComponentFactory(String packageOutDir) {
        String androidManifestFile = getManifestFilePath(packageOutDir);
        File acfOutFile = new File(getOutAssetsDir(packageOutDir),"app_acf");
        String acfName = AabManifestEditor.getAppComponentFactory(androidManifestFile);

        acfName = acfName == null ? "" : acfName;

        IoUtils.writeFile(acfOutFile.getAbsolutePath(),acfName.getBytes());
    }

    public String getBaseDir(String packageDir) {
        return  packageDir + File.separator + "base";
    }

    @Override
    public String getLibDir(String packageDir) {
        return  getBaseDir(packageDir) + File.separator + "lib";
    }

    @Override
    public String getDexDir(String packageDir) {
        return getBaseDir(packageDir) + File.separator + "dex";
    }

    @Override
    protected boolean sign(String packagePath, String keyStorePath, String signedPackagePath, String keyAlias, String storePassword, String KeyPassword) {
        List<String> command = new ArrayList<>();
        command.add(FileUtils.getJarSignerCommand());
        command.add("-keystore");
        command.add(keyStorePath);
        command.add("-storepass");
        command.add(storePassword);
        command.add("-keypass");
        command.add(KeyPassword);
        command.add("-signedjar");
        command.add(signedPackagePath);

        command.add(packagePath);
        command.add(keyAlias);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void process(Aab aab) {
        File aabFile = new File(aab.getFilePath());

        //aab extract path
        String aabMainProcessPath = aab.getWorkspaceDir().getAbsolutePath();

        LogUtils.info("Aab path: " + aabMainProcessPath);

        ZipUtils.unZip(aab.getFilePath(), aabMainProcessPath);
        String manifestFilePath = aab.getManifestFilePath(aabMainProcessPath);

        String manifestFileDir = aab.getManifestFileDir(aabMainProcessPath);

        String packageName = AabManifestEditor.getPackageName(manifestFilePath);
        aab.setPackageName(packageName);

        /*======================================*
         * Process AndroidManifest.xml
         *======================================*/
        // Save application name to file
        aab.saveApplicationName(aabMainProcessPath);
        // Write proxy application name to AndroidManifest.xml
        aab.writeProxyAppName(manifestFileDir);

        if(aab.isAppComponentFactory()){
            aab.saveAppComponentFactory(aabMainProcessPath);
            aab.writeProxyComponentFactoryName(manifestFileDir);
        }

        if(aab.isDebuggable()) {
            LogUtils.info("Make aab debuggable.");
            aab.setDebuggable(manifestFileDir, true);
        }
        aab.setExtractNativeLibs(manifestFileDir);

        /*======================================*
         * Process .dex files
         *======================================*/
        // Extract dex code
        String assetsPath = aab.getOutAssetsDir(aabMainProcessPath).getAbsolutePath();
        aab.extractDexCode(aabMainProcessPath, assetsPath);
        aab.addJunkCodeDex(aabMainProcessPath);
        aab.compressDexFiles(aabMainProcessPath);
        aab.deleteAllDexFiles(aabMainProcessPath);
        aab.combineDexZipWithShellDex(aabMainProcessPath);
        aab.addKeepDexes(aabMainProcessPath);
        File keepDexTempDir = aab.getKeepDexTempDir(aabMainProcessPath);
        FileUtils.deleteRecurse(keepDexTempDir);

        /*======================================*
         * Process .so files
         *======================================*/
        aab.copyNativeLibs(aabMainProcessPath);

        byte[] rc4key = RC4Utils.generateRC4Key();
        aab.encryptSoFiles(aabMainProcessPath, rc4key);

        /*======================================*
         * Build package
         *======================================*/
        aab.buildPackage(aabFile.getAbsolutePath(), aabMainProcessPath, FileUtils.getUserDir());

        File apkMainProcessFile = new File(aabMainProcessPath);

        if (apkMainProcessFile.exists()) {
            FileUtils.deleteRecurse(apkMainProcessFile);
        }
        LogUtils.info("All done.");
    }

    @Override
    public void protect() throws IOException {
        super.protect();
        process(this);
    }

}
