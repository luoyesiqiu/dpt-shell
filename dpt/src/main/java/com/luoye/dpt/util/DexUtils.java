package com.luoye.dpt.util;

import com.android.dex.ClassData;
import com.android.dex.ClassDef;
import com.android.dex.Code;
import com.android.dex.Dex;
import com.luoye.dpt.Global;
import com.luoye.dpt.model.Instruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * @author luoyesiqiu
 */
public class DexUtils {
    private static final Logger logger = LoggerFactory.getLogger(DexUtils.class.getSimpleName());
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
      "Lcom/xaiomi/.*",
      "Lcom/eclipsesource/.*",
      "Lcom/blankj/utilcode/.*",
      "Lcom/umeng/.*",
      "Ljavax/.*",
      "Lorg/slf4j/.*"
    };

    /**
     * 抽取所有方法的代码
     * @param dexFile 输入dex
     * @param outDexFile 输出dex
     * @return
     * @throws IOException
     */
    public static List<Instruction> extractAllMethods(File dexFile, File outDexFile) {
        List<Instruction> instructionList = new ArrayList<>();
        Dex dex = null;
        RandomAccessFile randomAccessFile = null;
        byte[] dexData = IoUtils.readFile(dexFile.getAbsolutePath());
        IoUtils.writeFile(outDexFile.getAbsolutePath(),dexData);

        String processedName = String.format("%s_processed_class.log", Global.packageName);
        File dealClassLogFile = new File(processedName);

        FileUtils.deleteRecurse(dealClassLogFile);

        try {
            dex = new Dex(dexFile);
            randomAccessFile = new RandomAccessFile(outDexFile, "rw");
            Iterable<ClassDef> classDefs = dex.classDefs();
            for (ClassDef classDef : classDefs) {
                boolean skip = false;
                //跳过系统类
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
                    String log = String.format("class '%s' data offset is zero",classDef.toString());
                    logger.warn(log);
                    continue;
                }

                ClassData classData = dex.readClassData(classDef);

                String className = dex.typeNames().get(classDef.getTypeIndex());
                String humanizeTypeName = TypeUtils.getHumanizeTypeName(className);

                ClassData.Method[] directMethods = classData.getDirectMethods();
                ClassData.Method[] virtualMethods = classData.getVirtualMethods();
                for (ClassData.Method method : directMethods) {
                    Instruction instruction = extractMethod(dex,randomAccessFile,classDef,method);
                    if(instruction != null) {
                        instructionList.add(instruction);
                    }
                }

                for (ClassData.Method method : virtualMethods) {
                    Instruction instruction = extractMethod(dex, randomAccessFile,classDef, method);
                    if(instruction != null) {
                        instructionList.add(instruction);
                    }
                }
                IoUtils.appendFile(dealClassLogFile.getAbsolutePath(),(humanizeTypeName + "\n").getBytes());
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            IoUtils.close(randomAccessFile);
        }

        return instructionList;
    }

    /**
     * 抽取单个方法的代码
     * @param dex dex文件
     * @param outRandomAccessFile 输出的文件
     * @param method 要抽取的方法
     * @return
     * @throws Exception
     */
    private static Instruction extractMethod(Dex dex ,RandomAccessFile outRandomAccessFile,ClassDef classDef,ClassData.Method method)
            throws Exception{
        String returnTypeName = dex.typeNames().get(dex.protoIds().get(dex.methodIds().get(method.getMethodIndex()).getProtoIndex()).getReturnTypeIndex());
        String methodName = dex.strings().get(dex.methodIds().get(method.getMethodIndex()).getNameIndex());
        String className = dex.typeNames().get(classDef.getTypeIndex());
        //native函数,abstract函数
        if(method.getCodeOffset() == 0){
            String log = String.format("method code offset is zero,name =  %s.%s , returnType = %s",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    TypeUtils.getHumanizeTypeName(returnTypeName));
            logger.warn(log);
            return null;
        }
        Instruction instruction = new Instruction();
        //16 = registers_size + ins_size + outs_size + tries_size + debug_info_off + insns_size
        int insnsOffset = method.getCodeOffset() + 16;
        Code code = dex.readCode(method);
        //容错处理
        if(code.getInstructions().length == 0){
            String log = String.format("method has no code,name =  %s.%s , returnType = %s",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    TypeUtils.getHumanizeTypeName(returnTypeName));
            logger.warn(log);
            return null;
        }
        int insnsCapacity = code.getInstructions().length;
        //insns容量不足以存放return语句，跳过
        byte[] returnByteCodes = getReturnByteCodes(returnTypeName);
        if(insnsCapacity * 2 < returnByteCodes.length){
            logger.warn("The capacity of insns is not enough to store the return statement. {}.{}() ClassIndex = {}-> {} insnsCapacity = {}byte(s) but returnByteCodes = {}byte(s)",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    classDef.getTypeIndex(),
                    TypeUtils.getHumanizeTypeName(returnTypeName),
                    insnsCapacity * 2,
                    returnByteCodes.length);

            return null;
        }
        instruction.setOffsetOfDex(insnsOffset);
        //这里的MethodIndex对应method_ids区的索引
        instruction.setMethodIndex(method.getMethodIndex());
        //注意：这里是数组的大小
        instruction.setInstructionDataSize(insnsCapacity * 2);
        byte[] byteCode = new byte[insnsCapacity * 2];
        //写入nop指令
        for (int i = 0; i < insnsCapacity; i++) {
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            byteCode[i * 2] = outRandomAccessFile.readByte();
            byteCode[i * 2 + 1] = outRandomAccessFile.readByte();
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            outRandomAccessFile.writeShort(0);
        }
        instruction.setInstructionsData(byteCode);
        outRandomAccessFile.seek(insnsOffset);
        //写出return语句
        outRandomAccessFile.write(returnByteCodes);

        return instruction;
    }

    /**
     * 根据类型缩写获取return语句的code
     * @param typeName
     * @return
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
     * 写入dex的校验数据
     * @param oldDexFile
     * @param newDexFile
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
     * 填充code到dex
     * @param dexFile 要填充的dex
     * @param instructions 指令数组
     * @throws IOException
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
