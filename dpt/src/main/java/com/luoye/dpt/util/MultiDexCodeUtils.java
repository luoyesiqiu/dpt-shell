package com.luoye.dpt.util;

import com.luoye.dpt.Const;
import com.luoye.dpt.model.DexCode;
import com.luoye.dpt.model.Instruction;
import com.luoye.dpt.model.MultiDexCode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * @author luoyesiqiu
 */
public class MultiDexCodeUtils {

    /**
     * 生成MultiDexCode结构
     */
    public static MultiDexCode makeMultiDexCode(List<List<Instruction>> multiDexInsns){
        int fileOffset = 0;
        MultiDexCode multiDexCode = new MultiDexCode();
        multiDexCode.setVersion(Const.MULTI_DEX_CODE_VERSION);
        fileOffset += 2;
        multiDexCode.setDexCount((short) multiDexInsns.size());
        fileOffset += 2;
        List<Integer> dexCodeIndex = new ArrayList<>();
        multiDexCode.setDexCodesIndex(dexCodeIndex);
        fileOffset += 4 * multiDexInsns.size();

        List<DexCode> dexCodeList = new ArrayList<>();
        List<Integer> insnsIndexList = new ArrayList<>();

        for (List<Instruction> insns : multiDexInsns) {
            System.out.println("DexCode offset = " + fileOffset);
            dexCodeIndex.add(fileOffset);
             DexCode dexCode = new DexCode();

            dexCode.setMethodCount((short)insns.size());
            fileOffset += 2;
            dexCode.setInsns(insns);

            insnsIndexList.add(fileOffset);

            dexCode.setInsnsIndex(insnsIndexList);

            for (Instruction ins : insns) {
                fileOffset += 4; //Instruction.offsetOfDex
                fileOffset += 4; //Instruction.methodIndex
                fileOffset += 4; //Instruction.instructionDataSize
                fileOffset += ins.getInstructionsData().length; //Instruction.instructionsData
            }

            dexCodeList.add(dexCode);
        }
        System.out.println("fileOffset = " + fileOffset);

        multiDexCode.setDexCodes(dexCodeList);

        return multiDexCode;
    }


    /**
     * 写出MultiDexCode结构
     * @param multiDexCode MultiDexCode结构
     */
    public static void writeMultiDexCode(String out, MultiDexCode multiDexCode){
        if(multiDexCode.getDexCodes().isEmpty()){
            return;
        }
        RandomAccessFile randomAccessFile = null;

        try {
            randomAccessFile = new RandomAccessFile(out, "rw");
            //写入版本号
            randomAccessFile.write(Endian.makeLittleEndian(multiDexCode.getVersion()));
            //写入dex数量
            randomAccessFile.write(Endian.makeLittleEndian(multiDexCode.getDexCount()));

            //写入每个dex在文件中的位置
            for (Integer dexCodesIndex : multiDexCode.getDexCodesIndex()) {
                randomAccessFile.write(Endian.makeLittleEndian(dexCodesIndex));
            }
            //写入每个dex的数据
            for (DexCode dexCode : multiDexCode.getDexCodes()) {
                List<Instruction> insns = dexCode.getInsns();
                System.out.println("insns item count:" + insns.size() + ",method count : " + dexCode.getMethodCount());
                //写入单个dex的函数数量
                randomAccessFile.write(Endian.makeLittleEndian(dexCode.getMethodCount()));
                for (int i = 0; i < insns.size(); i++) {
                    Instruction instruction = insns.get(i);
                    randomAccessFile.write(Endian.makeLittleEndian(instruction.getMethodIndex()));
                    randomAccessFile.write(Endian.makeLittleEndian(instruction.getOffsetOfDex()));
                    randomAccessFile.write(Endian.makeLittleEndian(instruction.getInstructionDataSize()));
                    randomAccessFile.write(instruction.getInstructionsData());
//                    System.out.println("wrote = " + instruction);
                }
            }

        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
