package com.luoye.dpt.util;

import com.luoye.dpt.Const;
import com.luoye.dpt.model.Instruction;
import com.luoye.dpt.model.MultiDexCode;
import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author luoyesiqiu
 */
public class ApkUtils {

    /**
     * 得到一个新的apk文件名
     * @param apkName
     * @return
     */
    public static String getNewApkName(String apkName){
        return FileUtils.getNewFileName(apkName,"unsign");
    }

    /**
     * 复制壳的so文件
     * @param src
     */
    public static void copyShellLibs(File src){
        File outLibDir = getOutLibDir();
        if(outLibDir.exists()){
            File[] libDirs = outLibDir.listFiles();
            if(libDirs != null && libDirs.length != 0) {
                for (File libDir : libDirs) {
                    File srcDir = new File(src + File.separator + libDir.getName());
                    if (srcDir.exists() && libDir.exists()) {
                        System.out.printf("copyShellLibs %s --> %s\n", srcDir.getAbsolutePath(), outLibDir.getAbsolutePath());
                        FileUtils.copy(srcDir.getAbsolutePath(), outLibDir.getAbsolutePath());
                    }
                }
            }
            else{
                File[] srcDirs = src.listFiles();
                if(srcDirs != null){
                    for (File srcDir : srcDirs) {
                        System.out.printf("copyShellLibs %s --> %s\n",srcDir.getAbsolutePath(),outLibDir.getAbsolutePath());
                        FileUtils.copy(srcDir.getAbsolutePath(), outLibDir.getAbsolutePath());
                    }
                }
            }
        }
        else{
            File[] srcDirs = src.listFiles();
            if(srcDirs != null){
                for (File srcDir : srcDirs) {
                    System.out.printf("copyShellLibs %s --> %s\n",srcDir.getAbsolutePath(),outLibDir.getAbsolutePath());
                    FileUtils.copy(srcDir.getAbsolutePath(), outLibDir.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 获取工作目录下的lib路径
     * @return
     */
    public static File getOutLibDir(){
        return FileUtils.getDir(getOutDir().getAbsolutePath(),"lib");
    }

    /**
     * 获取工作目录下的META-INF路径
     * @return
     */
    public static File getOutMetaDir(){
        return FileUtils.getDir(getOutDir().getAbsolutePath(),"META-INF");
    }

    /**
     * 删除meta-data
     */
    public static void deleteMetaData(){
        FileUtils.deleteRecurse(getOutMetaDir());
    }

    /**
     * 写入代理ApplicationName
     */
    public static void writeProxyAppName(){
        String inManifestPath = getOutDir().getAbsolutePath() + File.separator + "AndroidManifest.xml";
        String outManifestPath = getOutDir().getAbsolutePath() + File.separator + "AndroidManifest_new.xml";
        ManifestUtils.writeApplicationName(inManifestPath,outManifestPath, Const.PROXY_APPLICATION_NAME);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }
    /**
     * 写入代理ComponentFactory
     */
    public static void writeProxyComponentFactoryName(){
        String inManifestPath = getOutDir().getAbsolutePath() + File.separator + "AndroidManifest.xml";
        String outManifestPath = getOutDir().getAbsolutePath() + File.separator + "AndroidManifest_new.xml";
        ManifestUtils.writeAppComponentFactory(inManifestPath,outManifestPath, Const.PROXY_COMPONENT_FACTORY);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    /**
     * 设置extractNativeLibs为true
     */
    public static void setExtractNativeLibs(){

        String inManifestPath = getOutDir().getAbsolutePath() + File.separator + "AndroidManifest.xml";
        String outManifestPath = getOutDir().getAbsolutePath() + File.separator + "AndroidManifest_new.xml";
        ModificationProperty property = new ModificationProperty();

        property.addApplicationAttribute(new AttributeItem("extractNativeLibs","true"));

        FileProcesser.processManifestFile(inManifestPath, outManifestPath, property);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    /**
     * 获取工作目录
     * @return
     */
    public static File getOutDir(){
        return FileUtils.getDir(Const.ROOT_OF_OUT_DIR,"dptOut");
    }

    /**
     * 删除工作目录
     */
    public static void deleteOutDir(){
        File outDir = getOutDir();
        FileUtils.deleteRecurse(outDir);
    }

    /**
     * 获取工作目录下的Assets目录
     * @return
     */
    public static File getOutAssetsDir(){
        return FileUtils.getDir(getOutDir().getAbsolutePath(),"assets");
    }

    /**
     * 添加代理dex
     * @param apkDir
     */
    public static void addProxyDex(String apkDir){
        String proxyDexPath = "shell/dex/classes.dex";
        addDex(proxyDexPath,apkDir);
    }

    /**
     * 压缩dex文件
     */
    public static void compressDexFiles(String apkDir){
        compress(getDexFiles(apkDir),getOutAssetsDir().getAbsolutePath()+File.separator + "i11111i111");
    }

    public static void deleteAllDexFiles(String dir){
        List<File> dexFiles = getDexFiles(dir);
        for (File dexFile : dexFiles) {
            dexFile.delete();
        }
    }

    /**
     * 添加dex
     * @param dexFilePath
     * @param apkDir
     */
    public static void addDex(String dexFilePath,String apkDir){
        File dexFile = new File(dexFilePath);
        List<File> dexFiles = getDexFiles(apkDir);
        int newDexNameNumber = dexFiles.size() + 1;
        String newDexPath = apkDir + File.separator + String.format("classes%d.dex",newDexNameNumber);
        byte[] dexData = IoUtils.readFile(dexFile.getAbsolutePath());
        IoUtils.writeFile(newDexPath,dexData);
    }

    /**
     * 获取AndroidManiest.xml路径
     * @param apkOutDir
     * @return
     */
    private static String getManifestFilePath(String apkOutDir){
        return apkOutDir + File.separator + "AndroidManifest.xml";
    }

    /**
     * 保存ApplicationName
     * @param apkOutDir
     */
    public static void saveApplicationName(String apkOutDir){
        String androidManifestFile = getManifestFilePath(apkOutDir);
        File appNameOutFile = new File(getOutAssetsDir(),"app_name");
        String appName = ManifestUtils.getApplicationName(androidManifestFile);

        appName = appName == null ? "" : appName;

        IoUtils.writeFile(appNameOutFile.getAbsolutePath(),appName.getBytes());
    }

    /**
     * 保存ComponentFactory
     * @param apkOutDir
     */
    public static boolean saveAppComponentFactory(String apkOutDir){
        String androidManifestFile = getManifestFilePath(apkOutDir);
        File appNameOutFile = new File(getOutAssetsDir(),"app_acf");
        String appName = ManifestUtils.getAppComponentFactory(androidManifestFile);

        appName = appName == null ? "" : appName;

        if(isSystemComponentFactory(appName)){
            appName = "";
        }

        IoUtils.writeFile(appNameOutFile.getAbsolutePath(),appName.getBytes());

        return !appName.equals("");
    }

    public static boolean isSystemComponentFactory(String name){
        if(name.equals("androidx.core.app.CoreComponentFactory") || name.equals("android.support.v4.app.CoreComponentFactory")){
            return true;
        }
        return false;
    }

    /**
     * 提取apk里的dex的代码
     * @param apkOutDir
     */
    public static void  extractDexCode(String apkOutDir){
        List<File> dexFiles = getDexFiles(apkOutDir);
        List<List<Instruction>> instructionList = new ArrayList<>();
        String appNameNew = "OoooooOooo";
        String dataOutputPath = getOutAssetsDir().getAbsolutePath() + File.separator + appNameNew;
        for(File dexFile : dexFiles) {
            String newName = dexFile.getName().endsWith(".dex") ? dexFile.getName().replaceAll("\\.dex$", "_tmp.dex") : "_tmp.dex";
            File dexFileNew = new File(dexFile.getParent(), newName);
            //抽取dex的代码
            List<Instruction> ret = DexUtils.extractAllMethods(dexFile, dexFileNew);
            instructionList.add(ret);
            //更新dex的hash
            File dexFileRightHashes = new File(dexFile.getParent(),FileUtils.getNewFileName(dexFile.getName(),"new"));
            DexUtils.writeHashes(dexFileNew,dexFileRightHashes);
            dexFile.delete();
            dexFileNew.delete();
            dexFileRightHashes.renameTo(dexFile);

        }

        MultiDexCode multiDexCode = MultiDexCodeUtils.makeMultiDexCode(instructionList);

        MultiDexCodeUtils.writeMultiDexCode(dataOutputPath,multiDexCode);

    }

    /**
     * 获取某个目录下的所有dex文件
     * @param dir
     * @return
     */
    public static List<File> getDexFiles(String dir){
        List<File> dexFiles = new ArrayList<>();
        File dirFile = new File(dir);
        File[] files = dirFile.listFiles();
        if(files != null) {
            Arrays.stream(files).filter(it -> it.getName().endsWith("dex")).forEach(dexFiles::add);
        }
        return dexFiles;
    }

    /**
     * 解压文件
     * @param apkFile
     * @param destDir
     */
    public static void extract(String apkFile,String destDir)  {
        ZipFile zipFile = new ZipFile(apkFile);
        try {
            zipFile.extractAll(destDir);
        }
        catch (ZipException e){
            e.printStackTrace();
        }
    }

    /**
     * 压缩文件
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

    /**
     * 压缩文件
     */
    public static void compress(String srcDir,String destFile)  {
        ZipFile zipFile = new ZipFile(destFile);
        File dir = new File(srcDir);
        File[] list = dir.listFiles();
        if(list == null){
            return;
        }
        try {
            for(File f : list){
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
