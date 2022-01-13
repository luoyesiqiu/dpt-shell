package com.luoye.dpt.model;

import java.util.List;

/**
 * @author luoyesiqiu
 */
public class MultiDexCode {

    public short getVersion() {
        return version;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public short getDexCount() {
        return dexCount;
    }

    public void setDexCount(short dexCount) {
        this.dexCount = dexCount;
    }

    public List<Integer> getDexCodesIndex() {
        return dexCodesIndex;
    }

    public void setDexCodesIndex(List<Integer> dexCodesIndex) {
        this.dexCodesIndex = dexCodesIndex;
    }

    public List<DexCode> getDexCodes() {
        return dexCodes;
    }

    public void setDexCodes(List<DexCode> dexCodes) {
        this.dexCodes = dexCodes;
    }


    //版本号
    private short version;
    //dex的数量
    private short dexCount;
    //DexCode的索引，List长度为dex的数量
    private List<Integer> dexCodesIndex;
    //真实的DexCode，List长度为dex的数量
    private List<DexCode> dexCodes;

    @Override
    public String toString() {
        return "MultiDexCode{" +
                "version=" + version +
                ", dexCount=" + dexCount +
                ", dexCodesIndex=" + dexCodesIndex +
                ", dexCodes=" + dexCodes +
                '}';
    }
}
