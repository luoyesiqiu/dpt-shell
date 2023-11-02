package com.luoye.dpt.util;

import com.android.dex.ClassData;
import com.android.dex.ClassDef;
import com.android.dex.Code;
import com.android.dex.Dex;
import com.luoye.dpt.model.Instruction;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * @author luoyesiqiu
 */
public class DexUtils {
    /* The following are the rules for classes that do not extract */
    private static final String[] excludeRule = {
      "Landroid/.*",
      "Landroidx/.*",
      "Lcom/squareup/okhttp/.*",
      "Lokio/.*", "Lokhttp3/.*",
      "Lkotlin/.*",
      "Lcom/google/.*",
      "Lrx/.*",
      "Lorg/apache/.*",
      "Lretrofit2/.*",
      "Lcom/alibaba/.*",
      "Lcom/amap/api/.*",
      "Lcom/sina/weibo/.*",
      "Lcom/xiaomi/.*",
      "Lcom/eclipsesource/.*",
      "Lcom/blankj/utilcode/.*",
      "Lcom/umeng/.*",
      "Ljavax/.*",
      "Lorg/slf4j/.*"
    };

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
            randomAccessFile = new RandomAccessFile(outDexFile, "rw");
            Iterable<ClassDef> classDefs = dex.classDefs();
            for (ClassDef classDef : classDefs) {
                boolean skip = false;
                //Skip exclude classes name
                for(String rule : excludeRule){
                    if(classDef.toString().matches(rule)){
                        skip = true;
                        break;
                    }
                }
                if(skip){
                    continue;
                }
                if(classDef.getClassDataOffset() == 0){
                    LogUtils.noisy("class '%s' data offset is zero",classDef.toString());
                    continue;
                }

                JSONObject classJSONObject = new JSONObject();
                JSONArray classJSONArray = new JSONArray();
                ClassData classData = dex.readClassData(classDef);

                String className = dex.typeNames().get(classDef.getTypeIndex());
                String humanizeTypeName = TypeUtils.getHumanizeTypeName(className);

                ClassData.Method[] directMethods = classData.getDirectMethods();
                ClassData.Method[] virtualMethods = classData.getVirtualMethods();
                for (ClassData.Method method : directMethods) {
                    Instruction instruction = extractMethod(dex,randomAccessFile,classDef,method);
                    if(instruction != null) {
                        instructionList.add(instruction);
                        putToJSON(classJSONArray, instruction);
                    }
                }

                for (ClassData.Method method : virtualMethods) {
                    Instruction instruction = extractMethod(dex, randomAccessFile,classDef, method);
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
        //16 = registers_size + ins_size + outs_size + tries_size + debug_info_off + insns_size
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
        instruction.setOffsetOfDex(insnsOffset);
        //Here, MethodIndex corresponds to the index of the method_ids area
        instruction.setMethodIndex(method.getMethodIndex());
        //Note: Here is the size of the array
        instruction.setInstructionDataSize(insnsCapacity * 2);
        byte[] byteCode = new byte[insnsCapacity * 2];
        //Write nop instruction
        for (int i = 0; i < insnsCapacity; i++) {
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            byteCode[i * 2] = outRandomAccessFile.readByte();
            byteCode[i * 2 + 1] = outRandomAccessFile.readByte();
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            outRandomAccessFile.writeShort(0);
        }
        instruction.setInstructionsData(byteCode);
        outRandomAccessFile.seek(insnsOffset);
        //Write return instruction
        outRandomAccessFile.write(returnByteCodes);

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
