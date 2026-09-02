package com.luoye.dpt.profile.contract;

import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.validation.ProfileValidationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that generated shell artifacts consume one Profile contract consistently.
 */
public final class ProfileCrossLayerContractValidator {

    private ProfileCrossLayerContractValidator() {
    }

    public static void validate(ProfileContract contract, Artifacts artifacts) {
        ProfileModelValidator.validate(contract);
        if (artifacts == null) {
            fail("artifacts", "must not be null");
        }

        validatePackager(contract, artifacts);
        validateJavaBridge(contract, artifacts);
        validateNativeRegistry(contract, artifacts);
        validateManifest(contract, artifacts);
        validateBuildConstants(contract, artifacts);
    }

    private static void validatePackager(ProfileContract contract, Artifacts artifacts) {
        requireEqual("packager.jniClassNameSig", toClassSignature(contract.getJniBridgeClassName()),
                artifacts.getPackagerJniClassNameSig());
        requireEqual("packager.staticInitializeMethodName", ProfileJniSemantic.STATIC_INITIALIZE
                        .requireMethodName(contract.getJniMethods()),
                artifacts.getPackagerStaticInitializeMethodName());
    }

    private static void validateJavaBridge(ProfileContract contract, Artifacts artifacts) {
        requireEqual("java.jniBridgeClassName", contract.getJniBridgeClassName(),
                artifacts.getJavaJniBridgeClassName());
        validateJniDeclarations("javaNativeMethods", contract.getJniMethods(),
                artifacts.getJavaNativeMethods());
    }

    private static void validateNativeRegistry(ProfileContract contract, Artifacts artifacts) {
        requireEqual("native.jniBridgeClassName", contract.getJniBridgeClassName(),
                artifacts.getNativeJniBridgeClassName());
        validateJniDeclarations("nativeRegisteredMethods", contract.getJniMethods(),
                artifacts.getNativeRegisteredMethods());
    }

    private static void validateManifest(ProfileContract contract, Artifacts artifacts) {
        requireEqual("manifest.proxyApplicationClassName", contract.getProxyApplicationClassName(),
                artifacts.getManifestApplicationClassName());
        requireEqual("manifest.proxyComponentFactoryClassName",
                contract.getProxyComponentFactoryClassName(),
                artifacts.getManifestComponentFactoryClassName());
        requireDexClass("manifest.proxyApplicationClassName", contract.getProxyApplicationClassName(),
                artifacts.getShellDexClassNames());
        requireDexClass("manifest.proxyComponentFactoryClassName",
                contract.getProxyComponentFactoryClassName(), artifacts.getShellDexClassNames());
    }

    private static void validateBuildConstants(ProfileContract contract, Artifacts artifacts) {
        requireEqual("buildConstants.soName", contract.getSoName(), artifacts.getSoName());
        requireEqual("buildConstants.shellConfigAssetName", contract.getShellConfigAssetName(),
                artifacts.getShellConfigAssetName());
        requireEqual("buildConstants.dexAssetName", contract.getDexAssetName(),
                artifacts.getDexAssetName());
        requireEqual("buildConstants.codeItemAssetName", contract.getCodeItemAssetName(),
                artifacts.getCodeItemAssetName());
        requireEqual("buildConstants.nativeLibAssetDirName", contract.getNativeLibAssetDirName(),
                artifacts.getNativeLibAssetDirName());
        requireEqual("buildConstants.runtimeDirName", contract.getRuntimeDirName(),
                artifacts.getRuntimeDirName());
        requireEqual("buildConstants.junkClassName", contract.getJunkClassName(),
                artifacts.getJunkClassName());
        requireEqual("buildConstants.rc4KeySymbol", contract.getRc4KeySymbol(),
                artifacts.getRc4KeySymbol());
        requireEqual("buildConstants.buildKey", contract.getBuildKey(), artifacts.getBuildKey());
    }

    private static void validateJniDeclarations(String layer, List<JniMethodMapping> expected,
                                                List<JniDeclaration> actual) {
        Map<String, String> actualByMethodName = new LinkedHashMap<>();
        if (actual == null) {
            fail(layer, "must not be null");
        }
        for (JniDeclaration declaration : actual) {
            if (declaration == null) {
                fail(layer, "must not contain null declarations");
            }
            if (declaration.getMethodName() == null || declaration.getJvmSignature() == null) {
                fail(layer, "JNI declaration method and signature must not be null");
            }
            if (actualByMethodName.containsKey(declaration.getMethodName())) {
                fail(layer, "duplicate JNI declaration method=" + declaration.getMethodName());
            }
            actualByMethodName.put(declaration.getMethodName(), declaration.getJvmSignature());
        }

        for (JniMethodMapping mapping : expected) {
            String actualSignature = actualByMethodName.remove(mapping.getMethodName());
            if (actualSignature == null) {
                fail(layer, "missing JNI declaration for semanticId=" + mapping.getSemanticId()
                        + ", expectedMethod=" + mapping.getMethodName()
                        + ", expectedSignature=" + mapping.getJvmSignature());
            }
            if (!mapping.getJvmSignature().equals(actualSignature)) {
                fail(layer, "JNI signature mismatch for semanticId=" + mapping.getSemanticId()
                        + ", expected=" + mapping.getJvmSignature()
                        + ", actual=" + actualSignature);
            }
        }

        if (!actualByMethodName.isEmpty()) {
            fail(layer, "unexpected JNI declaration method="
                    + actualByMethodName.keySet().iterator().next());
        }
    }

