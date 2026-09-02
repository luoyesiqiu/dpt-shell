package com.luoye.dpt.profile.gate.structure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NativeStructuralFeatureExtractor {
    private static final Pattern FUNCTION_HEADER = Pattern.compile(
            "^\\s*(?:[0-9a-fA-F]+\\s+)?<[^>]+>:\\s*$");
    private static final Pattern ADDRESSED_LINE = Pattern.compile(
            "^\\s*[0-9a-fA-F]+:\\s*(.*)$");
    private static final Pattern MNEMONIC = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9._]*)(?:\\s+(.*))?$");

    public StructuralFeatureSet extract(Path llvmObjdump, List<Path> variableObjectFiles)
            throws IOException {
        if (llvmObjdump == null || Files.isSymbolicLink(llvmObjdump)
                || !Files.isRegularFile(llvmObjdump, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(llvmObjdump)) {
            throw new IOException("llvm-objdump 不可执行：" + llvmObjdump);
        }
        if (variableObjectFiles == null || variableObjectFiles.isEmpty()) {
            throw new IOException("Native 可变层目标文件不能为空");
        }
        List<String> disassemblies = new ArrayList<>();
        for (Path objectFile : variableObjectFiles) {
            if (objectFile == null || Files.isSymbolicLink(objectFile)
                    || !Files.isRegularFile(objectFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Native 可变层目标文件无效：" + objectFile);
            }
            disassemblies.add(disassemble(llvmObjdump, objectFile));
        }
        return extractDisassembly("arm64-variable-objects", disassemblies);
    }

    public StructuralFeatureSet extractDisassembly(String scope, List<String> disassemblies)
            throws IOException {
        if (scope == null || scope.trim().isEmpty()
                || disassemblies == null || disassemblies.isEmpty()) {
            throw new IllegalArgumentException("Native 结构特征输入不能为空");
        }
        List<List<Instruction>> functions = new ArrayList<>();
        for (String disassembly : disassemblies) {
            if (disassembly == null) {
                throw new IllegalArgumentException("Native 反汇编文本不能为空");
            }
            parseFunctions(disassembly, functions);
        }
        if (functions.isEmpty()) {
            throw new IOException("Native 可变层反汇编没有指令");
        }
        Map<String, Integer> featureCounts = new LinkedHashMap<>();
        for (List<Instruction> function : functions) {
            addFunctionFeatures(featureCounts, function);
        }
        return new StructuralFeatureSet("native", scope.trim(), expandCounts(featureCounts));
    }

    private static String disassemble(Path llvmObjdump, Path objectFile) throws IOException {
        Process process = new ProcessBuilder(llvmObjdump.toString(), "--disassemble",
                "--no-show-raw-insn", "--no-leading-addr", objectFile.toString())
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("llvm-objdump 执行被中断：" + objectFile, e);
        }
        if (exitCode != 0) {
            throw new IOException("llvm-objdump 执行失败：file=" + objectFile
                    + "，exitCode=" + exitCode + "，output="
                    + new String(output, StandardCharsets.UTF_8));
        }
        return new String(output, StandardCharsets.UTF_8);
    }

    private static void parseFunctions(String disassembly, List<List<Instruction>> functions) {
        List<Instruction> current = new ArrayList<>();
        for (String line : disassembly.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Disassembly of section")
                    || trimmed.contains("file format") || trimmed.startsWith(".debug")
                    || trimmed.startsWith("R_AARCH64_") || trimmed.startsWith("RELOCATION")) {
                continue;
            }
            if (FUNCTION_HEADER.matcher(trimmed).matches()) {
                flushFunction(functions, current);
                current = new ArrayList<>();
                continue;
            }
            Instruction instruction = parseInstruction(trimmed);
            if (instruction != null) {
                current.add(instruction);
            }
        }
        flushFunction(functions, current);
    }

    private static Instruction parseInstruction(String line) {
        Matcher addressed = ADDRESSED_LINE.matcher(line);
        String body = addressed.matches() ? addressed.group(1).trim() : line;
        body = body.replaceFirst("^(?:[0-9a-fA-F]{2}\\s+){4}", "")
                .replaceFirst("^[0-9a-fA-F]{8}\\s+", "")
                .trim();
        Matcher matcher = MNEMONIC.matcher(body);
        if (!matcher.matches()) {
            return null;
        }
        String mnemonic = matcher.group(1).toLowerCase();
        if (mnemonic.startsWith("r_aarch64_") || mnemonic.startsWith(".")) {
            return null;
        }
        String operands = matcher.group(2) == null ? "" : normalizeOperands(matcher.group(2));
        return new Instruction(mnemonic, operands.isEmpty() ? mnemonic : mnemonic + " " + operands);
    }

    private static String normalizeOperands(String operands) {
        String normalized = operands.replaceAll("//.*$", "")
                .replaceAll("<[^>]*>", "@")
                .replaceAll("(?i)#?[-+]?0x[0-9a-f]+", "#")
                .replaceAll("#?[-+]?\\b\\d+\\b", "#")
                .replaceAll("(?i)\\b(?:[wxvqds][0-9]+|sp|lr|fp|xzr|wzr)\\b", "%")
                .replaceAll("\\b[A-Za-z_.$][A-Za-z0-9_.$@]*\\b", "ID")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*([,\\[\\]{}()!])\\s*", "$1")
                .trim();
        return normalized;
    }

    private static void flushFunction(List<List<Instruction>> functions,
                                      List<Instruction> function) {
        if (!function.isEmpty()) {
            functions.add(function);
        }
    }

    private static void addFunctionFeatures(Map<String, Integer> features,
                                            List<Instruction> function) {
        List<List<Instruction>> blocks = basicBlocks(function);
        add(features, "native:function:block-count:" + blocks.size());
        add(features, "native:function:instruction-count:" + function.size());
        for (List<Instruction> block : blocks) {
            add(features, "native:block:instruction-count:" + block.size());
            List<String> mnemonics = new ArrayList<>();
            List<String> normalized = new ArrayList<>();
            for (Instruction instruction : block) {
                mnemonics.add(instruction.mnemonic);
                normalized.add(instruction.normalized);
                add(features, "native:instruction:" + instruction.normalized);
            }
            add(features, "native:block:mnemonics:" + String.join(" ", mnemonics));
            for (int width = 2; width <= 4; width++) {
                for (int start = 0; start + width <= normalized.size(); start++) {
                    add(features, "native:instruction-window-" + width + ":"
                            + String.join(" | ", normalized.subList(start, start + width)));
                }
            }
        }
    }

    private static List<List<Instruction>> basicBlocks(List<Instruction> function) {
        List<List<Instruction>> blocks = new ArrayList<>();
        List<Instruction> current = new ArrayList<>();
        for (Instruction instruction : function) {
            current.add(instruction);
            if (isBlockTerminator(instruction.mnemonic)) {
                blocks.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            blocks.add(current);
        }
        return blocks;
    }

    private static boolean isBlockTerminator(String mnemonic) {
        return "b".equals(mnemonic) || mnemonic.startsWith("b.")
                || "br".equals(mnemonic) || "ret".equals(mnemonic)
                || "eret".equals(mnemonic) || "cbz".equals(mnemonic)
                || "cbnz".equals(mnemonic) || "tbz".equals(mnemonic)
                || "tbnz".equals(mnemonic);
    }

    private static Set<String> expandCounts(Map<String, Integer> counts) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            for (int occurrence = 1; occurrence <= entry.getValue(); occurrence++) {
                result.add(entry.getKey() + "#" + occurrence);
            }
        }
        return result;
    }

    private static void add(Map<String, Integer> counts, String feature) {
        counts.put(feature, counts.getOrDefault(feature, 0) + 1);
    }

    private static final class Instruction {
        private final String mnemonic;
        private final String normalized;

        private Instruction(String mnemonic, String normalized) {
            this.mnemonic = mnemonic;
            this.normalized = normalized;
        }
    }
}
