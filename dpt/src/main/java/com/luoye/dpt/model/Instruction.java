package com.luoye.dpt.model;

import java.util.Arrays;

/**
 * @author luoyesiqiu
 */
public class Instruction {

    public int getOffsetOfDex() {
        return offsetOfDex;
    }

    public void setOffsetOfDex(int offsetOfDex) {
        this.offsetOfDex = offsetOfDex;
    }

    public int getMethodIndex() {
        return methodIndex;
    }

    public void setMethodIndex(int methodIndex) {
        this.methodIndex = methodIndex;
    }

    public int getInstructionDataSize() {
        return instructionDataSize;
    }

    public void setInstructionDataSize(int instructionDataSize) {
        this.instructionDataSize = instructionDataSize;
    }

    public byte[] getInstructionsData() {
        return instructionsData;
    }

    public void setInstructionsData(byte[] instructionsData) {
        this.instructionsData = instructionsData;
    }

    @Override
    public String toString() {
        return "Instruction{" +
                "offsetOfDex=" + offsetOfDex +
                ", methodIndex=" + methodIndex +
                ", instructionDataSize=" + instructionDataSize +
                ", instructionsData=" + Arrays.toString(instructionsData) +
                '}';
    }

    //在dex中的偏移
    private int offsetOfDex;
    //对应dex中的method_idx
    private int methodIndex;
    //instructionsData数组的长度
    private int instructionDataSize;
    //指令长度
    private byte[] instructionsData;
}
