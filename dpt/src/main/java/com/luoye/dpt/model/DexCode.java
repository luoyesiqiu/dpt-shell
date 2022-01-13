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

    //dex函数数量
    private Short methodCount;
    //insns结构索引
    private List<Integer> insnsIndex;
    //insn列表
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
