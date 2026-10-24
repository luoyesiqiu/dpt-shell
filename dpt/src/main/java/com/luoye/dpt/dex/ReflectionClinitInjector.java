package com.luoye.dpt.dex;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21s;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inject reflection-based JniBridge.clinit calls into static initializers.
 * Helper methods are generated in the caller's own class; when a dex reaches
 * the 65535 method limit, a same-class helper is reused if one already exists.
 */
public class ReflectionClinitInjector {

    private static final int MAX_METHODS_PER_DEX = 65535;

    private static final String CLASS_FOR_NAME = "Ljava/lang/Class;";
    private static final String METHOD_TYPE = "Ljava/lang/reflect/Method;";
    private static final String ACCESSIBLE_OBJECT = "Ljava/lang/reflect/AccessibleObject;";
    private static final String CLASS_ARRAY = "[Ljava/lang/Class;";
    private static final String OBJECT_ARRAY = "[Ljava/lang/Object;";
    private static final String CLINIT_METHOD_NAME = "clinit";

    private static final int REG_CLASS_NAME = 0;
    private static final int REG_METHOD_NAME = 1;
    private static final int REG_ARRAY = 2;
    private static final int REG_INDEX = 3;
    private static final int REG_KEY = 4;
    private static final int REG_LENGTH = 5;
    private static final int REG_TEMP = 6;

    private static final Set<String> SKIPPED_CLASS_TYPES = Set.of(
            "Landroidx/multidex/MultiDex;",
            "Lcom/android/support/multidex/MultiDex;"
    );

    private ReflectionClinitInjector() {
    }

    public static void inject(String inputDex, String outputDex, String jniClassSig) throws IOException {
        File inputFile = new File(inputDex);
        DexBackedDexFile dexFile = DexFileFactory.loadDexFile(inputFile, Opcodes.getDefault());

        String jniDotClassName = sigToDotName(jniClassSig);

        List<ClassDef> allClasses = new ArrayList<>();
        int totalMethodCount = 0;

        for (ClassDef classDef : dexFile.getClasses()) {
            allClasses.add(classDef);
            for (Method ignored : classDef.getMethods()) {
                totalMethodCount++;
            }
        }

        InjectState state = new InjectState(totalMethodCount, allClasses);

        // Pass 1: plan all helper methods and clinit->helper mappings before building classes.
        // Single-pass merge fails when a host class was already processed before its helper was scheduled.
        Map<String, List<Method>> helpersByHostClass = new HashMap<>();
        Map<String, ImmutableMethodReference> clinitClassToHelperRef = new HashMap<>();

        for (ClassDef classDef : allClasses) {
            if (isSkippedClass(classDef.getType())) {
                continue;
            }
            for (Method method : classDef.getMethods()) {
                if (isEligibleClinit(method)) {
                    ImmutableMethodReference helperRef = state.resolveHelper(
                            classDef.getType(), jniDotClassName, helpersByHostClass);
                    if (helperRef != null) {
                        clinitClassToHelperRef.put(classDef.getType(), helperRef);
                        state.clinitIndex++;
                    }
                }
            }
        }

        // Pass 2: inject calls and attach helpers to their host classes
        List<ClassDef> newClasses = new ArrayList<>();
        for (ClassDef classDef : allClasses) {
            List<Method> newMethods = new ArrayList<>();
            ImmutableMethodReference helperRef = clinitClassToHelperRef.get(classDef.getType());

            for (Method method : classDef.getMethods()) {
                if (helperRef != null && "<clinit>".equals(method.getName())) {
                    newMethods.add(injectHelperCall(method, helperRef));
                } else {
                    newMethods.add(method);
                }
            }

            List<Method> helpers = helpersByHostClass.get(classDef.getType());
            if (helpers != null) {
                newMethods.addAll(helpers);
            }

            newClasses.add(new ImmutableClassDefAdapter(classDef, newMethods).build());
        }

        DexFile outDex = new ImmutableDexFile(dexFile.getOpcodes(), newClasses);
        DexFileFactory.writeDexFile(outputDex, outDex);

        LogUtils.debug("reflection clinit inject: helpers=%d, clinits=%d, methods=%d",
                state.helperRefs.size(), state.clinitIndex, state.totalMethodCount);
    }

