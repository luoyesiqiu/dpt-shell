package com.luoye.dpt;

import com.luoye.dpt.builder.Apk;
import com.luoye.dpt.util.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Dpt {

    public static void main(String[] args) {
        try {
            Apk apk = parseOptions(args);
            apk.protect();
        } catch (Exception e){
        }
    }

    private static void usage(Options options){
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("java -jar dpt.jar [option] -f <apk>",options);
    }

    private static Apk parseOptions(String[] args){
        Options options = new Options();
        options.addOption(new Option(Const.OPTION_NO_SIGN_APK,Const.OPTION_NO_SIGN_APK_LONG,false,"Do not sign apk."));
        options.addOption(new Option(Const.OPTION_DUMP_CODE,Const.OPTION_DUMP_CODE_LONG,false,"Dump the code item of DEX and save it to .json files."));
        options.addOption(new Option(Const.OPTION_OPEN_NOISY_LOG,Const.OPTION_OPEN_NOISY_LOG_LONG,false,"Open noisy log."));
        options.addOption(new Option(Const.OPTION_APK_FILE,Const.OPTION_APK_FILE_LONG,true,"Need to protect apk file."));
        options.addOption(new Option(Const.OPTION_DEBUGGABLE,Const.OPTION_DEBUGGABLE_LONG,false,"Make apk debuggable."));
        options.addOption(new Option(Const.OPTION_DISABLE_APP_COMPONENT_FACTORY,Const.OPTION_DISABLE_APP_COMPONENT_FACTORY_LONG,false,"Disable app component factory(just use for debug)."));
        CommandLineParser commandLineParser = new DefaultParser();
        try {
            CommandLine commandLine = commandLineParser.parse(options, args);
            if(!commandLine.hasOption(Const.OPTION_APK_FILE)){
                usage(options);
                return new Apk.Builder().build();
            }
            LogUtils.setOpenNoisyLog(commandLine.hasOption(Const.OPTION_OPEN_NOISY_LOG));


            return new Apk.Builder()
                    .filePath(commandLine.getOptionValue(Const.OPTION_APK_FILE))
                    .sign(!commandLine.hasOption(Const.OPTION_NO_SIGN_APK))
                    .debuggable(commandLine.hasOption(Const.OPTION_DEBUGGABLE))
                    .appComponentFactory(!commandLine.hasOption(Const.OPTION_DISABLE_APP_COMPONENT_FACTORY))
                    .dumpCode(commandLine.hasOption(Const.OPTION_DUMP_CODE))
                    .build();
        }
        catch (ParseException e){
            usage(options);
        }

        return new Apk.Builder().build();
    }


}
