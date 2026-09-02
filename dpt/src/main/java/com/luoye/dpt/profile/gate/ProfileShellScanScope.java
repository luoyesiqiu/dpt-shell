package com.luoye.dpt.profile.gate;

import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.storage.ProfileBundle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restricts the static gate to Profile-owned shell artifacts and injected names. */
public final class ProfileShellScanScope {
    private final Map<String, Path> artifacts;
    private final List<String> injectedIdentifiers;

    private ProfileShellScanScope(Map<String, Path> artifacts,
                                  List<String> injectedIdentifiers) {
        this.artifacts = Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
        this.injectedIdentifiers = Collections.unmodifiableList(
                new ArrayList<>(injectedIdentifiers));
    }

    public static ProfileShellScanScope forBundle(ProfileBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Profile Bundle must not be null");
        }
        Map<String, Path> artifacts = new LinkedHashMap<>();
        artifacts.put("shell-dex", bundle.getShellDexPath());
        artifacts.put("arm64-so", bundle.getArm64SoPath());

        ProfileContract contract = bundle.getContract();
        List<String> injectedIdentifiers = new ArrayList<>();
        injectedIdentifiers.add(contract.getShellConfigAssetName());
        injectedIdentifiers.add(contract.getDexAssetName());
        injectedIdentifiers.add(contract.getCodeItemAssetName());
        injectedIdentifiers.add(contract.getNativeLibAssetDirName());
        injectedIdentifiers.add(contract.getRuntimeDirName());
        injectedIdentifiers.add(contract.getSoName());
        return new ProfileShellScanScope(artifacts, injectedIdentifiers);
    }

    public Map<String, Path> getArtifacts() {
        return artifacts;
    }

    public List<String> getInjectedIdentifiers() {
        return injectedIdentifiers;
    }
}
