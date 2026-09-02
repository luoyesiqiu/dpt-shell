package com.luoye.dpt.profile.apk;

import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionException;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;
import com.luoye.dpt.res.ApkManifestEditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Resolves the exact published Profile for an unpacked APK without mutating either tree. */
public final class ApkProfileResolver {
    private static final String MANIFEST_FILE_NAME = "AndroidManifest.xml";
    private static final String NATIVE_LIB_DIRECTORY_NAME = "lib";
    private static final String REQUIRED_ABI = "arm64-v8a";

    private final ProfileRegistry registry;
    private final ProfileActiveRevisionStore activeRevisionStore;
    private final ProfilePublishedBundleStore publishedBundleStore;

    public ApkProfileResolver(Path profilesRoot, Path activeProfilesRoot) {
        if (profilesRoot == null || activeProfilesRoot == null) {
            throw new IllegalArgumentException("APK Profile roots must not be null");
        }
        ProfileRevisionStore revisionStore = new ProfileRevisionStore(profilesRoot);
        registry = new ProfileRegistry(profilesRoot);
        activeRevisionStore = new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot,
                revisionStore);
        publishedBundleStore = new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot);
    }

    public ApkProfileProtectionContext resolve(Path unpackedApkRoot) throws IOException {
        if (unpackedApkRoot == null || !Files.isDirectory(unpackedApkRoot)) {
            throw new IOException("APK 解包目录不存在：" + unpackedApkRoot);
        }
        Path manifest = unpackedApkRoot.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("APK Manifest 不存在：" + manifest);
        }
        String applicationId = ApkManifestEditor.getPackageName(manifest.toString());
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new IOException("APK Manifest 缺少有效 applicationId：" + manifest);
        }

        Optional<String> registeredProfileId = registry.findProfileId(applicationId);
        if (!registeredProfileId.isPresent()) {
            throw new ProfileCommandException(ProfileErrorCode.PROFILE_NOT_FOUND,
                    "Profile 不存在：applicationId=" + applicationId);
        }
        String profileId = registeredProfileId.get();
        if (!Files.isRegularFile(activeRevisionStore.getActivePointerPath(profileId))) {
            throw new ProfileCommandException(ProfileErrorCode.ACTIVE_REVISION_MISSING,
                    "Profile 没有活动 Revision：applicationId=" + applicationId
                            + "，profileId=" + profileId);
        }
        ActiveRevisionPointer pointer;
        try {
            pointer = activeRevisionStore.loadActive(profileId);
        } catch (ProfileActiveRevisionException exception) {
            throw resolutionFailure(applicationId, profileId, exception);
        }
        ProfileBundle bundle;
        try {
            bundle = publishedBundleStore.load(applicationId, profileId, pointer.getRevisionId());
        } catch (IOException | RuntimeException exception) {
            throw resolutionFailure(applicationId, profileId, exception);
        }
        Set<String> businessNativeAbis = readBusinessNativeAbis(unpackedApkRoot);
        if (!businessNativeAbis.isEmpty() && !businessNativeAbis.contains(REQUIRED_ABI)) {
            throw new ProfileCommandException(ProfileErrorCode.APK_ABI_INCOMPATIBLE,
                    "APK 包含业务 Native 库但缺少 " + REQUIRED_ABI + "：applicationId="
                            + applicationId + "，业务 ABI=" + businessNativeAbis);
        }
        return new ApkProfileProtectionContext(applicationId, profileId, pointer.getRevisionId(),
                businessNativeAbis, bundle);
    }

    private static ProfileCommandException resolutionFailure(String applicationId,
                                                              String profileId,
                                                              Throwable cause) {
        String details = exceptionMessages(cause);
        if (details.contains("applicationId conflict")
                || details.contains("applicationId 不匹配")
                || details.contains("applicationId=") && details.contains("实际=")
                || details.contains("profileId mismatch")
                || details.contains("profileId 不匹配")) {
            return new ProfileCommandException(ProfileErrorCode.APK_PROFILE_TARGET_MISMATCH,
                    "Profile 目标应用不匹配：applicationId=" + applicationId
                            + "，profileId=" + profileId + "，原因=" + details, cause);
        }
        if (details.contains("protocolVersion") || details.contains("schemaVersion")
                || details.contains("generatorVersion") || details.contains("coreVersion")) {
            return new ProfileCommandException(
                    ProfileErrorCode.APK_PROFILE_PROTOCOL_INCOMPATIBLE,
                    "Profile 协议或生成器版本不兼容：applicationId=" + applicationId
                            + "，profileId=" + profileId + "，原因=" + details, cause);
        }
        return new ProfileCommandException(ProfileErrorCode.APK_BUNDLE_INTEGRITY_FAILED,
                "Profile Bundle 完整性校验失败：applicationId=" + applicationId
                        + "，profileId=" + profileId + "，原因=" + details, cause);
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

    private static Set<String> readBusinessNativeAbis(Path unpackedApkRoot) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        Path libRoot = unpackedApkRoot.resolve(NATIVE_LIB_DIRECTORY_NAME);
        if (!Files.isDirectory(libRoot)) {
            return result;
        }
        File[] abiDirectories = libRoot.toFile().listFiles();
        if (abiDirectories == null) {
            throw new IOException("无法读取 APK Native 目录：" + libRoot);
        }
        for (File abiDirectory : abiDirectories) {
            if (!abiDirectory.isDirectory()) {
                continue;
            }
            File[] libraries = abiDirectory.listFiles((directory, name) -> name.endsWith(".so"));
            if (libraries != null && libraries.length > 0) {
                result.add(abiDirectory.getName());
            }
        }
        return result;
    }
}
