package com.luoye.dpt.model;

import java.util.List;

/**
 * @author luoyesiqiu
 */
public class DexCode {

    public Short getMethodCount() {
        return methodCount;
    }

    public void setMethodCount(Short methodCount) {
        this.methodCount = methodCount;
    }

    public List<Integer> getInsnsIndex() {
        return insnsIndex;
    }

    public void setInsnsIndex(List<Integer> insnsIndex) {
        this.insnsIndex = insnsIndex;
    }

    public List<Instruction> getInsns() {
        return insns;
    }

    public void setInsns(List<Instruction> insns) {
        this.insns = insns;
    }

    //method count in dex
    private Short methodCount;
    //insns index list
    private List<Integer> insnsIndex;
    //insns list
    private List<Instruction> insns;

    @Override
    public String toString() {
        return "DexCode{" +
                "methodCount=" + methodCount +
                ", insnsIndex=" + insnsIndex +
                ", insns=" + insns +
                '}';
    }
}