    private static boolean isSkippedClass(String classType) {
        return SKIPPED_CLASS_TYPES.contains(classType);
    }

    private static String sigToDotName(String jniClassSig) {
        String name = jniClassSig;
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.replace('/', '.');
    }

    private static boolean isEligibleClinit(Method method) {
        if (!"<clinit>".equals(method.getName())) {
            return false;
        }
        MethodImplementation implementation = method.getImplementation();
        if (implementation == null) {
            return false;
        }

        boolean hasFillArrayData = false;
        for (Instruction instruction : implementation.getInstructions()) {
            if (instruction.getOpcode() == Opcode.FILL_ARRAY_DATA) {
                hasFillArrayData = true;
                break;
            }
        }
        if (hasFillArrayData || !implementation.getTryBlocks().isEmpty()) {
            return false;
        }

        List<Instruction> instructions = toInstructionList(implementation.getInstructions());
        return !instructions.isEmpty()
                && instructions.get(instructions.size() - 1).getOpcode() == Opcode.RETURN_VOID;
    }

    private static List<Instruction> toInstructionList(Iterable<? extends Instruction> instructions) {
        List<Instruction> list = new ArrayList<>();
        for (Instruction instruction : instructions) {
            list.add(instruction);
        }
        return list;
    }

    private static Method injectHelperCall(Method method, ImmutableMethodReference helperRef) {
        MethodImplementation implementation = method.getImplementation();
        List<Instruction> originalInstructions = toInstructionList(implementation.getInstructions());
        List<Instruction> newInstructions = new ArrayList<>();

        ImmutableInstruction35c invokeHelper = new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                0, 0, 0, 0, 0, 0,
                helperRef
        );

        for (int i = 0; i < originalInstructions.size() - 1; i++) {
            newInstructions.add(originalInstructions.get(i));
        }
        newInstructions.add(invokeHelper);
        newInstructions.add(originalInstructions.get(originalInstructions.size() - 1));

        MethodImplementation newImplementation = new ImmutableMethodImplementation(
                implementation.getRegisterCount(),
                newInstructions,
                implementation.getTryBlocks(),
                implementation.getDebugItems()
        );

