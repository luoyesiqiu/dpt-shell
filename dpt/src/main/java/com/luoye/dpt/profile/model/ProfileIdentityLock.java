package com.luoye.dpt.profile.model;

import java.util.ArrayList;
import java.util.List;

public class ProfileIdentityLock {
    private int schemaVersion = ProfileSchemaVersions.IDENTITY_LOCK;
    private String profileId;
    private String applicationId;
    private String identitySeed;
    private String shellPackageName;
    private String proxyApplicationClassName;
    private String proxyComponentFactoryClassName;
    private String jniBridgeClassName;
    private String soName;
    private String shellConfigAssetName;
    private String dexAssetName;
    private String codeItemAssetName;
    private String nativeLibAssetDirName;
    private String runtimeDirName;
    private String junkClassName;
    private String rc4KeySymbol;
    private String buildKey;
    private List<JniMethodMapping> jniMethods = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getIdentitySeed() {
        return identitySeed;
    }

    public void setIdentitySeed(String identitySeed) {
        this.identitySeed = identitySeed;
    }

    public String getShellPackageName() {
        return shellPackageName;
    }

    public void setShellPackageName(String shellPackageName) {
        this.shellPackageName = shellPackageName;
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

    public List<JniMethodMapping> getJniMethods() {
        return jniMethods;
    }

    public void setJniMethods(List<JniMethodMapping> jniMethods) {
        this.jniMethods = jniMethods;
    }
}
