package com.luoye.dpt.profile.storage;

import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.overlay.ProfileOverlayException;
import com.luoye.dpt.profile.generation.overlay.ProfileOverlayProcessor;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeSelector;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplateRegistry;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSourceConfig;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProfileBuildService {
    private static final String DEFAULT_TOOLCHAIN_VERSION = "java-" +
            System.getProperty("java.specification.version", "unknown");
    private static final String EMPTY_GENERATED_SOURCE_SHA256 = MessageDigestUtils.sha256(new byte[0]);

    private final ProfileRegistry registry;
    private final ProfileIdentityStore identityStore;
    private final ProfileSourceConfigStore sourceConfigStore;
    private final ProfileRevisionStore revisionStore;
    private final ProfileCodeGenerator codeGenerator;
    private final ProfileRecipeSelector recipeSelector;
    private final ProfileCandidateCache candidateCache;
    private final String toolchainVersion;

    public ProfileBuildService(Path profilesRoot, Path activeProfilesRoot) {
        this(profilesRoot, activeProfilesRoot, resolveDefaultToolchainVersion());
    }

    public ProfileBuildService(Path profilesRoot, Path activeProfilesRoot, String toolchainVersion) {
        if (profilesRoot == null || activeProfilesRoot == null) {
            throw new IllegalArgumentException("profile build roots must not be null");
        }
        if (toolchainVersion == null || toolchainVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("toolchainVersion must not be blank");
        }
        this.registry = new ProfileRegistry(profilesRoot);
        this.identityStore = new ProfileIdentityStore(profilesRoot);
        this.sourceConfigStore = new ProfileSourceConfigStore(profilesRoot);
        this.revisionStore = new ProfileRevisionStore(profilesRoot);
        this.codeGenerator = new ProfileCodeGenerator(profilesRoot);
        this.recipeSelector = ProfileRecipeSelector.builtIn();
        this.candidateCache = new ProfileCandidateCache(profilesRoot);
        this.toolchainVersion = toolchainVersion.trim();
    }

    public ProfileRevisionManifest build(String applicationId, String profileId, String recipeId,
                                         Path overlayDir) throws IOException {
        Binding binding = resolveBinding(applicationId, profileId);
        ProfileIdentityLock identity = identityStore.loadExisting(binding.applicationId,
                binding.profileId);
        ProfileSourceConfig sourceConfig = sourceConfigStore.loadExisting(binding.applicationId,
                binding.profileId);
        String resolvedRecipeId = firstNonBlank(recipeId, sourceConfig.getRecipeId());
        ProfileRecipe recipe;
        try {
            List<ProfileRevisionManifest> history = resolvedRecipeId == null
                    ? loadRevisionHistory() : Collections.emptyList();
            recipe = recipeSelector.select(resolvedRecipeId, binding.applicationId, history);
        } catch (IllegalArgumentException e) {
            throw new ProfileCommandException(ProfileErrorCode.INVALID_CANDIDATE,
                    e.getMessage(), e);
        }

        Path resolvedOverlayDir = resolveOverlayDir(overlayDir, sourceConfig, binding.profileId);
        ProfileOverlayProcessor.PreparedOverlay preparedOverlay;
        try {
            preparedOverlay = new ProfileOverlayProcessor().prepare(resolvedOverlayDir, identity);
        } catch (ProfileOverlayException e) {
            throw invalidOverlay(e);
        }
        ProfileRevisionManifest candidate = new ProfileRevisionManifest();
        candidate.setProfileId(binding.profileId);
        candidate.setApplicationId(binding.applicationId);
        candidate.setRecipeId(recipe.getRecipeId());
        candidate.setOverlaySha256(preparedOverlay.getSha256());
        candidate.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        candidate.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        candidate.setToolchainVersion(toolchainVersion);
        candidate.setGeneratedSourceSha256(EMPTY_GENERATED_SOURCE_SHA256);

        // loadExisting() has already verified this digest; use the persisted value so the key
        // reflects the exact identity-lock bytes rather than a re-serialized object.
        String identityLockSha256 = java.nio.file.Files.readString(
                identityStore.getIdentityHashPath(binding.profileId),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        ProfileCandidateCacheKey cacheKey = ProfileCandidateCacheKey.from(identity,
                identityLockSha256, recipe, preparedOverlay.getSha256(),
                candidate.getGeneratorVersion(), candidate.getCoreVersion(), toolchainVersion);
        candidate.setCacheKey(cacheKey.getKey());

        // Loading the identity above is a strict integrity check before any candidate is persisted.
        if (!binding.profileId.equals(identity.getProfileId())) {
            throw new IOException("profile identity mismatch: " + binding.profileId);
        }
        ProfileRevisionManifest persistedCandidate = revisionStore.createCandidate(candidate);
        ProfileRecipeTemplate template = ProfileRecipeTemplateRegistry.builtIn().requireFor(recipe);
        ProfileCodeGenerator.GenerationLayout generationLayout;
        Optional<ProfileCandidateCache.CacheEntry> cached;
        try {
            generationLayout = codeGenerator.prepare(identity.getProfileId(),
                    persistedCandidate.getRevisionId());
            cached = candidateCache.restore(cacheKey, generationLayout.getRevisionRoot());
        } catch (IOException e) {
            try {
                revisionStore.markRejected(binding.profileId, persistedCandidate.getRevisionId());
            } catch (IOException transitionFailure) {
                e.addSuppressed(transitionFailure);
            }
            throw e;
        }
        if (cached.isPresent()) {
            return revisionStore.updateGeneratedSourceSha256(binding.profileId,
                    persistedCandidate.getRevisionId(), cached.get().getGeneratedSourceSha256());
        }
        try {
            generationLayout = codeGenerator.generate(identity, persistedCandidate.getRevisionId(),
                    template, preparedOverlay);
        } catch (ProfileOverlayException e) {
            try {
                revisionStore.markRejected(binding.profileId, persistedCandidate.getRevisionId());
            } catch (IOException transitionFailure) {
                e.addSuppressed(transitionFailure);
            }
            throw invalidOverlay(e);
        }
        try {
            ProfileCandidateCache.CacheEntry stored = candidateCache.store(cacheKey,
                    generationLayout.getRevisionRoot());
            if (!stored.getGeneratedSourceSha256().equals(generationLayout.getGeneratedSourceSha256())) {
                throw new IOException("generated source hash mismatch after cache store: "
                        + persistedCandidate.getRevisionId());
            }
        } catch (IOException e) {
            try {
                revisionStore.markRejected(binding.profileId, persistedCandidate.getRevisionId());
            } catch (IOException transitionFailure) {
                e.addSuppressed(transitionFailure);
            }
            throw e;
        }
        return revisionStore.updateGeneratedSourceSha256(binding.profileId,
                persistedCandidate.getRevisionId(), generationLayout.getGeneratedSourceSha256());
    }

    private static String resolveDefaultToolchainVersion() {
        String property = System.getProperty("dpt.profile.toolchainVersion");
        if (property != null && !property.trim().isEmpty()) {
            return property.trim();
        }
        String environment = System.getenv("DPT_PROFILE_TOOLCHAIN_VERSION");
        if (environment != null && !environment.trim().isEmpty()) {
            return environment.trim();
        }
        return DEFAULT_TOOLCHAIN_VERSION;
    }

    private List<ProfileRevisionManifest> loadRevisionHistory() throws IOException {
        List<ProfileRevisionManifest> history = new ArrayList<>();
        for (Map.Entry<String, String> binding : registry.listBindings().entrySet()) {
            history.addAll(revisionStore.list(binding.getValue()));
        }
        return history;
    }

    private Binding resolveBinding(String applicationId, String profileId) throws IOException {
        String normalizedApplicationId = blankToNull(applicationId);
        String normalizedProfileId = blankToNull(profileId);
        if (normalizedApplicationId == null && normalizedProfileId == null) {
            throw new ProfileCommandException(ProfileErrorCode.MISSING_PARAMETER,
                    "缺少必填参数：applicationId 或 profileId");
        }

        if (normalizedApplicationId != null) {
            ProfileModelValidator.validateApplicationId(normalizedApplicationId);
            Optional<String> registeredProfileId = registry.findProfileId(normalizedApplicationId);
            if (!registeredProfileId.isPresent()) {
                throw new ProfileCommandException(ProfileErrorCode.PROFILE_NOT_FOUND,
                        "Profile 不存在：applicationId=" + normalizedApplicationId);
            }
            if (normalizedProfileId != null && !normalizedProfileId.equals(registeredProfileId.get())) {
                throw new IOException("profile binding conflict for applicationId: "
                        + normalizedApplicationId);
            }
            return new Binding(normalizedApplicationId, registeredProfileId.get());
        }

        ProfileModelValidator.validateProfileId(normalizedProfileId);
        Optional<String> registeredApplicationId = registry.findApplicationId(normalizedProfileId);
        if (!registeredApplicationId.isPresent()) {
            throw new ProfileCommandException(ProfileErrorCode.PROFILE_NOT_FOUND,
                    "Profile 不存在：profileId=" + normalizedProfileId);
        }
        return new Binding(registeredApplicationId.get(), normalizedProfileId);
    }

    private Path resolveOverlayDir(Path override, ProfileSourceConfig sourceConfig, String profileId) {
        if (override != null) {
            return override.toAbsolutePath().normalize();
        }
        if (sourceConfig.getOverlayDir() == null) {
            return null;
        }
        return sourceConfigStore.getSourceConfigPath(profileId).getParent()
                .resolve(sourceConfig.getOverlayDir()).normalize();
    }

    private static ProfileCommandException invalidOverlay(ProfileOverlayException cause) {
        return new ProfileCommandException(ProfileErrorCode.INVALID_CANDIDATE,
                cause.getMessage(), cause);
    }

    private static String firstNonBlank(String first, String second) {
        String normalizedFirst = blankToNull(first);
        return normalizedFirst != null ? normalizedFirst : blankToNull(second);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static final class Binding {
        private final String applicationId;
        private final String profileId;

        private Binding(String applicationId, String profileId) {
            this.applicationId = applicationId;
            this.profileId = profileId;
        }
    }
}
