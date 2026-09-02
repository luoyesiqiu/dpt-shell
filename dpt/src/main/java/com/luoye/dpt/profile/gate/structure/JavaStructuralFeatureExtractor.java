package com.luoye.dpt.profile.gate.structure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JavaStructuralFeatureExtractor {
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while"));
    private static final Set<String> LITERALS = new HashSet<>(Arrays.asList(
            "true", "false", "null"));
    private static final Set<String> CONTROL_KEYWORDS = new HashSet<>(Arrays.asList(
            "if", "else", "for", "while", "do", "switch", "case", "catch", "finally",
            "return", "throw", "break", "continue"));
    private static final List<String> MULTI_CHARACTER_OPERATORS = Arrays.asList(
            ">>>=", "<<=", ">>=", "===", "!==", ">>>", "->", "::", "==", "!=",
            "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=",
            "&=", "|=", "^=", "<<", ">>");

    public StructuralFeatureSet extract(Path javaRoot) throws IOException {
        if (javaRoot == null || Files.isSymbolicLink(javaRoot)
                || !Files.isDirectory(javaRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Java 可变层目录无效：" + javaRoot);
        }
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> javaRoot.relativize(path).toString()))
                    .collect(Collectors.toList());
        }
        if (sources.isEmpty()) {
            throw new IOException("Java 可变层没有源码：" + javaRoot);
        }

        Map<String, Integer> featureCounts = new LinkedHashMap<>();
        for (Path source : sources) {
            List<String> tokens = removePackageAndImports(tokenize(
                    Files.readString(source, StandardCharsets.UTF_8)));
            addWindowFeatures(featureCounts, tokens);
            addControlFlowFeatures(featureCounts, tokens);
        }
        return new StructuralFeatureSet("java", "generated-variable-sources",
                expandCounts(featureCounts));
    }

    private static void addWindowFeatures(Map<String, Integer> features, List<String> tokens) {
        for (int width = 2; width <= 5; width++) {
            for (int start = 0; start + width <= tokens.size(); start++) {
                add(features, "java:token-window-" + width + ":"
                        + String.join(" ", tokens.subList(start, start + width)));
            }
        }
    }

    private static void addControlFlowFeatures(Map<String, Integer> features,
                                               List<String> tokens) {
        int depth = 0;
        String previousControl = "entry";
        for (String token : tokens) {
            if ("}".equals(token)) {
                depth = Math.max(0, depth - 1);
            }
            if (CONTROL_KEYWORDS.contains(token)) {
                add(features, "java:control:" + token + "@depth=" + depth);
                add(features, "java:control-edge:" + previousControl + "->" + token);
                previousControl = token;
            }
            if ("{".equals(token)) {
                depth++;
            }
        }
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

    private static List<String> removePackageAndImports(List<String> tokens) {
        List<String> result = new ArrayList<>();
        boolean skipDeclaration = false;
        for (String token : tokens) {
            if (!skipDeclaration && ("package".equals(token) || "import".equals(token))) {
                skipDeclaration = true;
                continue;
            }
            if (skipDeclaration) {
                if (";".equals(token)) {
                    skipDeclaration = false;
                }
                continue;
            }
            result.add(token);
        }
        return result;
    }

    private static List<String> tokenize(String source) {
        List<String> tokens = new ArrayList<>();
        int offset = 0;
        while (offset < source.length()) {
            char current = source.charAt(offset);
            if (Character.isWhitespace(current)) {
                offset++;
                continue;
            }
            if (current == '/' && offset + 1 < source.length()) {
                char next = source.charAt(offset + 1);
                if (next == '/') {
                    offset = skipLineComment(source, offset + 2);
                    continue;
                }
                if (next == '*') {
                    offset = skipBlockComment(source, offset + 2);
                    continue;
                }
            }
            if (current == '"' || current == '\'') {
                tokens.add("LIT");
                offset = skipQuoted(source, offset + 1, current);
                continue;
            }
            if (Character.isJavaIdentifierStart(current)) {
                int end = offset + 1;
                while (end < source.length()
                        && Character.isJavaIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String identifier = source.substring(offset, end);
                if (LITERALS.contains(identifier)) {
                    tokens.add("LIT");
                } else {
                    tokens.add(KEYWORDS.contains(identifier) ? identifier : "ID");
                }
                offset = end;
                continue;
            }
            if (Character.isDigit(current)) {
                int end = offset + 1;
                while (end < source.length() && isNumberPart(source.charAt(end))) {
                    end++;
                }
                tokens.add("NUM");
                offset = end;
                continue;
            }
            String operator = operatorAt(source, offset);
            tokens.add(operator);
            offset += operator.length();
        }
        return tokens;
    }

    private static int skipLineComment(String source, int offset) {
        int newline = source.indexOf('\n', offset);
        return newline < 0 ? source.length() : newline + 1;
    }

    private static int skipBlockComment(String source, int offset) {
        int end = source.indexOf("*/", offset);
        return end < 0 ? source.length() : end + 2;
    }

    private static int skipQuoted(String source, int offset, char quote) {
        boolean escaped = false;
        while (offset < source.length()) {
            char current = source.charAt(offset++);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == quote) {
                break;
            }
        }
        return offset;
    }

    private static boolean isNumberPart(char value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '_';
    }

    private static String operatorAt(String source, int offset) {
        for (String operator : MULTI_CHARACTER_OPERATORS) {
            if (source.startsWith(operator, offset)) {
                return operator;
            }
        }
        return String.valueOf(source.charAt(offset));
    }
}
