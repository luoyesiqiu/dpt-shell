package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipe;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic identity for a generated Profile source result.
 *
 * <p>The key deliberately contains only build inputs. Revision IDs are not part of it, so a
 * retry or a new revision with byte-for-byte identical inputs can reuse a verified source tree.
 * The cache itself remains namespaced by Profile ID.</p>
 */
public final class ProfileCandidateCacheKey {
    // The generated tree now includes the Profile-specific Native junk-class contract. Bump the
    // namespace so trees generated before that contract cannot be restored into a new revision.
    public static final String VERSION = "profile-cache-key-v4";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final String profileId;
    private final String applicationId;
    private final String identityLockSha256;
    private final String recipeId;
    private final int recipeProtocolVersion;
    private final String javaCompositionId;
    private final String nativeCompositionId;
    private final String recipeVerificationStatus;
    private final String overlaySha256;
    private final String generatorVersion;
    private final String coreVersion;
    private final String toolchainVersion;
    private final String key;

    private ProfileCandidateCacheKey(String profileId, String applicationId,
                                     String identityLockSha256, ProfileRecipe recipe,
                                     String overlaySha256, String generatorVersion,
                                     String coreVersion, String toolchainVersion) {
        this.profileId = requireProfileId(profileId);
        this.applicationId = requireApplicationId(applicationId);
        this.identityLockSha256 = requireSha("identityLockSha256", identityLockSha256);
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe 不能为空");
        }
        this.recipeId = requireText("recipeId", recipe.getRecipeId());
        this.recipeProtocolVersion = recipe.getProtocolVersion();
        this.javaCompositionId = requireText("javaCompositionId", recipe.getJavaCompositionId());
        this.nativeCompositionId = requireText("nativeCompositionId", recipe.getNativeCompositionId());
        if (recipe.getVerificationStatus() == null) {
            throw new IllegalArgumentException("recipeVerificationStatus 不能为空");
        }
        this.recipeVerificationStatus = recipe.getVerificationStatus().name();
        this.overlaySha256 = requireSha("overlaySha256", overlaySha256);
        this.generatorVersion = requireText("generatorVersion", generatorVersion);
        this.coreVersion = requireText("coreVersion", coreVersion);
        this.toolchainVersion = requireText("toolchainVersion", toolchainVersion);
        this.key = calculateKey();
    }

    public static ProfileCandidateCacheKey from(ProfileIdentityLock identity, ProfileRecipe recipe,
                                                 String overlaySha256, String generatorVersion,
                                                 String coreVersion, String toolchainVersion) {
        if (identity == null) {
            throw new IllegalArgumentException("Profile identity 不能为空");
        }
        ProfileModelValidator.validate(identity);
        byte[] identityBytes = JSON.toJSONString(identity).getBytes(StandardCharsets.UTF_8);
        return new ProfileCandidateCacheKey(identity.getProfileId(), identity.getApplicationId(),
                MessageDigestUtils.sha256(identityBytes), recipe, overlaySha256, generatorVersion,
                coreVersion, toolchainVersion);
    }

    public static ProfileCandidateCacheKey from(ProfileIdentityLock identity,
                                                 String identityLockSha256, ProfileRecipe recipe,
                                                 String overlaySha256, String generatorVersion,
                                                 String coreVersion, String toolchainVersion) {
        if (identity == null) {
            throw new IllegalArgumentException("Profile identity 不能为空");
        }
        ProfileModelValidator.validate(identity);
        return new ProfileCandidateCacheKey(identity.getProfileId(), identity.getApplicationId(),
                identityLockSha256, recipe, overlaySha256, generatorVersion, coreVersion,
                toolchainVersion);
    }

    public static ProfileCandidateCacheKey from(String profileId, String applicationId,
                                                 String identityLockSha256, ProfileRecipe recipe,
                                                 String overlaySha256, String generatorVersion,
                                                 String coreVersion, String toolchainVersion) {
        return new ProfileCandidateCacheKey(profileId, applicationId, identityLockSha256, recipe,
                overlaySha256, generatorVersion, coreVersion, toolchainVersion);
    }

    public String getKey() {
        return key;
    }

    public String getCacheKey() {
        return key;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getIdentityLockSha256() {
        return identityLockSha256;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public int getRecipeProtocolVersion() {
        return recipeProtocolVersion;
    }

    public String getJavaCompositionId() {
        return javaCompositionId;
    }

    public String getNativeCompositionId() {
        return nativeCompositionId;
    }

    public String getRecipeVerificationStatus() {
        return recipeVerificationStatus;
    }

    public String getOverlaySha256() {
        return overlaySha256;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    public String getCoreVersion() {
        return coreVersion;
    }

    public String getToolchainVersion() {
        return toolchainVersion;
    }

    private String calculateKey() {
        String canonical = VERSION + "\n"
                + field("profileId", profileId)
                + field("applicationId", applicationId)
                + field("identityLockSha256", identityLockSha256)
                + field("recipeId", recipeId)
                + field("recipeProtocolVersion", Integer.toString(recipeProtocolVersion))
                + field("javaCompositionId", javaCompositionId)
                + field("nativeCompositionId", nativeCompositionId)
                + field("recipeVerificationStatus", recipeVerificationStatus)
                + field("overlaySha256", overlaySha256)
                + field("generatorVersion", generatorVersion)
                + field("coreVersion", coreVersion)
                + field("toolchainVersion", toolchainVersion)
                + field("abi", ProfileCodeGenerator.PROFILE_ABI);
        return MessageDigestUtils.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }


    private static String field(String name, String value) {
        return name + "=" + value.length() + ":" + value + "\n";
    }

    private static String requireProfileId(String value) {
        ProfileModelValidator.validateProfileId(value);
        return value;
    }

    private static String requireApplicationId(String value) {
        ProfileModelValidator.validateApplicationId(value);
        return value;
    }

    private static String requireSha(String fieldName, String value) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " 必须为 64 位小写 SHA-256");
        }
        return value;
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProfileCandidateCacheKey)) {
            return false;
        }
        ProfileCandidateCacheKey that = (ProfileCandidateCacheKey) other;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return key;
    }
}
