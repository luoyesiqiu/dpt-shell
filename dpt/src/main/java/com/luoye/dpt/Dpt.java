package com.luoye.dpt;

import com.luoye.dpt.util.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;

public class Dpt {

    public static void main(String[] args) {
        try {
            parseOptions(args);
            if(Global.optionApkPath != null) {
                processApk(Global.optionApkPath);
            }
        } catch (Exception e){
        }
    }

    private static void usage(Options options){
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("java -jar dpt.jar [option] -f <apk>",options);
    }

    private static void parseOptions(String[] args){
        Options options = new Options();
        options.addOption(new Option(Const.OPTION_NO_SIGN_APK,Const.OPTION_NO_SIGN_APK_LONG,false,"Do not sign apk."));
        options.addOption(new Option(Const.OPTION_DUMP_CODE,Const.OPTION_DUMP_CODE_LONG,false,"Dump the code item of DEX and save it to .json files."));
        options.addOption(new Option(Const.OPTION_OPEN_NOISY_LOG,Const.OPTION_OPEN_NOISY_LOG_LONG,false,"Open noisy log."));
        options.addOption(new Option(Const.OPTION_APK_FILE,Const.OPTION_APK_FILE_LONG,true,"Need to protect apk file."));
        options.addOption(new Option(Const.OPTION_DEBUGGABLE,Const.OPTION_DEBUGGABLE_LONG,false,"Make apk debuggable."));
        CommandLineParser commandLineParser = new DefaultParser();
        try {
            CommandLine commandLine = commandLineParser.parse(options, args);
            if(!commandLine.hasOption(Const.OPTION_APK_FILE)){
                usage(options);
                return;
            }
            LogUtils.setOpenNoisyLog(commandLine.hasOption(Const.OPTION_OPEN_NOISY_LOG));
            Global.optionApkPath = commandLine.getOptionValue(Const.OPTION_APK_FILE);
            Global.dumpCode = commandLine.hasOption(Const.OPTION_DUMP_CODE);
            Global.optionSignApk = !commandLine.hasOption(Const.OPTION_NO_SIGN_APK);
            Global.debuggable = commandLine.hasOption(Const.OPTION_DEBUGGABLE);
        }
        catch (ParseException e){
            usage(options);
        }
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

        //apk文件解压的目录
        String apkMainProcessPath = ApkUtils.getWorkspaceDir().getAbsolutePath();

        LogUtils.info("Apk main process path: " + apkMainProcessPath);

        ZipUtils.extractAPK(apkPath,apkMainProcessPath);
        Global.packageName = ManifestUtils.getPackageName(apkMainProcessPath + File.separator + "AndroidManifest.xml");
        ApkUtils.extractDexCode(apkMainProcessPath);

        ApkUtils.compressDexFiles(apkMainProcessPath);
        ApkUtils.deleteAllDexFiles(apkMainProcessPath);

        ApkUtils.saveApplicationName(apkMainProcessPath);
        ApkUtils.writeProxyAppName(apkMainProcessPath);
        ApkUtils.saveAppComponentFactory(apkMainProcessPath);
        ApkUtils.writeProxyComponentFactoryName(apkMainProcessPath);
        if(Global.debuggable) {
            LogUtils.info("Make apk debuggable.");
            ApkUtils.setDebuggable(apkMainProcessPath, true);
        }

        ApkUtils.addProxyDex(apkMainProcessPath);

        ApkUtils.copyShellLibs(apkMainProcessPath, new File(FileUtils.getExecutablePath(),"shell-files/libs"));

        ApkUtils.buildApk(apkFile.getAbsolutePath(),apkMainProcessPath,FileUtils.getExecutablePath());

        File apkMainProcessFile = new File(apkMainProcessPath);
        if (apkMainProcessFile.exists()) {
            FileUtils.deleteRecurse(apkMainProcessFile);
        }
        LogUtils.info("All done.");
    }
}
