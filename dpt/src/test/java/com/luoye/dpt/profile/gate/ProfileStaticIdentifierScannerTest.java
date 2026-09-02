package com.luoye.dpt.profile.gate;

import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileCreationService;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileStaticIdentifierScannerTest {
    private static final List<String> REQUIRED_IDENTIFIERS = Arrays.asList(
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
            "com/luoye/dpt/junkcode/JunkClass");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void catalogIsVersionedAndContainsEveryRequiredLegacyIdentifier() {
        LegacyShellIdentifierCatalog catalog = LegacyShellIdentifierCatalog.v1();

        Assert.assertEquals(1, catalog.getVersion());
        Assert.assertEquals(REQUIRED_IDENTIFIERS, catalog.getIdentifiers());
    }

    @Test
    public void scannerReportsArtifactAndCountForDotAndSlashForms() throws Exception {
        Path dex = temporaryFolder.newFile("classes.dex").toPath();
        Files.write(dex, ("Lcom/luoyesiqiu/ProxyApplication;\0MainActivity\0"
                + "i11111i111.zip\0").getBytes(StandardCharsets.UTF_8));
        Path so = temporaryFolder.newFile("libshell.so").toPath();
        Files.write(so, ("DPT_UNKNOWN_DATA\0code_cache\0code_cache\0"
                + "com/luoye/dpt/junkcode/JunkClass").getBytes(StandardCharsets.UTF_8));
        Map<String, Path> artifacts = new LinkedHashMap<>();
        artifacts.put("shell-dex", dex);
        artifacts.put("arm64-so", so);

        ProfileStaticIdentifierReport report = new ProfileStaticIdentifierScanner(
                LegacyShellIdentifierCatalog.v1()).scan(artifacts);

        Assert.assertFalse(report.isPassed());
        Assert.assertEquals(1, report.getHits().get("ProxyApplication")
                .get("shell-dex").intValue());
        Assert.assertEquals(2, report.getHits().get("code_cache")
                .get("arm64-so").intValue());
        Assert.assertEquals(1, report.getHits().get("com.luoyesiqiu")
                .get("shell-dex").intValue());
        Assert.assertEquals(1, report.getHits().get("com/luoye/dpt/junkcode/JunkClass")
                .get("arm64-so").intValue());
    }

    @Test
    public void scannerPassesProfileSpecificArtifacts() throws Exception {
        Path dex = temporaryFolder.newFile("profile.dex").toPath();
        Files.writeString(dex, "Lp/n0123456789abcd/A123456789abc;",
                StandardCharsets.UTF_8);
        Path so = temporaryFolder.newFile("profile.so").toPath();
        Files.writeString(so, "profile_private_symbol", StandardCharsets.UTF_8);
        Map<String, Path> artifacts = new LinkedHashMap<>();
        artifacts.put("shell-dex", dex);
        artifacts.put("arm64-so", so);

        ProfileStaticIdentifierReport report = new ProfileStaticIdentifierScanner(
                LegacyShellIdentifierCatalog.v1()).scan(artifacts);

        Assert.assertTrue(report.getHits().toString(), report.isPassed());
        Assert.assertTrue(report.getHits().isEmpty());
    }

    @Test
    public void bundleScopeExcludesBusinessApkContentAndScansOnlyInjectedNames() throws Exception {
        String applicationId = "com.example.scan.scope";
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeRoot = temporaryFolder.newFolder("active").toPath();
        new ProfileCreationService(profilesRoot).create(applicationId);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(applicationId, null, "recipe-a", null);
        ProfileBundle bundle = ProfileTestArtifactFactory.assemble(profilesRoot, applicationId,
                revision, temporaryFolder.newFolder("bundle-input").toPath());
        Path businessDex = temporaryFolder.newFile("business-classes.dex").toPath();
        Files.writeString(businessDex, String.join("\n", REQUIRED_IDENTIFIERS),
                StandardCharsets.UTF_8);

        ProfileShellScanScope scope = ProfileShellScanScope.forBundle(bundle);
        ProfileStaticIdentifierReport report = new ProfileStaticIdentifierScanner(
                LegacyShellIdentifierCatalog.v1()).scan(scope);

        Assert.assertEquals(Arrays.asList("shell-dex", "arm64-so"),
                Arrays.asList(scope.getArtifacts().keySet().toArray(new String[0])));
        Assert.assertFalse(scope.getArtifacts().containsValue(businessDex));
        Assert.assertFalse(scope.getInjectedIdentifiers().contains(applicationId));
        Assert.assertTrue(report.getHits().toString(), report.isPassed());
    }
}
