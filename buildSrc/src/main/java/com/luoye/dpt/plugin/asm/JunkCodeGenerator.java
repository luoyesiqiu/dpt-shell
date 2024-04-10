package com.luoye.dpt.plugin.asm;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ATHROW;
import static groovyjarjarasm.asm.Opcodes.V1_8;

import com.luoye.dpt.plugin.asm.util.LogUtils;

import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public class JunkCodeGenerator {

    private static final Set<String> identifierSet = new HashSet<>();
    public static void generate(File dir) throws IOException {
        generateClass(dir);
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

    private static void generateClass(File dir) throws IOException {
        SecureRandom secureRandom = new SecureRandom();
        final int generateClassCount = secureRandom.nextInt(10) + 10;

        LogUtils.debug("generate class count: %d",generateClassCount);
        for(int i = 0;i < generateClassCount;i++) {
            String className = generateIdentifier();
            if(identifierSet.contains(className)){
                className = generateIdentifier();
            }
            identifierSet.add(className);

            ClassWriter classWriter = new ClassWriter(0);
            classWriter.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);

            // generate static code black
            MethodVisitor classLoaderMethodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            insertExitCode(classLoaderMethodVisitor);
            insertReturnCode(classLoaderMethodVisitor);

            // generate constructor
            MethodVisitor constructorMethodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            insertExitCode(constructorMethodVisitor);
            insertReturnCode(constructorMethodVisitor);

            // generate normal method
            int methodCount = secureRandom.nextInt(2) + 2;
            for (int j = 0; j < methodCount; j++) {
                String methodName = generateIdentifier();

                MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, methodName, "()V", null, null);
                if(j % 2 == 0) {
                    insertNullExceptionCode(methodVisitor);
                }
                else {
                    insertExitCode(methodVisitor);
                }
                insertReturnCode(methodVisitor);

            }

            classWriter.visitEnd();

            //write to file
            byte[] byteCode = classWriter.toByteArray();
            FileUtils.writeByteArrayToFile(new File(dir,className + ".class"),byteCode);
        }
    }

    private static void insertExitCode(MethodVisitor methodVisitor) {
        methodVisitor.visitCode();
        methodVisitor.visitMaxs(4,1);
        methodVisitor.visitLdcInsn(0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/System",
                "exit","(I)V",
                false);
    }

    private static void insertNullExceptionCode(MethodVisitor methodVisitor) {
        methodVisitor.visitCode();
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/lang/NullPointerException");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "()V", false);
        methodVisitor.visitInsn(ATHROW);
    }

    private static void insertReturnCode(MethodVisitor methodVisitor) {
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitEnd();
    }

}
