package com.luoye.dpt.builder;

import com.android.apksigner.ApkSignerTool;
import com.luoye.dpt.config.Const;
import com.luoye.dpt.config.ShellConfig;
import com.luoye.dpt.profile.apk.ApkProfileProtectionContext;
import com.luoye.dpt.profile.apk.ApkProfileInputValidator;
import com.luoye.dpt.profile.apk.ApkProfileResolver;
import com.luoye.dpt.profile.archive.ProfileArchiveBundle;
import com.luoye.dpt.profile.archive.ProfileArchiveReader;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.util.FileUtils;
import com.luoye.dpt.util.KeyUtils;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.util.StringUtils;
import com.luoye.dpt.res.ApkManifestEditor;
import com.luoye.dpt.util.ZipUtils;
import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class Apk extends AndroidPackage {
    private static final ReentrantLock IN_PROCESS_PROTECTION_LOCK = new ReentrantLock(true);

    public static class Builder extends AndroidPackage.Builder {
        private Path profilesRoot;
        private Path activeProfilesRoot;

        public Builder profileRoots(Path profilesRoot, Path activeProfilesRoot) {
            this.profilesRoot = profilesRoot;
            this.activeProfilesRoot = activeProfilesRoot;
            return this;
        }

        @Override
        public Builder profileArchive(Path profileArchive) {
            super.profileArchive(profileArchive);
            return this;
        }

        @Override
        public Apk build() {
            return new Apk(this);
        }
    }

    private final Path profilesRoot;
    private final Path activeProfilesRoot;
    private final Path profileArchive;
    private final Path runRoot;
    private ApkProfileProtectionContext profileContext;

    protected Apk(Builder builder) {
        super(builder);
        Path executableRoot = Paths.get(FileUtils.getExecutablePath()).toAbsolutePath().normalize();
        Path projectRoot = executableRoot.getParent();
        profilesRoot = normalizeRoot(builder.profilesRoot,
                projectRoot == null ? executableRoot.resolve("profiles")
                        : projectRoot.resolve("profiles"));
        activeProfilesRoot = normalizeRoot(builder.activeProfilesRoot,
                executableRoot.resolve("profiles"));
        profileArchive = builder.profileArchive == null ? null
                : builder.profileArchive.toAbsolutePath().normalize();
        runRoot = Paths.get(Const.ROOT_OF_OUT_DIR,
                "dptApk-" + StringUtils.generateIdentifier(16)).toAbsolutePath().normalize();
    }

    private static Path normalizeRoot(Path configured, Path fallback) {
        return (configured == null ? fallback : configured).toAbsolutePath().normalize();
    }

    @Override
    public File getWorkspaceDir() {
        return FileUtils.getDir(runRoot.toString(), "unpacked");
    }

    @Override
    public File getLastProcessDir() {
        return FileUtils.getDir(runRoot.toString(), "last-process");
    }

    private File getIntermediatesDir() {
        return FileUtils.getDir(runRoot.toString(), "intermediates");
    }

    @Override
    protected File getOutAssetsDir(String packageDir) {
        return FileUtils.getDir(packageDir,"assets");
    }

    @Override
    public String getLibDir(String packageDir) {
        return packageDir + File.separator + "lib";
    }

    @Override
    public String getDexDir(String packageDir) {
        return packageDir;
    }

    @Override
    protected String getManifestFilePath(String packageOutDir) {
        return packageOutDir + File.separator + "AndroidManifest.xml";
    }

    @Override
    protected boolean sign(String packagePath, String keyStorePath, String signedPackagePath, String keyAlias, String storePassword, String KeyPassword) {
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("sign");
        commandList.add("--ks");
        commandList.add(keyStorePath);
        commandList.add("--ks-key-alias");
        commandList.add(keyAlias);
        commandList.add("--ks-pass");
        commandList.add("pass:" + storePassword);
        commandList.add("--key-pass");
        commandList.add("pass:" + KeyPassword);
        commandList.add("--out");
        commandList.add(signedPackagePath);
        commandList.add("--v1-signing-enabled");
        commandList.add("true");
        commandList.add("--v2-signing-enabled");
        commandList.add("true");
        commandList.add("--v3-signing-enabled");
        commandList.add("true");
        commandList.add(packagePath);

        int size = commandList.size();
        String[] commandArray = new String[size];
        commandArray = commandList.toArray(commandArray);

        try {
            ApkSignerTool.main(commandArray);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void writeProxyAppName(String manifestDir) {
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ApkManifestEditor.writeApplicationName(inManifestPath,outManifestPath, getProxyApplicationName());

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void writeProxyComponentFactoryName(String manifestDir){
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ApkManifestEditor.writeAppComponentFactory(inManifestPath,outManifestPath, getProxyComponentFactory());

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void setExtractNativeLibs(String manifestDir){
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ModificationProperty property = new ModificationProperty();

        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.EXTRACTNATIVELIBS, "true"));

        FileProcesser.processManifestFile(inManifestPath, outManifestPath, property);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void setDebuggable(String manifestDir, boolean debuggable){
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ApkManifestEditor.writeDebuggable(inManifestPath,outManifestPath, debuggable ? "true" : "false");

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void saveApplicationName(String packageOutDir) {
        String androidManifestFile = getManifestFilePath(packageOutDir);
        ShellConfig shellConfig = ShellConfig.getInstance();
        String appName = ApkManifestEditor.getApplicationName(androidManifestFile);

        appName = appName == null ? "" : appName;
        appName = appName.startsWith(".") ? appName.substring(1) : appName;

        shellConfig.setApplicationName(appName);
    }

    @Override
    public void saveAppComponentFactory(String packageOutDir) {
        String androidManifestFile = getManifestFilePath(packageOutDir);
        ShellConfig shellConfig = ShellConfig.getInstance();

        String acfName = ApkManifestEditor.getAppComponentFactory(androidManifestFile);

        acfName = acfName == null ? "" : acfName;

        shellConfig.setAppComponentFactoryName(acfName);
    }

    private static void process(Apk apk) throws IOException {
        byte[] encKey = KeyUtils.generateKey();

        File apkFile = new File(apk.getFilePath());
        //apk extract path
        String apkMainProcessPath = apk.getWorkspaceDir().getAbsolutePath();

        try {
            LogUtils.info("Workspace path: " + apkMainProcessPath);

            ZipUtils.unZip(apk.getFilePath(),apkMainProcessPath);

            String packageName = ApkManifestEditor.getPackageName(apkMainProcessPath + File.separator + "AndroidManifest.xml");
            apk.setPackageName(packageName);
            apk.resolveProfileInputs(Paths.get(apkMainProcessPath));
            apk.prepareProtection();

        /*======================================*
         * Process AndroidManifest.xml
         *======================================*/
            apk.saveApplicationName(apkMainProcessPath);
            apk.writeProxyAppName(apkMainProcessPath);
            if(apk.isAppComponentFactory()){
                apk.saveAppComponentFactory(apkMainProcessPath);
                apk.writeProxyComponentFactoryName(apkMainProcessPath);
            }
            if(apk.isDebuggable()) {
                LogUtils.info("Make apk debuggable.");
                apk.setDebuggable(apkMainProcessPath, true);
            }
            apk.setExtractNativeLibs(apkMainProcessPath);

        /*======================================*
         * Process .dex files
         *======================================*/
            String assetsPath = apk.getOutAssetsDir(apkMainProcessPath).getAbsolutePath();

            apk.extractDexCode(apkMainProcessPath, assetsPath, encKey);
            apk.addJunkCodeDex(apkMainProcessPath);
            apk.compressDexFiles(apkMainProcessPath);
            apk.deleteAllDexFiles(apkMainProcessPath);
            apk.combineDexZipWithShellDex(apkMainProcessPath);
            apk.addKeepDexes(apkMainProcessPath);
            FileUtils.deleteRecurse(apk.getKeepDexTempDir(apkMainProcessPath));

        /*======================================*
         * Process .so files
         *======================================*/
            apk.copyNativeLibs(apkMainProcessPath);

            apk.encryptSoFiles(apkMainProcessPath, encKey);

        /*======================================*
         * Build package
         *======================================*/
            apk.writeConfig(apkMainProcessPath, encKey);

            apk.buildPackage(apkFile.getAbsolutePath(), apkMainProcessPath, FileUtils.getUserDir());

            LogUtils.info("All done.");
        } finally {
            apk.cleanupRunRoot();
        }
    }

    @Override
    public void protect() throws IOException {
        IN_PROCESS_PROTECTION_LOCK.lock();
        try {
            process(this);
        } finally {
            IN_PROCESS_PROTECTION_LOCK.unlock();
        }
    }

    private void cleanupRunRoot() {
        if (profileContext != null) {
            try {
                profileContext.close();
            } catch (IOException exception) {
                LogUtils.warn("Failed to close Profile archive workspace: %s", exception.getMessage());
            }
        }
        File runDirectory = runRoot.toFile();
        if (runDirectory.exists()) {
            FileUtils.deleteRecurse(runDirectory);
        }
    }

    private void prepareProtection() throws IOException {
        super.protect();
    }

    void resolveProfileInputs(Path unpackedApkRoot) throws IOException {
        if (profileArchive != null) {
            resolveArchiveProfileInputs(unpackedApkRoot);
            return;
        }
        profileContext = new ApkProfileResolver(profilesRoot, activeProfilesRoot)
                .resolve(unpackedApkRoot);
        setProfileContract(profileContext.getContract());
    }

    private void resolveArchiveProfileInputs(Path unpackedApkRoot) throws IOException {
        ApkProfileInputValidator.Metadata apkMetadata =
                ApkProfileInputValidator.inspectUnpacked(unpackedApkRoot);
        Path manifest = unpackedApkRoot.resolve("AndroidManifest.xml");
        String applicationId = apkMetadata.getApplicationId();
        Path extractionRoot = runRoot.resolve("profile-archive");
        try {
            ProfileArchiveBundle archive = new ProfileArchiveReader().readVerified(
                    profileArchive, extractionRoot, applicationId);
            ApkProfileInputValidator.validate(archive, apkMetadata);
            profileContext = ApkProfileProtectionContext.fromArchive(applicationId,
                    archive.getProfileId(), archive.getRevisionId(),
                    apkMetadata.getBusinessNativeAbis(), archive);
            setProfileContract(profileContext.getContract());
        } catch (ProfileCommandException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw archiveResolutionFailure(applicationId, exception);
        }
    }

    private static ProfileCommandException archiveResolutionFailure(String applicationId,
                                                                      Throwable cause) {
        String message = exceptionMessages(cause);
        if (message.contains("candidate") || message.contains("候选")) {
            return new ProfileCommandException(ProfileErrorCode.ARCHIVE_CANDIDATE_REQUIRED,
                    "正式加固只能使用 verified Profile：applicationId=" + applicationId
                            + "，原因=" + message, cause);
        }
        if (message.contains("applicationId") || message.contains("目标应用")) {
            return new ProfileCommandException(ProfileErrorCode.APK_PROFILE_TARGET_MISMATCH,
                    "Profile 目标应用不匹配：applicationId=" + applicationId
                            + "，原因=" + message, cause);
        }
        if (message.contains("protocol") || message.contains("协议")
                || message.contains("schema") || message.contains("生成器")
                || message.contains("generatorVersion") || message.contains("toolchainVersion")
                || message.contains("core")) {
            return new ProfileCommandException(ProfileErrorCode.APK_PROFILE_PROTOCOL_INCOMPATIBLE,
                    "Profile 协议或生成器版本不兼容：applicationId=" + applicationId
                            + "，原因=" + message, cause);
        }
        return new ProfileCommandException(ProfileErrorCode.APK_BUNDLE_INTEGRITY_FAILED,
                "Profile 归档完整性校验失败：applicationId=" + applicationId
                        + "，原因=" + message, cause);
    }

    private static String exceptionMessages(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isEmpty()) {
                if (result.length() > 0) {
                    result.append(" <- ");
                }
                result.append(current.getMessage());
            }
            current = current.getCause();
        }
        return result.toString();
    }

    private ApkProfileProtectionContext requireProfileContext() {
        if (profileContext == null) {
            throw new IllegalStateException("APK Profile context has not been resolved");
        }
        return profileContext;
    }

    @Override
    protected String getProxyDexPath() {
        return requireProfileContext().getShellDexPath().toString();
    }

    @Override
    protected File getShellNativeLibsRoot() {
        return requireProfileContext().getShellNativeLibsRoot().toFile();
    }

    @Override
    protected String getRenameDexPath() {
        return new File(getIntermediatesDir(), "rename_classes.dex").getAbsolutePath();
    }

    @Override
    protected String getJunkCodeDexPath() {
        return new File(getIntermediatesDir(), "junkcode.dex").getAbsolutePath();
    }

}
