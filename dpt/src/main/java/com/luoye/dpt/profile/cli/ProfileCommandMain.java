package com.luoye.dpt.profile.cli;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.luoye.dpt.profile.archive.ProfileArchiveBundle;
import com.luoye.dpt.profile.archive.ProfileArchivePromotionService;
import com.luoye.dpt.profile.archive.ProfileArchiveReader;
import com.luoye.dpt.profile.archive.ProfileArchiveStatus;
import com.luoye.dpt.profile.archive.ProfileArchiveWriter;
import com.luoye.dpt.profile.apk.ApkProfileInputValidator;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;
import com.luoye.dpt.profile.storage.ProfileBundleValidator;
import com.luoye.dpt.profile.storage.ProfilePublishService;
import com.luoye.dpt.profile.storage.ProfileShowResult;
import com.luoye.dpt.profile.storage.ProfileShowService;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileCommandMain {
    private ProfileCommandMain() {
    }

    public static void main(String[] args) throws Exception {
        try {
            execute(args);
        } catch (ProfileCommandException e) {
            System.err.println("PROFILE_ERROR code=" + e.getErrorCode().getCode()
                    + " message=" + e.getDisplayMessage());
            throw e;
        }
    }

    private static void execute(String[] args) throws Exception {
        if (args.length == 0) {
            throw missingParameter("command");
        }
        Map<String, String> options = parseOptions(args);
        String command = args[0];
        if ("pack".equals(command)) {
            executePack(options);
            return;
        }
        if ("validate".equals(command)) {
            executeValidate(options);
            return;
        }
        if ("promote".equals(command)) {
            executePromote(options);
            return;
        }

        String profilesRoot = requireOption(options, "--profiles-root");
        if ("create".equals(command)) {
            String applicationId = requireOption(options, "--application-id");
            ProfileCreationResult result = new ProfileCreationService(Paths.get(profilesRoot))
                    .create(applicationId);
            System.out.println("PROFILE_CREATE_RESULT profileId=" + result.getProfileId()
                    + " created=" + result.isCreated());
            return;
        }
        if ("show".equals(command)) {
            String activeProfilesRoot = requireOption(options, "--active-profiles-root");
            ProfileShowResult result = new ProfileShowService(Paths.get(profilesRoot),
                    Paths.get(activeProfilesRoot)).show(optionalOption(options, "--application-id"),
                    optionalOption(options, "--profile-id"));
            System.out.println("PROFILE_SHOW_RESULT " + JSON.toJSONString(result,
                    JSONWriter.Feature.WriteNulls));
            return;
        }
        if ("build".equals(command)) {
            String activeProfilesRoot = requireOption(options, "--active-profiles-root");
            String overlayDir = optionalOption(options, "--overlay-dir");
            Path overlayPath = overlayDir == null ? null : Paths.get(overlayDir);
            String toolchainVersion = optionalOption(options, "--toolchain-version");
            ProfileBuildService buildService = toolchainVersion == null
                    ? new ProfileBuildService(Paths.get(profilesRoot), Paths.get(activeProfilesRoot))
                    : new ProfileBuildService(Paths.get(profilesRoot),
                    Paths.get(activeProfilesRoot), toolchainVersion);
            ProfileRevisionManifest result = buildService.build(
                    optionalOption(options, "--application-id"),
                    optionalOption(options, "--profile-id"), optionalOption(options, "--recipe-id"),
                    overlayPath);
            System.out.println("PROFILE_BUILD_RESULT " + JSON.toJSONString(result,
                    JSONWriter.Feature.WriteNulls));
            return;
        }
        if ("assemble-candidate".equals(command)) {
            String applicationId = requireOption(options, "--application-id");
            String profileId = requireOption(options, "--profile-id");
            String revisionId = requireOption(options, "--revision-id");
            Path shellFilesRoot = Paths.get(requireOption(options, "--shell-files-root"));
            ProfileBundleAssembler assembler = new ProfileBundleAssembler(Paths.get(profilesRoot));
            assembler.assemble(applicationId, profileId, revisionId,
                    shellFilesRoot.resolve("dex/classes.dex"),
                    shellFilesRoot.resolve("libs/arm64"),
                    shellFilesRoot.resolve("build-key"));
            ProfileBundle bundle = new ProfileBundleValidator(Paths.get(profilesRoot))
                    .validateCandidate(applicationId, profileId, revisionId);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("applicationId", applicationId);
            result.put("profileId", profileId);
            result.put("revisionId", revisionId);
            result.put("bundleRoot", bundle.getRoot().toString());
            System.out.println("PROFILE_BUNDLE_RESULT " + JSON.toJSONString(result));
            return;
        }
        if ("publish".equals(command)) {
            String activeProfilesRoot = requireOption(options, "--active-profiles-root");
            String revisionId = requireOption(options, "--revision-id");
            Path reportDir = Paths.get(requireOption(options, "--report-dir"));
            Path scanBaseline = optionalPath(options, "--scan-baseline");
            Path scanCandidate = optionalPath(options, "--scan-candidate");
            ActiveRevisionPointer result;
            ProfilePublishService service = new ProfilePublishService(Paths.get(profilesRoot),
                    Paths.get(activeProfilesRoot));
            if (scanBaseline != null || scanCandidate != null) {
                result = service.publish(optionalOption(options, "--application-id"),
                        optionalOption(options, "--profile-id"), revisionId, reportDir,
                        scanBaseline, scanCandidate);
            } else {
                // The command-line publishing path is production-facing and therefore always
                // requires the manually exported, comparable scan pair.
                result = service.publishStrict(optionalOption(options, "--application-id"),
                        optionalOption(options, "--profile-id"), revisionId, reportDir);
            }
            System.out.println("PROFILE_PUBLISH_RESULT " + JSON.toJSONString(result));
            return;
        }
        if ("rollback".equals(command)) {
            String activeProfilesRoot = requireOption(options, "--active-profiles-root");
            String revisionId = requireOption(options, "--revision-id");
            ActiveRevisionPointer result = new ProfilePublishService(Paths.get(profilesRoot),
                    Paths.get(activeProfilesRoot)).rollback(optionalOption(options, "--application-id"),
                    optionalOption(options, "--profile-id"), revisionId);
            System.out.println("PROFILE_ROLLBACK_RESULT " + JSON.toJSONString(result));
            return;
        }
        throw new ProfileCommandException(ProfileErrorCode.MISSING_PARAMETER,
                "不支持的 Profile 命令：" + command);
    }

    private static void executePack(Map<String, String> options) throws Exception {
        Path bundle = Paths.get(requireOption(options, "--bundle"));
        Path output = Paths.get(requireOption(options, "--out"));
        if (output.getFileName() == null
                || !output.getFileName().toString().endsWith(ProfileArchiveWriter.CANDIDATE_SUFFIX)) {
            throw archiveError(ProfileErrorCode.ARCHIVE_INVALID,
                    "pack 输出文件必须以 " + ProfileArchiveWriter.CANDIDATE_SUFFIX + " 结尾", null);
        }
        if (java.nio.file.Files.exists(output.toAbsolutePath().normalize(),
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw archiveError(ProfileErrorCode.ARCHIVE_OUTPUT_EXISTS,
                    "Profile 归档输出已存在，拒绝覆盖：" + output.toAbsolutePath().normalize(), null);
        }
        try {
            Path packed = new ProfileArchiveWriter().packCandidate(bundle, output);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", ProfileArchiveStatus.CANDIDATE.name());
            result.put("profile", packed.toString());
            System.out.println("PROFILE_PACK_RESULT " + JSON.toJSONString(result));
        } catch (ProfileCommandException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw archiveError(ProfileErrorCode.ARCHIVE_INVALID,
                    "Profile 候选归档失败：" + messageOf(exception), exception);
        }
    }

    private static void executeValidate(Map<String, String> options) throws Exception {
        Path profile = Paths.get(requireOption(options, "--profile"));
        Path apk = optionalPath(options, "--apk");
        try (ProfileArchiveBundle bundle = new ProfileArchiveReader().readVerified(profile)) {
            if (apk != null) {
                ApkProfileInputValidator.validate(bundle,
                        ApkProfileInputValidator.inspectArchive(apk));
            }
            String expectedApplicationId = optionalOption(options, "--application-id");
            if (expectedApplicationId != null
                    && !expectedApplicationId.equals(bundle.getApplicationId())) {
                throw new ProfileCommandException(ProfileErrorCode.APK_PROFILE_TARGET_MISMATCH,
                        "Profile 目标应用不匹配：期望=" + expectedApplicationId
                                + "，实际=" + bundle.getApplicationId());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", bundle.getStatus().name());
            result.put("applicationId", bundle.getApplicationId());
            result.put("profileId", bundle.getProfileId());
            result.put("revisionId", bundle.getRevisionId());
            result.put("abi", bundle.getManifest().getAbi());
            result.put("profile", profile.toAbsolutePath().normalize().toString());
            System.out.println("PROFILE_VALIDATE_RESULT " + JSON.toJSONString(result));
        } catch (ProfileCommandException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            String message = messageOf(exception);
            ProfileErrorCode code = message.contains("candidate") || message.contains("候选")
                    ? ProfileErrorCode.ARCHIVE_CANDIDATE_REQUIRED
                    : ProfileErrorCode.ARCHIVE_INVALID;
            throw archiveError(code, "Profile 校验失败：" + message, exception);
        }
    }

    private static void executePromote(Map<String, String> options) throws Exception {
        Path candidate = Paths.get(requireOption(options, "--candidate"));
        Path reports = Paths.get(requireOption(options, "--reports"));
        Path output = Paths.get(requireOption(options, "--out"));
        if (java.nio.file.Files.exists(output.toAbsolutePath().normalize(),
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw archiveError(ProfileErrorCode.ARCHIVE_OUTPUT_EXISTS,
                    "Profile 正式归档输出已存在，拒绝覆盖：" + output.toAbsolutePath().normalize(), null);
        }
        try {
            Path promoted = new ProfileArchivePromotionService().promote(candidate, reports, output);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", ProfileArchiveStatus.VERIFIED.name());
            result.put("profile", promoted.toString());
            System.out.println("PROFILE_PROMOTE_RESULT " + JSON.toJSONString(result));
        } catch (IOException | RuntimeException exception) {
            String message = messageOf(exception);
            ProfileErrorCode code = message.contains("门禁") || message.contains("扫描")
                    ? ProfileErrorCode.ARCHIVE_GATE_FAILED
                    : message.contains("candidate") || message.contains("候选")
                    ? ProfileErrorCode.ARCHIVE_CANDIDATE_REQUIRED
                    : ProfileErrorCode.ARCHIVE_INVALID;
            throw archiveError(code, "Profile promote 失败：" + message, exception);
        }
    }

    private static ProfileCommandException archiveError(ProfileErrorCode code, String message,
                                                         Throwable cause) {
        return new ProfileCommandException(code, message, cause);
    }

    private static String messageOf(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName() : message;
    }

    private static Map<String, String> parseOptions(String[] args) throws ProfileCommandException {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index += 2) {
            if (index + 1 >= args.length || !args[index].startsWith("--")) {
                throw new ProfileCommandException(ProfileErrorCode.MISSING_PARAMETER,
                        "Profile 命令参数格式错误：位置=" + index);
            }
            options.put(args[index], args[index + 1]);
        }
        return options;
    }

    private static String requireOption(Map<String, String> options, String name)
            throws ProfileCommandException {
        String value = optionalOption(options, name);
        if (value == null) {
            throw missingParameter(name);
        }
        return value;
    }

    private static String optionalOption(Map<String, String> options, String name) {
        String value = options.get(name);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static Path optionalPath(Map<String, String> options, String name) {
        String value = optionalOption(options, name);
        return value == null ? null : Paths.get(value);
    }

    private static ProfileCommandException missingParameter(String name) {
        return new ProfileCommandException(ProfileErrorCode.MISSING_PARAMETER,
                "缺少必填参数：" + name);
    }
}
