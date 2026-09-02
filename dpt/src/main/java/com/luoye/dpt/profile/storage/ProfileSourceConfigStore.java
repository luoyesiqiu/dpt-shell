package com.luoye.dpt.profile.storage;

import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.ProfileSourceConfig;
import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ProfileSourceConfigStore {
    private static final String SOURCE_DIRECTORY_NAME = "source";
    private static final String SOURCE_CONFIG_FILE_NAME = "profile.yaml";

    private final Path profilesRoot;

    public ProfileSourceConfigStore(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        this.profilesRoot = profilesRoot;
    }

    public void createIfAbsent(String applicationId, String profileId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        ProfileModelValidator.validateProfileId(profileId);

        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            Path sourceConfigPath = getSourceConfigPath(profileId);
            if (Files.exists(sourceConfigPath)) {
                if (!Files.isRegularFile(sourceConfigPath)) {
                    throw new IOException("profile source config is not a file: " + sourceConfigPath);
                }
                return;
            }

            ProfileSourceConfig sourceConfig = new ProfileSourceConfig();
            sourceConfig.setApplicationId(applicationId);
            sourceConfig.setAbis(Collections.singletonList("arm64-v8a"));
            ProfileModelValidator.validate(sourceConfig);
            writeAtomically(sourceConfigPath, toYaml(sourceConfig));
        }
    }

    public ProfileSourceConfig loadExisting(String applicationId, String profileId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        ProfileModelValidator.validateProfileId(profileId);

        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            Path sourceConfigPath = getSourceConfigPath(profileId);
            if (!Files.isRegularFile(sourceConfigPath)) {
                throw new IOException("profile source config is missing: " + sourceConfigPath);
            }
            ProfileSourceConfig sourceConfig = parseYaml(Files.readString(sourceConfigPath,
                    StandardCharsets.UTF_8), sourceConfigPath);
            if (!applicationId.equals(sourceConfig.getApplicationId())) {
                throw new IOException("profile source config applicationId conflict for profileId: "
                        + profileId);
            }
            return sourceConfig;
        }
    }

    public Path getSourceConfigPath(String profileId) {
        ProfileModelValidator.validateProfileId(profileId);
        return profilesRoot.resolve(SOURCE_DIRECTORY_NAME).resolve(profileId)
                .resolve(SOURCE_CONFIG_FILE_NAME);
    }

    private static String toYaml(ProfileSourceConfig sourceConfig) {
        StringBuilder result = new StringBuilder();
        result.append("schemaVersion: ").append(ProfileSchemaVersions.SOURCE_CONFIG).append('\n');
        result.append("applicationId: ").append(sourceConfig.getApplicationId()).append('\n');
        if (sourceConfig.getRecipeId() != null) {
            result.append("recipeId: ").append(sourceConfig.getRecipeId()).append('\n');
        }
        if (sourceConfig.getOverlayDir() != null) {
            result.append("overlayDir: ").append(sourceConfig.getOverlayDir()).append('\n');
        }
        result.append("abis:\n");
        for (String abi : sourceConfig.getAbis()) {
            result.append("  - ").append(abi).append('\n');
        }
        return result.toString();
    }

    private static ProfileSourceConfig parseYaml(String content, Path sourceConfigPath)
            throws IOException {
        ProfileSourceConfig sourceConfig = new ProfileSourceConfig();
        sourceConfig.setAbis(new ArrayList<>());
        Set<String> keys = new HashSet<>();
        boolean inAbis = false;
        boolean hasSchemaVersion = false;
        boolean hasApplicationId = false;
        boolean hasAbis = false;

        String[] lines = content.split("\\R", -1);
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            if (line.startsWith("  - ")) {
                if (!inAbis) {
                    throw invalidSourceConfig(sourceConfigPath, "ABI entry is outside abis");
                }
                String abi = line.substring("  - ".length()).trim();
                if (abi.isEmpty()) {
                    throw invalidSourceConfig(sourceConfigPath, "ABI entry is empty");
                }
                sourceConfig.getAbis().add(abi);
                continue;
            }
            if (Character.isWhitespace(line.charAt(0))) {
                throw invalidSourceConfig(sourceConfigPath, "unexpected indentation");
            }
            inAbis = false;

            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw invalidSourceConfig(sourceConfigPath, "invalid key-value entry");
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!keys.add(key)) {
                throw invalidSourceConfig(sourceConfigPath, "duplicate key: " + key);
            }
            switch (key) {
                case "schemaVersion":
                    try {
                        sourceConfig.setSchemaVersion(Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        throw invalidSourceConfig(sourceConfigPath, "invalid schemaVersion");
                    }
                    hasSchemaVersion = true;
                    break;
                case "applicationId":
                    sourceConfig.setApplicationId(value);
                    hasApplicationId = true;
                    break;
                case "recipeId":
                    sourceConfig.setRecipeId(emptyToNull(value));
                    break;
                case "overlayDir":
                    sourceConfig.setOverlayDir(emptyToNull(value));
                    break;
                case "abis":
                    if (!value.isEmpty()) {
                        throw invalidSourceConfig(sourceConfigPath, "abis must be a YAML list");
                    }
                    inAbis = true;
                    hasAbis = true;
                    break;
                default:
                    throw invalidSourceConfig(sourceConfigPath, "unknown key: " + key);
            }
        }

        if (!hasSchemaVersion || !hasApplicationId || !hasAbis) {
            throw invalidSourceConfig(sourceConfigPath, "missing required key");
        }
        try {
            ProfileModelValidator.validate(sourceConfig);
            return sourceConfig;
        } catch (RuntimeException e) {
            throw new IOException("profile source config validation failed: " + sourceConfigPath, e);
        }
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static IOException invalidSourceConfig(Path sourceConfigPath, String message) {
        return new IOException("invalid profile source config " + sourceConfigPath + ": " + message);
    }

    private static void writeAtomically(Path targetPath, String content) throws IOException {
        Files.createDirectories(targetPath.getParent());
        Path temporaryPath = Files.createTempFile(targetPath.getParent(),
                "." + targetPath.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporaryPath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporaryPath, targetPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("atomic source config replacement is not supported: " + targetPath, e);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }
}
