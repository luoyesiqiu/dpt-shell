package com.luoye.dpt.util;

import com.luoye.dpt.Const;
import com.luoye.dpt.model.DexCode;
import com.luoye.dpt.model.Instruction;
import com.luoye.dpt.model.MultiDexCode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author luoyesiqiu
 */
public class MultiDexCodeUtils {

    public static MultiDexCode makeMultiDexCode(Map<Integer, List<Instruction>> multiDexInsns) {
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
        Iterator<Map.Entry<Integer, List<Instruction>>> iterator = multiDexInsns.entrySet()
                                                                                .iterator();
        while (iterator.hasNext()) {
            List<Instruction> insns = iterator.next()
                                              .getValue();
            LogUtils.info("DexCode offset = " + fileOffset);
            if (insns == null) {
                continue;
            }
            dexCodeIndex.add(fileOffset);
            DexCode dexCode = new DexCode();

            dexCode.setMethodCount((short) insns.size());
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
        LogUtils.info("fileOffset = " + fileOffset);

        multiDexCode.setDexCodes(dexCodeList);

        return multiDexCode;
    }

    /**
     * Write MultiDexCode struct to file
     */
    public static void writeMultiDexCode(String out, MultiDexCode multiDexCode) {
        if (multiDexCode.getDexCodes()
                        .isEmpty()) {
            return;
        }
        RandomAccessFile randomAccessFile = null;

        try {
            randomAccessFile = new RandomAccessFile(out, "rw");
            //Write file version
            randomAccessFile.write(Endian.makeLittleEndian(multiDexCode.getVersion()));
            //Write dex count
            randomAccessFile.write(Endian.makeLittleEndian(multiDexCode.getDexCount()));

            //Write to the location of each dex in the file
            for (Integer dexCodesIndex : multiDexCode.getDexCodesIndex()) {
                randomAccessFile.write(Endian.makeLittleEndian(dexCodesIndex));
            }
            //Write data for each dex
            for (DexCode dexCode : multiDexCode.getDexCodes()) {
                List<Instruction> insns = dexCode.getInsns();

                int methodCount = dexCode.getMethodCount() & 0xFFFF;

                LogUtils.info("insns item count:" + insns.size() + ",method count : " + methodCount);
                //The number of functions that are written to a single dex
                randomAccessFile.write(Endian.makeLittleEndian(dexCode.getMethodCount()));
                for (int i = 0; i < insns.size(); i++) {
                    Instruction instruction = insns.get(i);
                    randomAccessFile.write(Endian.makeLittleEndian(instruction.getMethodIndex()));
                    randomAccessFile.write(Endian.makeLittleEndian(instruction.getOffsetOfDex()));
                    randomAccessFile.write(Endian.makeLittleEndian(instruction.getInstructionDataSize()));
                    randomAccessFile.write(instruction.getInstructionsData());
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IoUtils.close(randomAccessFile);
        }
    }
}