    private static void requireDexClass(String field, String className, Set<String> shellDexClassNames) {
        if (shellDexClassNames == null || !shellDexClassNames.contains(className)) {
            fail(field, "class is missing from shell dex: " + className);
        }
    }

    private static void requireEqual(String field, String expected, String actual) {
        if (!expected.equals(actual)) {
            fail(field, "expected " + expected + " but was " + actual);
        }
    }

    private static String toClassSignature(String className) {
        return "L" + className.replace('.', '/') + ";";
    }

    private static void fail(String field, String reason) {
        throw new ProfileValidationException(field + ": " + reason);
    }

    public static final class Artifacts {
        private String packagerJniClassNameSig;
        private String packagerStaticInitializeMethodName;
        private String javaJniBridgeClassName;
        private List<JniDeclaration> javaNativeMethods = Collections.emptyList();
        private String nativeJniBridgeClassName;
        private List<JniDeclaration> nativeRegisteredMethods = Collections.emptyList();
        private String manifestApplicationClassName;
        private String manifestComponentFactoryClassName;
        private Set<String> shellDexClassNames = Collections.emptySet();
        private String soName;
        private String shellConfigAssetName;
        private String dexAssetName;
        private String codeItemAssetName;
        private String nativeLibAssetDirName;
        private String runtimeDirName;
        private String junkClassName;
        private String rc4KeySymbol;
        private String buildKey;

        public String getPackagerJniClassNameSig() {
            return packagerJniClassNameSig;
        }

        public Artifacts setPackagerJniClassNameSig(String value) {
            packagerJniClassNameSig = value;
            return this;
        }

        public String getPackagerStaticInitializeMethodName() {
            return packagerStaticInitializeMethodName;
        }

        public Artifacts setPackagerStaticInitializeMethodName(String value) {
            packagerStaticInitializeMethodName = value;
            return this;
        }

        public String getJavaJniBridgeClassName() {
            return javaJniBridgeClassName;
        }

        public Artifacts setJavaJniBridgeClassName(String value) {
            javaJniBridgeClassName = value;
            return this;
        }

        public List<JniDeclaration> getJavaNativeMethods() {
            return javaNativeMethods;
        }

        public Artifacts setJavaNativeMethods(Collection<JniDeclaration> values) {
            javaNativeMethods = copyList(values);
            return this;
        }

        public String getNativeJniBridgeClassName() {
            return nativeJniBridgeClassName;
        }

        public Artifacts setNativeJniBridgeClassName(String value) {
            nativeJniBridgeClassName = value;
            return this;
        }

        public List<JniDeclaration> getNativeRegisteredMethods() {
            return nativeRegisteredMethods;
        }

        public Artifacts setNativeRegisteredMethods(Collection<JniDeclaration> values) {
            nativeRegisteredMethods = copyList(values);
            return this;
        }

        public String getManifestApplicationClassName() {
            return manifestApplicationClassName;
        }

        public Artifacts setManifestApplicationClassName(String value) {
            manifestApplicationClassName = value;
            return this;
        }

        public String getManifestComponentFactoryClassName() {
            return manifestComponentFactoryClassName;
        }

        public Artifacts setManifestComponentFactoryClassName(String value) {
            manifestComponentFactoryClassName = value;
            return this;
        }

        public Set<String> getShellDexClassNames() {
            return shellDexClassNames;
        }

        public Artifacts setShellDexClassNames(Collection<String> values) {
            shellDexClassNames = values == null ? null
                    : Collections.unmodifiableSet(new LinkedHashSet<>(values));
            return this;
        }

        public String getSoName() {
            return soName;
        }

        public Artifacts setSoName(String value) {
            soName = value;
            return this;
        }

        public String getShellConfigAssetName() {
            return shellConfigAssetName;
        }

        public Artifacts setShellConfigAssetName(String value) {
            shellConfigAssetName = value;
            return this;
        }

        public String getDexAssetName() {
            return dexAssetName;
        }

        public Artifacts setDexAssetName(String value) {
            dexAssetName = value;
            return this;
        }

        public String getCodeItemAssetName() {
            return codeItemAssetName;
        }

        public Artifacts setCodeItemAssetName(String value) {
            codeItemAssetName = value;
            return this;
        }

        public String getNativeLibAssetDirName() {
            return nativeLibAssetDirName;
        }

        public Artifacts setNativeLibAssetDirName(String value) {
            nativeLibAssetDirName = value;
            return this;
        }

        public String getRuntimeDirName() {
            return runtimeDirName;
        }

        public Artifacts setRuntimeDirName(String value) {
            runtimeDirName = value;
            return this;
        }

        public String getJunkClassName() {
            return junkClassName;
        }

        public Artifacts setJunkClassName(String value) {
            junkClassName = value;
            return this;
        }

        public String getRc4KeySymbol() {
            return rc4KeySymbol;
        }

        public Artifacts setRc4KeySymbol(String value) {
            rc4KeySymbol = value;
            return this;
        }

        public String getBuildKey() {
            return buildKey;
        }

        public Artifacts setBuildKey(String value) {
            buildKey = value;
            return this;
        }

        private static List<JniDeclaration> copyList(Collection<JniDeclaration> values) {
            return values == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    public static final class JniDeclaration {
        private final String methodName;
        private final String jvmSignature;

        public JniDeclaration(String methodName, String jvmSignature) {
            this.methodName = methodName;
            this.jvmSignature = jvmSignature;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getJvmSignature() {
            return jvmSignature;
        }
    }
}
