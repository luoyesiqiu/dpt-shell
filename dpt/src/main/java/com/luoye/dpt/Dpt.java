package com.luoye.dpt;

import com.luoye.dpt.builder.Aab;
import com.luoye.dpt.builder.AndroidPackage;
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
            AndroidPackage androidPackage = parseOptions(args);
            if(androidPackage == null) {
                return;
            }
            androidPackage.protect();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void usage(Options options, String msg) {
        System.err.println("Error: " + msg);
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("java -jar dpt.jar [option] -f <apk/aab>",options);
    }

    private static AndroidPackage parseOptions(String[] args) {
        Options options = new Options();
        options.addOption(new Option(Const.OPTION_NO_SIGN_PACKAGE,Const.OPTION_NO_SIGN_PACKAGE_LONG,false,"Do not sign package."));
        options.addOption(new Option(Const.OPTION_DUMP_CODE,Const.OPTION_DUMP_CODE_LONG,false,"Dump the code item of DEX and save it to .json files."));
        options.addOption(new Option(Const.OPTION_OPEN_NOISY_LOG,Const.OPTION_OPEN_NOISY_LOG_LONG,false,"Open noisy log."));
        options.addOption(new Option(Const.OPTION_INPUT_FILE,Const.OPTION_INPUT_FILE_LONG,true,"Need to protect .apk/.aab file."));
        options.addOption(new Option(Const.OPTION_DEBUGGABLE,Const.OPTION_DEBUGGABLE_LONG,false,"Make apk debuggable."));
        options.addOption(new Option(Const.OPTION_DISABLE_APP_COMPONENT_FACTORY,Const.OPTION_DISABLE_APP_COMPONENT_FACTORY_LONG,false,"Disable app component factory(just use for debug)."));
        CommandLineParser commandLineParser = new DefaultParser();
        try {
            CommandLine commandLine = commandLineParser.parse(options, args);
            if(!commandLine.hasOption(Const.OPTION_INPUT_FILE)) {
                usage(options,"Need a file path.");
                return null;
            }
            LogUtils.setOpenNoisyLog(commandLine.hasOption(Const.OPTION_OPEN_NOISY_LOG));

            String filePath = commandLine.getOptionValue(Const.OPTION_INPUT_FILE);

            if(filePath.endsWith(".apk")) {
                return new Apk.Builder()
                        .filePath(filePath)
                        .sign(!commandLine.hasOption(Const.OPTION_NO_SIGN_PACKAGE))
                        .debuggable(commandLine.hasOption(Const.OPTION_DEBUGGABLE))
                        .appComponentFactory(!commandLine.hasOption(Const.OPTION_DISABLE_APP_COMPONENT_FACTORY))
                        .dumpCode(commandLine.hasOption(Const.OPTION_DUMP_CODE))
                        .build();
            }
            else if(filePath.endsWith(".aab")) {
                return new Aab.Builder()
                        .filePath(filePath)
                        .sign(!commandLine.hasOption(Const.OPTION_NO_SIGN_PACKAGE))
                        .debuggable(commandLine.hasOption(Const.OPTION_DEBUGGABLE))
                        .appComponentFactory(!commandLine.hasOption(Const.OPTION_DISABLE_APP_COMPONENT_FACTORY))
                        .dumpCode(commandLine.hasOption(Const.OPTION_DUMP_CODE))
                        .build();
            }
            else {
                usage(options, "Unsupported file type!");
            }

        }
        catch (ParseException e) {
            usage(options, e.toString());
        }

        return null;
    }


}