        return new ImmutableMethod(
                method.getDefiningClass(),
                method.getName(),
                method.getParameters(),
                method.getReturnType(),
                method.getAccessFlags(),
                method.getAnnotations(),
                method.getHiddenApiRestrictions(),
                newImplementation
        );
    }

    private static MethodImplementation buildReflectionImplementation(String jniDotClassName) {
        int key = StringXorCipher.randomKey();
        String encryptedClassName = StringXorCipher.encrypt(jniDotClassName, key);
        String encryptedMethodName = StringXorCipher.encrypt(CLINIT_METHOD_NAME, key);

        MutableMethodImplementation impl = new MutableMethodImplementation(7);

        impl.addInstruction(new BuilderInstruction21s(Opcode.CONST_16, REG_KEY, key));

        DexStringDecryptBuilder.emitDecrypt(impl, REG_CLASS_NAME, encryptedClassName,
                REG_ARRAY, REG_INDEX, REG_KEY, REG_LENGTH, REG_TEMP);
        DexStringDecryptBuilder.emitDecrypt(impl, REG_METHOD_NAME, encryptedMethodName,
                REG_ARRAY, REG_INDEX, REG_KEY, REG_LENGTH, REG_TEMP);

        impl.addInstruction(new BuilderInstruction35c(
                Opcode.INVOKE_STATIC, 1, REG_CLASS_NAME, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        CLASS_FOR_NAME, "forName",
                        Collections.singletonList("Ljava/lang/String;"),
                        CLASS_FOR_NAME)));
        impl.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, REG_CLASS_NAME));

        impl.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, REG_ARRAY, 0));
        impl.addInstruction(new BuilderInstruction22c(
                Opcode.NEW_ARRAY, REG_ARRAY, REG_ARRAY, new ImmutableTypeReference(CLASS_ARRAY)));
        impl.addInstruction(new BuilderInstruction35c(
                Opcode.INVOKE_VIRTUAL, 3, REG_CLASS_NAME, REG_METHOD_NAME, REG_ARRAY, 0, 0,
                new ImmutableMethodReference(
                        CLASS_FOR_NAME, "getDeclaredMethod",
                        List.of("Ljava/lang/String;", CLASS_ARRAY),
                        METHOD_TYPE)));
        impl.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, REG_CLASS_NAME));

        impl.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, REG_METHOD_NAME, 1));
        impl.addInstruction(new BuilderInstruction35c(
                Opcode.INVOKE_VIRTUAL, 2, REG_CLASS_NAME, REG_METHOD_NAME, 0, 0, 0,
                new ImmutableMethodReference(
                        ACCESSIBLE_OBJECT, "setAccessible",
                        Collections.singletonList("Z"),
                        "V")));

        impl.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, REG_METHOD_NAME, 0));
        impl.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, REG_ARRAY, 0));
        impl.addInstruction(new BuilderInstruction22c(
                Opcode.NEW_ARRAY, REG_ARRAY, REG_ARRAY, new ImmutableTypeReference(OBJECT_ARRAY)));
        impl.addInstruction(new BuilderInstruction35c(
                Opcode.INVOKE_VIRTUAL, 3, REG_CLASS_NAME, REG_METHOD_NAME, REG_ARRAY, 0, 0,
                new ImmutableMethodReference(
                        METHOD_TYPE, "invoke",
                        List.of("Ljava/lang/Object;", OBJECT_ARRAY),
                        "Ljava/lang/Object;")));
        impl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));

        return impl;
    }

    private static Method createHelperMethod(String hostClassType, String methodName, String jniDotClassName) {
        return new ImmutableMethod(
                hostClassType,
                methodName,
                Collections.emptyList(),
                "V",
                AccessFlags.PRIVATE.getValue() | AccessFlags.STATIC.getValue() | AccessFlags.SYNTHETIC.getValue(),
                null,
                null,
                buildReflectionImplementation(jniDotClassName)
        );
    }

    private static class InjectState {
        int totalMethodCount;
        final List<ImmutableMethodReference> helperRefs = new ArrayList<>();
        final Map<String, Set<String>> methodNamesByClass = new HashMap<>();
        int clinitIndex;

        InjectState(int totalMethodCount, List<ClassDef> allClasses) {
            this.totalMethodCount = totalMethodCount;
            for (ClassDef classDef : allClasses) {
                Set<String> names = new HashSet<>();
                for (Method method : classDef.getMethods()) {
                    names.add(method.getName());
                }
                methodNamesByClass.put(classDef.getType(), names);
            }
        }

        ImmutableMethodReference resolveHelper(String clinitClassType,
                                               String jniDotClassName,
                                               Map<String, List<Method>> helpersByHostClass) {
            if (totalMethodCount >= MAX_METHODS_PER_DEX) {
                for (ImmutableMethodReference ref : helperRefs) {
                    if (ref.getDefiningClass().equals(clinitClassType)) {
                        return ref;
                    }
                }
                return null;
            }

            String hostClass = clinitClassType;
            String methodName = generateUniqueMethodName(hostClass);

            Method helper = createHelperMethod(hostClass, methodName, jniDotClassName);
            helpersByHostClass.computeIfAbsent(hostClass, k -> new ArrayList<>()).add(helper);

            ImmutableMethodReference ref = new ImmutableMethodReference(
                    hostClass, methodName, Collections.emptyList(), "V");
            helperRefs.add(ref);
            totalMethodCount++;
            return ref;
        }

        private String generateUniqueMethodName(String hostClass) {
            Set<String> names = methodNamesByClass.computeIfAbsent(hostClass, k -> new HashSet<>());
            String name;
            do {
                name = StringUtils.generateIdentifier(4);
            } while (names.contains(name));
            names.add(name);
            return name;
        }
    }

    /**
     * Thin adapter to rebuild ClassDef without pulling in extra immutable imports at call site.
     */
    private static class ImmutableClassDefAdapter {
        private final ClassDef source;
        private final List<Method> methods;

        ImmutableClassDefAdapter(ClassDef source, List<Method> methods) {
            this.source = source;
            this.methods = methods;
        }

        ClassDef build() {
            return new com.android.tools.smali.dexlib2.immutable.ImmutableClassDef(
                    source.getType(),
                    source.getAccessFlags(),
                    source.getSuperclass(),
                    source.getInterfaces(),
                    source.getSourceFile(),
                    source.getAnnotations(),
                    source.getFields(),
                    methods
            );
        }
    }
}
