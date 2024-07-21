package com.luoye.dpt.builder;

abstract class AndroidPackage {

    protected static abstract class Builder {
        public String filePath = null;
        public String packageName = null;
        public boolean debuggable = false;
        public boolean sign = true;
        public boolean appComponentFactory = true;

        public abstract AndroidPackage build();
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    public boolean isSign() {
        return sign;
    }

    public void setSign(boolean sign) {
        this.sign = sign;
    }

    public boolean isAppComponentFactory() {
        return appComponentFactory;
    }

    public void setAppComponentFactory(boolean appComponentFactory) {
        this.appComponentFactory = appComponentFactory;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String filePath = null;

    public String packageName = null;
    public boolean debuggable = false;
    public boolean sign = true;
    public boolean appComponentFactory = true;
}
