package com.luoye.dpt.profile.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileShowResult {
    private final String applicationId;
    private final String profileId;
    private final IdentitySummary identity;
    private final List<String> abis;
    private final String activeRevisionId;
    private final List<RevisionSummary> revisions;

    public ProfileShowResult(String applicationId, String profileId, IdentitySummary identity,
                             List<String> abis, String activeRevisionId,
                             List<RevisionSummary> revisions) {
        this.applicationId = applicationId;
        this.profileId = profileId;
        this.identity = identity;
        this.abis = Collections.unmodifiableList(new ArrayList<>(abis));
        this.activeRevisionId = activeRevisionId;
        this.revisions = Collections.unmodifiableList(new ArrayList<>(revisions));
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getProfileId() {
        return profileId;
    }

    public IdentitySummary getIdentity() {
        return identity;
    }

    public List<String> getAbis() {
        return abis;
    }

    public String getActiveRevisionId() {
        return activeRevisionId;
    }

    public List<RevisionSummary> getRevisions() {
        return revisions;
    }

    public static final class IdentitySummary {
        private final String shellPackageName;
        private final String proxyApplicationClassName;
        private final String proxyComponentFactoryClassName;
        private final String jniBridgeClassName;
        private final String soName;
        private final String shellConfigAssetName;
        private final String dexAssetName;
        private final String codeItemAssetName;
        private final String nativeLibAssetDirName;
        private final String runtimeDirName;
        private final List<String> jniSemanticIds;

        public IdentitySummary(String shellPackageName, String proxyApplicationClassName,
                               String proxyComponentFactoryClassName, String jniBridgeClassName,
                               String soName, String shellConfigAssetName, String dexAssetName,
                               String codeItemAssetName, String nativeLibAssetDirName,
                               String runtimeDirName, List<String> jniSemanticIds) {
            this.shellPackageName = shellPackageName;
            this.proxyApplicationClassName = proxyApplicationClassName;
            this.proxyComponentFactoryClassName = proxyComponentFactoryClassName;
            this.jniBridgeClassName = jniBridgeClassName;
            this.soName = soName;
            this.shellConfigAssetName = shellConfigAssetName;
            this.dexAssetName = dexAssetName;
            this.codeItemAssetName = codeItemAssetName;
            this.nativeLibAssetDirName = nativeLibAssetDirName;
            this.runtimeDirName = runtimeDirName;
            this.jniSemanticIds = Collections.unmodifiableList(new ArrayList<>(jniSemanticIds));
        }

        public String getShellPackageName() {
            return shellPackageName;
        }

        public String getProxyApplicationClassName() {
            return proxyApplicationClassName;
        }

        public String getProxyComponentFactoryClassName() {
            return proxyComponentFactoryClassName;
        }

        public String getJniBridgeClassName() {
            return jniBridgeClassName;
        }

        public String getSoName() {
            return soName;
        }

        public String getShellConfigAssetName() {
            return shellConfigAssetName;
        }

        public String getDexAssetName() {
            return dexAssetName;
        }

        public String getCodeItemAssetName() {
            return codeItemAssetName;
        }

        public String getNativeLibAssetDirName() {
            return nativeLibAssetDirName;
        }

        public String getRuntimeDirName() {
            return runtimeDirName;
        }

        public List<String> getJniSemanticIds() {
            return jniSemanticIds;
        }
    }

    public static final class RevisionSummary {
        private final String revisionId;
        private final String status;
        private final String recipeId;
        private final String gateStatus;
        private final String gateReportSha256;

        public RevisionSummary(String revisionId, String status, String recipeId,
                               String gateStatus, String gateReportSha256) {
            this.revisionId = revisionId;
            this.status = status;
            this.recipeId = recipeId;
            this.gateStatus = gateStatus;
            this.gateReportSha256 = gateReportSha256;
        }

        public String getRevisionId() {
            return revisionId;
        }

        public String getStatus() {
            return status;
        }

        public String getRecipeId() {
            return recipeId;
        }

        public String getGateStatus() {
            return gateStatus;
        }

        public String getGateReportSha256() {
            return gateReportSha256;
        }
    }
}
