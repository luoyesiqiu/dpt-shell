package com.luoye.dpt.profile.gate;

import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileArtifactUniquenessChecker {

    public ProfileArtifactUniquenessReport compare(ProfileBundle left, ProfileBundle right)
            throws IOException {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Profile Bundles must not be null");
        }
        ProfileContract leftContract = left.getContract();
        ProfileContract rightContract = right.getContract();
        List<ProfileArtifactUniquenessReport.IdentifierConflict> conflicts =
                identifierConflicts(identifiers(leftContract), identifiers(rightContract));
        Map<String, ProfileArtifactUniquenessReport.ArtifactHashComparison> artifactHashes =
                new LinkedHashMap<>();
        artifactHashes.put("shell-dex", compareHashes(left.getShellDexPath(),
                right.getShellDexPath()));
        artifactHashes.put("arm64-so", compareHashes(left.getArm64SoPath(),
                right.getArm64SoPath()));
        return new ProfileArtifactUniquenessReport(reference(leftContract),
                reference(rightContract), conflicts, artifactHashes);
    }

    private static ProfileArtifactUniquenessReport.ProfileReference reference(
            ProfileContract contract) {
        return new ProfileArtifactUniquenessReport.ProfileReference(contract.getApplicationId(),
                contract.getProfileId(), contract.getRevisionId());
    }

    private static Map<String, String> identifiers(ProfileContract contract) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("profile-id", contract.getProfileId());
        result.put("shell-package", packageName(contract.getProxyApplicationClassName()));
        result.put("proxy-application-class", contract.getProxyApplicationClassName());
        result.put("proxy-component-factory-class",
                contract.getProxyComponentFactoryClassName());
        result.put("jni-bridge-class", contract.getJniBridgeClassName());
        for (JniMethodMapping mapping : contract.getJniMethods()) {
            result.put("jni-method:" + mapping.getSemanticId(), mapping.getMethodName());
        }
        result.put("so-name", contract.getSoName());
        result.put("shell-config-asset", contract.getShellConfigAssetName());
        result.put("dex-asset", contract.getDexAssetName());
        result.put("code-item-asset", contract.getCodeItemAssetName());
        result.put("native-lib-asset-dir", contract.getNativeLibAssetDirName());
        result.put("runtime-dir", contract.getRuntimeDirName());
        result.put("junk-class", contract.getJunkClassName());
        result.put("rc4-key-symbol", contract.getRc4KeySymbol());
        result.put("build-key", contract.getBuildKey());
        return result;
    }

    private static List<ProfileArtifactUniquenessReport.IdentifierConflict>
            identifierConflicts(Map<String, String> left, Map<String, String> right) {
        List<ProfileArtifactUniquenessReport.IdentifierConflict> result = new ArrayList<>();
        for (Map.Entry<String, String> leftEntry : left.entrySet()) {
            for (Map.Entry<String, String> rightEntry : right.entrySet()) {
                if (leftEntry.getValue().equals(rightEntry.getValue())) {
                    result.add(new ProfileArtifactUniquenessReport.IdentifierConflict(
                            leftEntry.getKey(), rightEntry.getKey(), leftEntry.getValue()));
                }
            }
        }
        return result;
    }

    private static ProfileArtifactUniquenessReport.ArtifactHashComparison compareHashes(
            Path left, Path right) throws IOException {
        return new ProfileArtifactUniquenessReport.ArtifactHashComparison(
                MessageDigestUtils.sha256(Files.readAllBytes(left)),
                MessageDigestUtils.sha256(Files.readAllBytes(right)));
    }

    private static String packageName(String className) {
        return className.substring(0, className.lastIndexOf('.'));
    }
}
