package com.luoye.dpt.profile.model;

import java.util.ArrayList;
import java.util.List;

public class ProfileContract {
    private int schemaVersion = ProfileSchemaVersions.PROFILE_CONTRACT;
    private int protocolVersion;
    private String generatorVersion;
    private String profileId;
    private String revisionId;
    private String applicationId;
    private List<String> abis = new ArrayList<>();
    private String proxyApplicationClassName;
    private String proxyComponentFactoryClassName;
    private String jniBridgeClassName;
    private List<JniMethodMapping> jniMethods = new ArrayList<>();
    private String soName;
    private String shellConfigAssetName;
    private String dexAssetName;
    private String codeItemAssetName;
    private String nativeLibAssetDirName;
    private String runtimeDirName;
    private String junkClassName;
    private String rc4KeySymbol;
    private String buildKey;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    public void setGeneratorVersion(String generatorVersion) {
        this.generatorVersion = generatorVersion;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public List<String> getAbis() {
        return abis;
    }

    public void setAbis(List<String> abis) {
        this.abis = abis;
    }

    public String getProxyApplicationClassName() {
        return proxyApplicationClassName;
    }

    public void setProxyApplicationClassName(String proxyApplicationClassName) {
        this.proxyApplicationClassName = proxyApplicationClassName;
    }

    public String getProxyComponentFactoryClassName() {
        return proxyComponentFactoryClassName;
    }

    public void setProxyComponentFactoryClassName(String proxyComponentFactoryClassName) {
        this.proxyComponentFactoryClassName = proxyComponentFactoryClassName;
    }

    public String getJniBridgeClassName() {
        return jniBridgeClassName;
    }

    public void setJniBridgeClassName(String jniBridgeClassName) {
        this.jniBridgeClassName = jniBridgeClassName;
    }

    public List<JniMethodMapping> getJniMethods() {
        return jniMethods;
    }

    public void setJniMethods(List<JniMethodMapping> jniMethods) {
        this.jniMethods = jniMethods;
    }

    public String getSoName() {
        return soName;
    }

    public void setSoName(String soName) {
        this.soName = soName;
    }

    public String getShellConfigAssetName() {
        return shellConfigAssetName;
    }

    public void setShellConfigAssetName(String shellConfigAssetName) {
        this.shellConfigAssetName = shellConfigAssetName;
    }

    public String getDexAssetName() {
        return dexAssetName;
    }

    public void setDexAssetName(String dexAssetName) {
        this.dexAssetName = dexAssetName;
    }

    public String getCodeItemAssetName() {
        return codeItemAssetName;
    }

    public void setCodeItemAssetName(String codeItemAssetName) {
        this.codeItemAssetName = codeItemAssetName;
    }

    public String getNativeLibAssetDirName() {
        return nativeLibAssetDirName;
    }

    public void setNativeLibAssetDirName(String nativeLibAssetDirName) {
        this.nativeLibAssetDirName = nativeLibAssetDirName;
    }

    public String getRuntimeDirName() {
        return runtimeDirName;
    }

    public void setRuntimeDirName(String runtimeDirName) {
        this.runtimeDirName = runtimeDirName;
    }

    public String getJunkClassName() {
        return junkClassName;
    }

    public void setJunkClassName(String junkClassName) {
        this.junkClassName = junkClassName;
    }

    public String getRc4KeySymbol() {
        return rc4KeySymbol;
    }

    public void setRc4KeySymbol(String rc4KeySymbol) {
        this.rc4KeySymbol = rc4KeySymbol;
    }

    public String getBuildKey() {
        return buildKey;
    }

    public void setBuildKey(String buildKey) {
        this.buildKey = buildKey;
    }
}
