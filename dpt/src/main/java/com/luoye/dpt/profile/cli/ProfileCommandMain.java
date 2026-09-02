package com.luoye.dpt.profile.cli;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
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
        String profilesRoot = requireOption(options, "--profiles-root");
        if ("create".equals(args[0])) {
            String applicationId = requireOption(options, "--application-id");
            ProfileCreationResult result = new ProfileCreationService(Paths.get(profilesRoot))
                    .create(applicationId);
            System.out.println("PROFILE_CREATE_RESULT profileId=" + result.getProfileId()
                    + " created=" + result.isCreated());
            return;
        }
        if ("show".equals(args[0])) {
            String activeProfilesRoot = requireOption(options, "--active-profiles-root");
            ProfileShowResult result = new ProfileShowService(Paths.get(profilesRoot),
                    Paths.get(activeProfilesRoot)).show(optionalOption(options, "--application-id"),
                    optionalOption(options, "--profile-id"));
            System.out.println("PROFILE_SHOW_RESULT " + JSON.toJSONString(result,
                    JSONWriter.Feature.WriteNulls));
            return;
        }
        if ("build".equals(args[0])) {
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
        if ("assemble-candidate".equals(args[0])) {
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
        if ("publish".equals(args[0])) {
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
        if ("rollback".equals(args[0])) {
            String activeProfilesRoot = requireOption(options, "--active-profiles-root");
            String revisionId = requireOption(options, "--revision-id");
            ActiveRevisionPointer result = new ProfilePublishService(Paths.get(profilesRoot),
                    Paths.get(activeProfilesRoot)).rollback(optionalOption(options, "--application-id"),
                    optionalOption(options, "--profile-id"), revisionId);
            System.out.println("PROFILE_ROLLBACK_RESULT " + JSON.toJSONString(result));
            return;
        }
        throw new ProfileCommandException(ProfileErrorCode.MISSING_PARAMETER,
                "不支持的 Profile 命令：" + args[0]);
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
