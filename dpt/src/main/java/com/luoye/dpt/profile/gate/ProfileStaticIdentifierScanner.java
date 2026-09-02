package com.luoye.dpt.profile.gate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ProfileStaticIdentifierScanner {
    private final LegacyShellIdentifierCatalog catalog;

    public ProfileStaticIdentifierScanner(LegacyShellIdentifierCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("legacy identifier catalog must not be null");
        }
        this.catalog = catalog;
    }

    public ProfileStaticIdentifierReport scan(ProfileShellScanScope scope) throws IOException {
        if (scope == null) {
            throw new IllegalArgumentException("shell scan scope must not be null");
        }
        Map<String, Map<String, Integer>> hits = scanArtifacts(scope.getArtifacts());
        String injectedNames = String.join("\0", scope.getInjectedIdentifiers());
        scanContent(hits, "injected-identifiers",
                injectedNames.getBytes(StandardCharsets.UTF_8));
        return new ProfileStaticIdentifierReport(catalog.getVersion(), hits);
    }

    ProfileStaticIdentifierReport scan(Map<String, Path> artifacts) throws IOException {
        return new ProfileStaticIdentifierReport(catalog.getVersion(), scanArtifacts(artifacts));
    }

    private Map<String, Map<String, Integer>> scanArtifacts(Map<String, Path> artifacts)
            throws IOException {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new IllegalArgumentException("shell scan artifacts must not be empty");
        }
        Map<String, Map<String, Integer>> hits = new LinkedHashMap<>();
        for (Map.Entry<String, Path> artifact : artifacts.entrySet()) {
            String label = artifact.getKey();
            Path path = artifact.getValue();
            if (label == null || label.trim().isEmpty() || path == null
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("壳扫描制品无效：" + label + "=" + path);
            }
            scanContent(hits, label, Files.readAllBytes(path));
        }
        return hits;
    }

    private void scanContent(Map<String, Map<String, Integer>> hits, String label,
                             byte[] content) {
        for (String identifier : catalog.getIdentifiers()) {
            int count = 0;
            for (String representation : representations(identifier)) {
                count += count(content, representation.getBytes(StandardCharsets.UTF_8));
            }
            if (count > 0) {
                hits.computeIfAbsent(identifier, ignored -> new LinkedHashMap<>())
                        .put(label, count);
            }
        }
    }

    private static Set<String> representations(String identifier) {
        Set<String> result = new LinkedHashSet<>();
        result.add(identifier);
        if (identifier.indexOf('.') >= 0) {
            result.add(identifier.replace('.', '/'));
        }
        if (identifier.indexOf('/') >= 0) {
            result.add(identifier.replace('/', '.'));
        }
        return result;
    }

    private static int count(byte[] content, byte[] needle) {
        int count = 0;
        outer:
        for (int start = 0; start <= content.length - needle.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (content[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            count++;
        }
        return count;
    }
}
