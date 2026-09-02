package com.luoye.dpt.profile.contract;

import com.luoye.dpt.profile.model.JniMethodMapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum ProfileJniSemantic {
    CALL_REAL_APPLICATION_ON_CREATE(
            "CALL_REAL_APPLICATION_ON_CREATE", "(Ljava/lang/String;)V"),
    INIT_APP("INIT_APP", "()V"),
    GET_SOURCE_DIR("GET_SOURCE_DIR", "()Ljava/lang/String;"),
    GET_COMPRESSED_DEXES_PATH("GET_COMPRESSED_DEXES_PATH", "()Ljava/lang/String;"),
    READ_APP_COMPONENT_FACTORY("READ_APP_COMPONENT_FACTORY", "()Ljava/lang/String;"),
    READ_APPLICATION_NAME("READ_APPLICATION_NAME", "()Ljava/lang/String;"),
    COMBINE_DEX_ELEMENTS("COMBINE_DEX_ELEMENTS", "(Ljava/lang/ClassLoader;)V"),
    REMOVE_DEX_ELEMENTS(
            "REMOVE_DEX_ELEMENTS", "(Ljava/lang/ClassLoader;Ljava/lang/String;)V"),
    REPLACE_APPLICATION(
            "REPLACE_APPLICATION", "(Ljava/lang/String;)Ljava/lang/Object;"),
    STATIC_INITIALIZE("STATIC_INITIALIZE", "()V");

    private static final Map<String, ProfileJniSemantic> BY_SEMANTIC_ID = buildIndex();

    private final String semanticId;
    private final String jvmSignature;

    ProfileJniSemantic(String semanticId, String jvmSignature) {
        this.semanticId = semanticId;
        this.jvmSignature = jvmSignature;
    }

    public String getSemanticId() {
        return semanticId;
    }

    public String getJvmSignature() {
        return jvmSignature;
    }

    public String requireMethodName(List<JniMethodMapping> mappings) {
        if (mappings != null) {
            for (JniMethodMapping mapping : mappings) {
                if (semanticId.equals(mapping.getSemanticId())) {
                    if (!jvmSignature.equals(mapping.getJvmSignature())) {
                        throw new IllegalStateException("JNI mapping signature mismatch for " + semanticId);
                    }
                    return mapping.getMethodName();
                }
            }
        }
        throw new IllegalStateException("JNI mapping is missing for " + semanticId);
    }

    public static ProfileJniSemantic fromSemanticId(String semanticId) {
        return BY_SEMANTIC_ID.get(semanticId);
    }

    private static Map<String, ProfileJniSemantic> buildIndex() {
        Map<String, ProfileJniSemantic> result = new LinkedHashMap<>();
        for (ProfileJniSemantic semantic : values()) {
            ProfileJniSemantic previous = result.put(semantic.semanticId, semantic);
            if (previous != null) {
                throw new IllegalStateException("duplicate JNI semantic ID: " + semantic.semanticId);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
