package com.luoye.dpt.dex;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.luoye.dpt.config.Const;
import com.luoye.dpt.config.ShellConfig;
import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.util.DexUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReflectionClinitInjectorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void resetGlobalShellConfig() {
        ShellConfig.getInstance().init(Const.SHELL_PACKAGE_NAME_AUTO);
    }

    @Test
    public void injectsProfileSpecificBridgeAndStaticInitializeMethodIntoBusinessDex()
            throws IOException {
        File inputDex = new File(temporaryFolder.getRoot(), "input.dex");
        File outputDex = new File(temporaryFolder.getRoot(), "output.dex");
        writeDexWithEligibleClinit(inputDex);

        DexUtils.injectInvokeMethod(inputDex.getAbsolutePath(), outputDex.getAbsolutePath(),
                "Lp/profile/Bridge;", "mProfileStaticInit");

        Assert.assertEquals(Arrays.asList("p.profile.Bridge", "mProfileStaticInit"),
                decryptedReflectionTarget(outputDex));
    }

    @Test
    public void profileContractDrivesBusinessDexInjection() throws IOException {
        ShellConfig config = ShellConfig.getInstance();
        config.init(Const.SHELL_PACKAGE_NAME_AUTO);
        config.applyProfileContract(profileContract());

        File inputDex = new File(temporaryFolder.getRoot(), "profile-input.dex");
        File outputDex = new File(temporaryFolder.getRoot(), "profile-output.dex");
        writeDexWithEligibleClinit(inputDex);

        DexUtils.injectInvokeMethod(inputDex.getAbsolutePath(), outputDex.getAbsolutePath(),
                config.getJniClassNameSig(), config.getJniStaticInitializeMethodName());

        Assert.assertEquals(Arrays.asList("p.profile.Bridge", "mProfileStaticInit"),
                decryptedReflectionTarget(outputDex));
    }

    @Test
    public void legacyInjectionOverloadKeepsTheExistingStaticInitializeMethodName() throws IOException {
        File inputDex = new File(temporaryFolder.getRoot(), "legacy-input.dex");
        File outputDex = new File(temporaryFolder.getRoot(), "legacy-output.dex");
        writeDexWithEligibleClinit(inputDex);

        DexUtils.injectInvokeMethod(inputDex.getAbsolutePath(), outputDex.getAbsolutePath(),
                "Lp/legacy/Bridge;");

        Assert.assertEquals(Arrays.asList("p.legacy.Bridge", "clinit"),
                decryptedReflectionTarget(outputDex));
    }

    private static void writeDexWithEligibleClinit(File outputDex) throws IOException {
        ImmutableMethod clinit = new ImmutableMethod(
                "Lsample/Target;",
                "<clinit>",
                Collections.emptyList(),
                "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                null,
                null,
                new ImmutableMethodImplementation(0,
                        Collections.singletonList(new ImmutableInstruction10x(Opcode.RETURN_VOID)),
                        null,
                        null));
        ImmutableClassDef targetClass = new ImmutableClassDef(
                "Lsample/Target;",
                AccessFlags.PUBLIC.getValue(),
                "Ljava/lang/Object;",
                Collections.emptyList(),
                null,
                null,
                Collections.emptyList(),
                Collections.singletonList(clinit));
        DexFileFactory.writeDexFile(outputDex.getAbsolutePath(),
                new ImmutableDexFile(Opcodes.getDefault(), Collections.singletonList(targetClass)));
    }

    private static List<String> decryptedReflectionTarget(File outputDex) throws IOException {
        DexBackedDexFile dexFile = DexFileFactory.loadDexFile(outputDex, Opcodes.getDefault());
        Method helper = null;
        for (ClassDef classDef : dexFile.getClasses()) {
            for (Method method : classDef.getMethods()) {
                if (!"<clinit>".equals(method.getName()) && method.getImplementation() != null) {
                    helper = method;
                    break;
                }
            }
        }
        Assert.assertNotNull("injection should add a reflection helper", helper);

        Integer key = null;
        List<String> encryptedValues = new ArrayList<>();
        for (Instruction instruction : helper.getImplementation().getInstructions()) {
            if (instruction.getOpcode() == Opcode.CONST_16
                    && instruction instanceof NarrowLiteralInstruction) {
                key = ((NarrowLiteralInstruction) instruction).getNarrowLiteral();
            }
            if (instruction instanceof ReferenceInstruction) {
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                if (reference instanceof StringReference) {
                    encryptedValues.add(((StringReference) reference).getString());
                }
            }
        }
        Assert.assertNotNull("reflection helper should contain its XOR key", key);
        Assert.assertEquals("reflection helper should encrypt class and method names", 2,
                encryptedValues.size());

        List<String> values = new ArrayList<>(encryptedValues.size());
        for (String encryptedValue : encryptedValues) {
            values.add(decrypt(encryptedValue, key));
        }
        return values;
    }

    private static String decrypt(String value, int key) {
        char[] chars = value.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            chars[index] = (char) (chars[index] ^ key);
        }
        return new String(chars);
    }

    private static ProfileContract profileContract() {
        ProfileContract value = new ProfileContract();
        value.setSchemaVersion(ProfileSchemaVersions.PROFILE_CONTRACT);
        value.setProtocolVersion(ProfileSchemaVersions.PROFILE_PROTOCOL);
        value.setGeneratorVersion("generator-1");
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        value.setApplicationId("com.example.alpha");
        value.setAbis(Collections.singletonList("arm64-v8a"));
        value.setProxyApplicationClassName("p.profile.App");
        value.setProxyComponentFactoryClassName("p.profile.Factory");
        value.setJniBridgeClassName("p.profile.Bridge");
        value.setJniMethods(jniMappings());
        value.setSoName("libprofile.so");
        value.setShellConfigAssetName("config");
        value.setDexAssetName("dex.zip");
        value.setCodeItemAssetName("code");
        value.setNativeLibAssetDirName("native");
        value.setRuntimeDirName("runtime");
        value.setJunkClassName("p.profile.Junk");
        value.setRc4KeySymbol("profile_key");
        value.setBuildKey("0123456789abcdef");
        return value;
    }

    private static List<JniMethodMapping> jniMappings() {
        List<JniMethodMapping> mappings = new ArrayList<>();
        int index = 0;
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            String methodName = semantic == ProfileJniSemantic.STATIC_INITIALIZE
                    ? "mProfileStaticInit"
                    : "m" + index;
            mappings.add(new JniMethodMapping(semantic.getSemanticId(), methodName,
                    semantic.getJvmSignature()));
            index++;
        }
        return mappings;
    }
}
