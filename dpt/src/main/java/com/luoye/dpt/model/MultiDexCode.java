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


    //File version
    private short version;
    //Dex count
    private short dexCount;
    //Index of DexCode, the list size equals dex count
    private List<Integer> dexCodesIndex;
    //Real DexCode, the list size equals dex count
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
