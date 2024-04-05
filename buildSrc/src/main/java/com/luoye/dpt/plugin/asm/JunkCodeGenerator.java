package com.luoye.dpt.plugin.asm;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.V1_8;

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
        SecureRandom rd = new SecureRandom();
        final int cnt = rd.nextInt(2) + 3;
        StringBuilder sb = new StringBuilder();
        for(int i = 0;i < cnt;i++) {
            char baseChar = rd.nextBoolean() ? 'A' : 'a';

            int index = rd.nextInt(26);
            char ch = (char) (baseChar + index);
            sb.append(ch);
        }

        return sb.toString();
    }

    private static void generateClass(File dir) throws IOException {
        SecureRandom rd = new SecureRandom();
        final int generateClassCount = rd.nextInt(50) + 100;
        for(int i = 0;i < generateClassCount;i++) {
            String className = generateIdentifier();
            if(identifierSet.contains(className)){
                className = generateIdentifier();
            }
            identifierSet.add(className);
            String methodName = generateIdentifier();
            ClassWriter classWriter = new ClassWriter(0);
            classWriter.visit(V1_8, ACC_PUBLIC, className, null, "java/lang/Object", null);
            // generate constructor
            MethodVisitor constructorMethodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            insertExitCode(constructorMethodVisitor);
            insertReturnCode(constructorMethodVisitor);

            // generate normal method
            MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, methodName, "()V", null, null);
            insertExitCode(methodVisitor);
            insertReturnCode(methodVisitor);
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

    private static void insertReturnCode(MethodVisitor methodVisitor) {
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitEnd();
    }

}
