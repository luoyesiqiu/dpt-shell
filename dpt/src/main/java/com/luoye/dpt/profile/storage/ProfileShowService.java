package com.luoye.dpt.profile.storage;

import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSourceConfig;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProfileShowService {
    private final ProfileRegistry registry;
    private final ProfileIdentityStore identityStore;
    private final ProfileSourceConfigStore sourceConfigStore;
    private final ProfileRevisionStore revisionStore;
    private final ProfileActiveRevisionStore activeRevisionStore;

    public ProfileShowService(Path profilesRoot, Path activeProfilesRoot) {
        if (profilesRoot == null || activeProfilesRoot == null) {
            throw new IllegalArgumentException("profile show roots must not be null");
        }
        this.registry = new ProfileRegistry(profilesRoot);
        this.identityStore = new ProfileIdentityStore(profilesRoot);
        this.sourceConfigStore = new ProfileSourceConfigStore(profilesRoot);
        this.revisionStore = new ProfileRevisionStore(profilesRoot);
        this.activeRevisionStore = new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot,
                revisionStore);
    }

    public ProfileShowResult showByApplicationId(String applicationId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        Optional<String> profileId = registry.findProfileId(applicationId);
        if (!profileId.isPresent()) {
            throw new ProfileCommandException(ProfileErrorCode.PROFILE_NOT_FOUND,
                    "Profile 不存在：applicationId=" + applicationId);
        }
        return load(applicationId, profileId.get());
    }

    public ProfileShowResult showByProfileId(String profileId) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        Optional<String> applicationId = registry.findApplicationId(profileId);
        if (!applicationId.isPresent()) {
            throw new ProfileCommandException(ProfileErrorCode.PROFILE_NOT_FOUND,
                    "Profile 不存在：profileId=" + profileId);
        }
        return load(applicationId.get(), profileId);
    }

    public ProfileShowResult show(String applicationId, String profileId) throws IOException {
        if (applicationId == null && profileId == null) {
            throw new ProfileCommandException(ProfileErrorCode.MISSING_PARAMETER,
                    "缺少必填参数：applicationId 或 profileId");
        }
        if (applicationId == null) {
            return showByProfileId(profileId);
        }
        ProfileShowResult result = showByApplicationId(applicationId);
        if (profileId != null && !profileId.equals(result.getProfileId())) {
            throw new IOException("profile binding conflict for applicationId: " + applicationId);
        }
        return result;
    }

    private ProfileShowResult load(String applicationId, String profileId) throws IOException {
        ProfileIdentityLock identity = identityStore.loadExisting(applicationId, profileId);
        ProfileSourceConfig sourceConfig = sourceConfigStore.loadExisting(applicationId, profileId);
        List<ProfileRevisionManifest> revisions = revisionStore.list(profileId);
        String activeRevisionId = loadActiveRevisionIdIfPresent(profileId);
        return new ProfileShowResult(applicationId, profileId, summarize(identity), sourceConfig.getAbis(),
                activeRevisionId, summarizeRevisions(revisions));
    }

    private String loadActiveRevisionIdIfPresent(String profileId) throws IOException {
        Path pointerPath = activeRevisionStore.getActivePointerPath(profileId);
        if (!Files.exists(pointerPath)) {
            return null;
        }
        if (!Files.isRegularFile(pointerPath)) {
            throw new IOException("active revision pointer is not a file: " + pointerPath);
        }
        ActiveRevisionPointer pointer = activeRevisionStore.loadActive(profileId);
        return pointer.getRevisionId();
    }

    private static ProfileShowResult.IdentitySummary summarize(ProfileIdentityLock identity) {
        List<String> semanticIds = new ArrayList<>();
        for (JniMethodMapping method : identity.getJniMethods()) {
            semanticIds.add(method.getSemanticId());
        }
        return new ProfileShowResult.IdentitySummary(identity.getShellPackageName(),
                identity.getProxyApplicationClassName(), identity.getProxyComponentFactoryClassName(),
                identity.getJniBridgeClassName(), identity.getSoName(), identity.getShellConfigAssetName(),
                identity.getDexAssetName(), identity.getCodeItemAssetName(),
                identity.getNativeLibAssetDirName(), identity.getRuntimeDirName(), semanticIds);
    }

    private static List<ProfileShowResult.RevisionSummary> summarizeRevisions(
            List<ProfileRevisionManifest> revisions) {
        List<ProfileShowResult.RevisionSummary> summaries = new ArrayList<>();
        for (ProfileRevisionManifest revision : revisions) {
            summaries.add(new ProfileShowResult.RevisionSummary(revision.getRevisionId(),
                    revision.getStatus().name(), revision.getRecipeId(),
                    gateStatus(revision.getStatus()), revision.getGateReportSha256()));
        }
        return summaries;
    }

    private static String gateStatus(RevisionStatus status) {
        switch (status) {
            case ACTIVE:
            case VERIFIED:
                return "PASSED";
            case REJECTED:
                return "FAILED";
            case CANDIDATE:
                return "PENDING";
            default:
                throw new IllegalStateException("unsupported revision status: " + status);
        }
    }
}
