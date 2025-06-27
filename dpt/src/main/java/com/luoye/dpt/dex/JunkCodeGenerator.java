package com.luoye.dpt.dex;

import com.android.dx.Code;
import com.android.dx.DexMaker;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.TypeId;
import com.luoye.dpt.util.LogUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @author luoyesiqiu
 */
public class JunkCodeGenerator {
    private static final String BASE_CLASS_NAME = "com/luoye/dpt/junkcode/JunkClass";
    private static final int MAX_GENERATE_COUNT = 100;
    private static final Set<String> classNameSet = new HashSet<>();

    private static void insertSystemExit(Code code, boolean returnVoid) {
        TypeId<System> systemType = TypeId.get(System.class);

        MethodId<System, Void> exit = systemType.getMethod(TypeId.VOID, "exit", TypeId.INT);

        Local<Integer> exitCode = code.newLocal(TypeId.INT);
        code.loadConstant(exitCode, 0);

        code.invokeStatic(exit, null, exitCode);
        if(returnVoid) {
            code.returnVoid();
        }
    }

    private static void insertNullExceptionCode(Code code) {
        TypeId<NullPointerException> nullPointerExceptionTypeId = TypeId.get(NullPointerException.class);
        Local<NullPointerException> throwableLocal = code.newLocal(nullPointerExceptionTypeId);
        MethodId<NullPointerException, Void> constructor = nullPointerExceptionTypeId.getConstructor();
        code.newInstance(throwableLocal, constructor);
        code.throwValue(throwableLocal);
    }

    private static String generateIdentifier() {
        SecureRandom secureRandom = new SecureRandom();
        final int cnt = secureRandom.nextInt(2) + 3;
        StringBuilder sb = new StringBuilder();
        for(int i = 0;i < cnt;i++) {
            char baseChar = secureRandom.nextBoolean() ? 'A' : 'a';

            int index = secureRandom.nextInt(26);
            char ch = (char) (baseChar + index);
            sb.append(ch);
        }

        return sb.toString();
    }

    private static String generateBaseClassName() {

        return String.format(Locale.US, "L%s;", BASE_CLASS_NAME);
    }

    private static String generateClassName() {
        SecureRandom secureRandom = new SecureRandom();
        int number = secureRandom.nextInt() % (MAX_GENERATE_COUNT * 10);

        return String.format(Locale.US, "L%s%d;", BASE_CLASS_NAME, number);
    }

    public static void generateJunkCodeDex(File file) throws IOException {
        SecureRandom secureRandom = new SecureRandom();
        final int generateClassCount = secureRandom.nextInt(MAX_GENERATE_COUNT / 2) + (MAX_GENERATE_COUNT / 2);

        DexMaker dexMaker = new DexMaker();

        for(int i = 0;i < generateClassCount;i++) {

            String className;
            if(i == 0) {
                className = generateBaseClassName();
            }
            else {
                do {
                    className = generateClassName();
                }
                while(classNameSet.contains(className));
                classNameSet.add(className);
            }

            TypeId<?> typeId = TypeId.get(className);
            dexMaker.declare(typeId, "", Modifier.PUBLIC, TypeId.OBJECT);

            // generate static code black
            MethodId<?, Void> clinitMethod = typeId.getMethod(TypeId.VOID, "<clinit>");
            Code clinitCode = dexMaker.declare(clinitMethod, Modifier.STATIC);
            insertSystemExit(clinitCode, false);

            // generate constructor
            MethodId<?, Void> initMethod = typeId.getConstructor();
            Code initCode = dexMaker.declare(initMethod, Modifier.PUBLIC);
            insertSystemExit(initCode, true);

            // generate normal method
            int methodCount = secureRandom.nextInt(2) + 2;
            for (int j = 0; j < methodCount; j++) {
                String methodName = generateIdentifier();

                MethodId<?, Void> randomMethod = typeId.getMethod(TypeId.VOID, methodName);
                Code randomMethodCode = dexMaker.declare(randomMethod, Modifier.PUBLIC);
                if(j % 2 == 0) {
                    insertSystemExit(randomMethodCode, true);
                }
                else {
                    insertNullExceptionCode(randomMethodCode);
                }

            }
        }

        byte[] generate = dexMaker.generate();
        Files.write(Paths.get(file.getAbsolutePath()), generate);
        LogUtils.info("generated junk class count: %d", generateClassCount);

    }
}