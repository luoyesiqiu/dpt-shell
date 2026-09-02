package com.luoye.dpt.profile.model;

public class JniMethodMapping {
    private String semanticId;
    private String methodName;
    private String jvmSignature;

    public JniMethodMapping() {
    }

    public JniMethodMapping(String semanticId, String methodName, String jvmSignature) {
        this.semanticId = semanticId;
        this.methodName = methodName;
        this.jvmSignature = jvmSignature;
    }

    public String getSemanticId() {
        return semanticId;
    }

    public void setSemanticId(String semanticId) {
        this.semanticId = semanticId;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getJvmSignature() {
        return jvmSignature;
    }

    public void setJvmSignature(String jvmSignature) {
        this.jvmSignature = jvmSignature;
    }
}
