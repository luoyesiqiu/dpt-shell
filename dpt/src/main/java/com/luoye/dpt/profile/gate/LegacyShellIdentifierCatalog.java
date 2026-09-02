package com.luoye.dpt.profile.gate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Versioned identifiers that must not appear in released Profile shell artifacts. */
public final class LegacyShellIdentifierCatalog {
    private static final LegacyShellIdentifierCatalog V1 = new LegacyShellIdentifierCatalog(1,
            Arrays.asList(
                    "com.luoye",
                    "com.luoyesiqiu",
                    "MainActivity",
                    "ProxyApplication",
                    "ProxyComponentFactory",
                    "JniBridge",
                    "DPT_UNKNOWN_DATA",
                    "d_shell_data_001",
                    "i11111i111.zip",
                    "OoooooOooo",
                    "vwwwwwvwww",
                    "dpt-libs",
                    "code_cache",
                    "com/luoye/dpt/junkcode/JunkClass"));

    private final int version;
    private final List<String> identifiers;

    private LegacyShellIdentifierCatalog(int version, List<String> identifiers) {
        this.version = version;
        this.identifiers = Collections.unmodifiableList(identifiers);
    }

    public static LegacyShellIdentifierCatalog v1() {
        return V1;
    }

    public int getVersion() {
        return version;
    }

    public List<String> getIdentifiers() {
        return identifiers;
    }
}
