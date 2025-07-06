package com.luoye.dpt.util;

import com.android.dex.ClassData;
import com.android.dex.ClassDef;
import com.android.dex.Code;
import com.android.dex.Dex;
import com.luoye.dpt.config.ProtectRules;
import com.luoye.dpt.model.Instruction;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriters;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author luoyesiqiu
 */
public class DexUtils {
    private final static Map<String,Integer> codeOffAppearMap = new ConcurrentHashMap<>();

    /**
     * split dex file
     * @param originDex The dex file will be split
     * @param keepDex The dex file will keep in apk
     * @param splitDex The dex file will be extract
     */
    public static Pair<Integer,Integer> splitDex(File originDex, File keepDex, File splitDex) {

        AtomicInteger totalClassesCount = new AtomicInteger();
        AtomicInteger keepClassesCount = new AtomicInteger();
        ProtectRules protectRules = ProtectRules.getsInstance();
        try {

            DexBackedDexFile dexBackedDexFile = DexFileFactory.loadDexFile(originDex, Opcodes.getDefault());

            DexRewriter keepRewriter = new DexRewriter(new RewriterModule() {
                @Override
                public Rewriter<DexFile> getDexFileRewriter(Rewriters rewriters) {
                    return value -> {
                        Set<? extends org.jf.dexlib2.iface.ClassDef> classes = value.getClasses();
                        totalClassesCount.set(classes.size());
                        Set<org.jf.dexlib2.iface.ClassDef> newClasses = new HashSet<>();
                        for (org.jf.dexlib2.iface.ClassDef aClass : classes) {
                            // match rules
                            if (protectRules.matchRules(aClass.getType())) {
                                newClasses.add(aClass);
                                keepClassesCount.getAndIncrement();
                            }
                        }
                        return new ImmutableDexFile(value.getOpcodes(), newClasses);
                    };
                }
            });

            DexFile keepDexFile = keepRewriter.getDexFileRewriter().rewrite(dexBackedDexFile);
            DexFileFactory.writeDexFile(keepDex.getAbsolutePath(), keepDexFile);

            DexRewriter splitRewriter = new DexRewriter(new RewriterModule() {
                @Override
                public Rewriter<DexFile> getDexFileRewriter(Rewriters rewriters) {
                    return value -> {
                        Set<? extends org.jf.dexlib2.iface.ClassDef> classes = value.getClasses();
                        Set<org.jf.dexlib2.iface.ClassDef> newClasses = new HashSet<>();
                        for (org.jf.dexlib2.iface.ClassDef aClass : classes) {
                            // do not match
                            if (!protectRules.matchRules(aClass.getType())) {
                                newClasses.add(aClass);
                            }
                        }
                        return new ImmutableDexFile(value.getOpcodes(), newClasses);
                    };
                }
            });
            DexFile splitDexFile = splitRewriter.getDexFileRewriter().rewrite(dexBackedDexFile);
            DexFileFactory.writeDexFile(splitDex.getAbsolutePath(), splitDexFile);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return new ImmutablePair<>(keepClassesCount.get(), totalClassesCount.get());
    }

    private static void saveCodeOffAppear(Dex dex, int dexIndex) {
        codeOffAppearMap.clear();
        Iterable<ClassDef> classDefs = dex.classDefs();

        for (ClassDef classDef : classDefs) {
            int classDataOffset = classDef.getClassDataOffset();
            if(classDataOffset == 0) {
                continue;
            }

            ClassData classData = dex.readClassData(classDef);
            ClassData.Method[] methods = classData.allMethods();
            for (ClassData.Method method : methods) {
                if (method.getCodeOffset() != 0) {
                    codeOffAppearMap.merge(dexIndex + "_" + method.getCodeOffset(), 1, Integer::sum);
                }
            }
        }

    }

    private static int getCodeOffAppearCount(int dexIndex, int codeOff) {
        try {
            Integer appearCount = codeOffAppearMap.get(dexIndex + "_" + codeOff);
            if(appearCount != null) {
                return appearCount;
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get dex file number
     * ex：classes2.dex return 1
     */
    public static int getDexNumber(String dexName) {
        Pattern pattern = Pattern.compile("classes(\\d*)\\.dex$");
        Matcher matcher = pattern.matcher(dexName);
        if(matcher.find()) {
            String dexNo = matcher.group(1);
            return (dexNo == null || dexNo.isEmpty()) ? 0 : Integer.parseInt(dexNo) - 1;
        }
        else{
            return  -1;
        }
    }

    /**
     * Extract all methods code
     * @param dexFile dex input path
     * @param outDexFile dex output path
     * @return insns list
     */
    public static List<Instruction> extractAllMethods(File dexFile, File outDexFile,String packageName,boolean dumpCode) {
        List<Instruction> instructionList = new ArrayList<>();
        Dex dex = null;
        RandomAccessFile randomAccessFile = null;
        byte[] dexData = IoUtils.readFile(dexFile.getAbsolutePath());
        IoUtils.writeFile(outDexFile.getAbsolutePath(),dexData);
        JSONArray dumpJSON = new JSONArray();
        try {
            dex = new Dex(dexFile);
            int dexNumber = getDexNumber(dexFile.getName());
            randomAccessFile = new RandomAccessFile(outDexFile, "rw");
            Iterable<ClassDef> classDefs = dex.classDefs();

            saveCodeOffAppear(dex, dexNumber);

            for (ClassDef classDef : classDefs) {
                if(classDef.getClassDataOffset() == 0) {
                    LogUtils.noisy("class '%s' data offset is zero", classDef.toString());
                    continue;
                }
                // Skip exclude classes name
                if(ProtectRules.getsInstance().matchRules(classDef.toString())) {
                    continue;
                }

                JSONObject classJSONObject = new JSONObject();
                JSONArray classJSONArray = new JSONArray();
                ClassData classData = dex.readClassData(classDef);

                String className = dex.typeNames().get(classDef.getTypeIndex());
                String humanizeTypeName = TypeUtils.getHumanizeTypeName(className);

                ClassData.Method[] methods = classData.allMethods();
                for (ClassData.Method method : methods) {
                    if(getCodeOffAppearCount(dexNumber, method.getCodeOffset()) > 1) {
                        LogUtils.noisy("codeoff 0x%x appear many times", method.getCodeOffset());
                        continue;
                    }

                    Instruction instruction = extractMethod(dex,randomAccessFile,classDef,method);
                    if(instruction != null) {
                        instructionList.add(instruction);
                        putToJSON(classJSONArray, instruction);
                    }
                }

                classJSONObject.put(humanizeTypeName,classJSONArray);
                dumpJSON.put(classJSONObject);
            }
        }
        catch (Exception e){
        }
        finally {
            IoUtils.close(randomAccessFile);
            if(dumpCode) {
                dumpJSON(packageName,dexFile, dumpJSON);
            }
        }

        return instructionList;
    }

    private static void dumpJSON(String packageName, File originFile, JSONArray array){
        File pkg = new File(packageName);
        if(!pkg.exists()){
            pkg.mkdirs();
        }
        File writePath = new File(pkg.getAbsolutePath(),originFile.getName() + ".json");
        LogUtils.info("dump json to path: %s",writePath.getParentFile().getName() + File.separator + writePath.getName());

        IoUtils.writeFile(writePath.getAbsolutePath(),array.toString(1).getBytes());
    }

    private static void putToJSON(JSONArray array,Instruction instruction){
        JSONObject jsonObject = new JSONObject();
        String hex = HexUtils.toHexArray(instruction.getInstructionsData());
        jsonObject.put("methodId",instruction.getMethodIndex());
        jsonObject.put("code",hex);
        array.put(jsonObject);
    }

    /**
     * Extract a method code
     * @param dex dex struct
     * @param outRandomAccessFile out file
     * @param method will extract method
     * @return a insns
     */
    private static Instruction extractMethod(Dex dex ,RandomAccessFile outRandomAccessFile,ClassDef classDef,ClassData.Method method)
            throws Exception{
        String returnTypeName = dex.typeNames().get(dex.protoIds().get(dex.methodIds().get(method.getMethodIndex()).getProtoIndex()).getReturnTypeIndex());
        String methodName = dex.strings().get(dex.methodIds().get(method.getMethodIndex()).getNameIndex());
        String className = dex.typeNames().get(classDef.getTypeIndex());
        //native function or abstract function
        if(method.getCodeOffset() == 0){
            LogUtils.noisy("method code offset is zero,name =  %s.%s , returnType = %s",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    TypeUtils.getHumanizeTypeName(returnTypeName));
            return null;
        }
        Instruction instruction = new Instruction();
        // CodeItem size = registers_size + ins_size + outs_size + tries_size + debug_info_off + insns_size = 16
        int insnsOffset = method.getCodeOffset() + 16;
        Code code = dex.readCode(method);
        //Fault-tolerant handling
        if(code.getInstructions().length == 0){
            LogUtils.noisy("method has no code,name =  %s.%s , returnType = %s",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    TypeUtils.getHumanizeTypeName(returnTypeName));
            return null;
        }
        int insnsCapacity = code.getInstructions().length;
        //The insns capacity is not enough to store the return statement, skip it
        byte[] returnByteCodes = getReturnByteCodes(returnTypeName);
        if(insnsCapacity * 2 < returnByteCodes.length){
            LogUtils.noisy("The capacity of insns is not enough to store the return statement. %s.%s() ClassIndex = %d -> %s insnsCapacity = %d byte(s) but returnByteCodes = %d byte(s)",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    classDef.getTypeIndex(),
                    TypeUtils.getHumanizeTypeName(returnTypeName),
                    insnsCapacity * 2,
                    returnByteCodes.length);

            return null;
        }
        //Here, MethodIndex corresponds to the index of the method_ids area
        instruction.setMethodIndex(method.getMethodIndex());
        //Note: Here is the size of the array
        instruction.setInstructionDataSize(insnsCapacity * 2);
        byte[] byteCode = new byte[insnsCapacity * 2];
        //Write random bytes
        SecureRandom insRandom = new SecureRandom();
        for (int i = 0; i < insnsCapacity; i++) {
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            byteCode[i * 2] = outRandomAccessFile.readByte();
            byteCode[i * 2 + 1] = outRandomAccessFile.readByte();
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            outRandomAccessFile.writeShort(insRandom.nextInt());
        }
        instruction.setInstructionsData(byteCode);
        outRandomAccessFile.seek(insnsOffset);

        return instruction;
    }

    /**
     * Obtain the code of the return statement based on the jvm type
     */
    public static byte[] getReturnByteCodes(String typeName){
        byte[] returnVoidCodes = {(byte)0x0e , (byte)(0x0)};
        byte[] returnCodes = {(byte)0x12 , (byte)0x0 , (byte) 0x0f , (byte) 0x0};
        byte[] returnWideCodes = {(byte)0x16 , (byte)0x0 , (byte) 0x0 , (byte) 0x0, (byte) 0x10 , (byte) 0x0};
        byte[] returnObjectCodes = {(byte)0x12 , (byte)0x0 , (byte) 0x11 , (byte) 0x0};
        switch (typeName){
            case "V":
                return returnVoidCodes;
            case "B":
            case "C":
            case "F":
            case "I":
            case "S":
            case "Z":
                return returnCodes;
            case "D":
            case "J":
                return returnWideCodes;
            default: {
                return returnObjectCodes;
            }
        }
    }

    /**
     * Write dex hashes
     */
    public static void writeHashes(File oldDexFile,File newDexFile){
        byte[] dexData = IoUtils.readFile(oldDexFile.getAbsolutePath());

        Dex dex = null;
        try {
            dex = new Dex(dexData);
            dex.writeHashes();
            dex.writeTo(newDexFile);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Restore dex code to dex
     */
    public static void restoreInstructions(File dexFile,List<Instruction> instructions) throws IOException {
        Dex dex = new Dex(dexFile);
        RandomAccessFile randomAccessFile = new RandomAccessFile(dexFile,"rw");
        Iterable<ClassDef> classDefs = dex.classDefs();
        int listIndex = 0;
        for (ClassDef classDef : classDefs) {
            ClassData.Method[] methods = dex.readClassData(classDef).allMethods();
            for(int i = 0; i < methods.length ;i++){
                ClassData.Method method = methods[i];
                int offsetInstructions = method.getCodeOffset() + 16;
                Instruction instruction = instructions.get(listIndex ++ );
                if(instruction.getMethodIndex() == method.getMethodIndex()) {
                    byte[] byteCode = Base64.getDecoder().decode(instruction.getInstructionsData());

                    randomAccessFile.seek(offsetInstructions);
                    randomAccessFile.write(byteCode,0,byteCode.length);
                }
            }
        }

        randomAccessFile.close();
    }

}
