package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.validation.ProfileValidationException;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ProfileIdentityStore {
    private static final String IDENTITY_LOCK_FILE_NAME = "identity.lock.json";
    private static final String IDENTITY_HASH_FILE_NAME = "identity.lock.sha256";
    private static final String SOURCE_DIRECTORY_NAME = "source";
    private final Path profilesRoot;
    private final SecureRandom secureRandom;

    public ProfileIdentityStore(Path profilesRoot) {
        this(profilesRoot, new SecureRandom());
    }

    ProfileIdentityStore(Path profilesRoot, SecureRandom secureRandom) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profilesRoot must not be null");
        }
        this.profilesRoot = profilesRoot;
        this.secureRandom = secureRandom;
    }

    public ProfileIdentityLock createIfAbsent(String applicationId, String profileId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        ProfileModelValidator.validateProfileId(profileId);

        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            Path identityPath = getIdentityLockPath(profileId);
            if (Files.exists(identityPath)) {
                return loadIdentity(identityPath, applicationId, profileId);
            }

            ProfileIdentityLock identity = generateIdentity(applicationId, profileId);
            ProfileModelValidator.validate(identity);
            writeIdentity(identityPath, identity);
            return identity;
        }
    }

    public ProfileIdentityLock loadExisting(String applicationId, String profileId) throws IOException {
        ProfileModelValidator.validateApplicationId(applicationId);
        ProfileModelValidator.validateProfileId(profileId);

        try (ProfileFileLock ignored = ProfileFileLock.acquire(profilesRoot, profileId)) {
            Path identityPath = getIdentityLockPath(profileId);
            if (!Files.isRegularFile(identityPath)) {
                throw new IOException("identity lock is missing: " + identityPath);
            }
            return loadIdentity(identityPath, applicationId, profileId);
        }
    }

    public Path getIdentityLockPath(String profileId) {
        ProfileModelValidator.validateProfileId(profileId);
        return profilesRoot.resolve(SOURCE_DIRECTORY_NAME).resolve(profileId)
                .resolve(IDENTITY_LOCK_FILE_NAME);
    }

    public Path getIdentityHashPath(String profileId) {
        ProfileModelValidator.validateProfileId(profileId);
        return profilesRoot.resolve(SOURCE_DIRECTORY_NAME).resolve(profileId)
                .resolve(IDENTITY_HASH_FILE_NAME);
    }

    private ProfileIdentityLock generateIdentity(String applicationId, String profileId) {
        String identitySeed = randomHex(16);
        String shellPackageName = "p.n" + derive(identitySeed, "shell-package", 14);
        ProfileIdentityLock identity = new ProfileIdentityLock();
        identity.setProfileId(profileId);
        identity.setApplicationId(applicationId);
        identity.setIdentitySeed(identitySeed);
        identity.setShellPackageName(shellPackageName);
        identity.setProxyApplicationClassName(shellPackageName + ".A"
                + derive(identitySeed, "proxy-application", 12));
        identity.setProxyComponentFactoryClassName(shellPackageName + ".C"
                + derive(identitySeed, "proxy-component-factory", 12));
        identity.setJniBridgeClassName(shellPackageName + ".B"
                + derive(identitySeed, "jni-bridge", 12));
        identity.setSoName("lib" + derive(identitySeed, "so-name", 16) + ".so");
        identity.setShellConfigAssetName("a" + derive(identitySeed, "shell-config-asset", 16));
        identity.setDexAssetName("d" + derive(identitySeed, "dex-asset", 16) + ".zip");
        identity.setCodeItemAssetName("c" + derive(identitySeed, "code-item-asset", 16));
        identity.setNativeLibAssetDirName("n" + derive(identitySeed, "native-lib-dir", 16));
        identity.setRuntimeDirName("r" + derive(identitySeed, "runtime-dir", 16));
        identity.setJunkClassName(shellPackageName + ".J"
                + derive(identitySeed, "junk-class", 12));
        identity.setRc4KeySymbol("k" + derive(identitySeed, "rc4-key-symbol", 16));
        identity.setBuildKey(derive(identitySeed, "build-key", 16));
        identity.setJniMethods(generateJniMethods(identitySeed));
        return identity;
    }

    private List<JniMethodMapping> generateJniMethods(String identitySeed) {
        List<JniMethodMapping> methods = new ArrayList<>();
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            methods.add(new JniMethodMapping(semantic.getSemanticId(),
                    "m" + derive(identitySeed, "jni-" + semantic.getSemanticId(), 16),
                    semantic.getJvmSignature()));
        }
        return methods;
    }

    private ProfileIdentityLock loadIdentity(Path identityPath, String applicationId, String profileId)
            throws IOException {
        Path hashPath = getIdentityHashPath(profileId);
        if (!Files.isRegularFile(hashPath)) {
            throw new IOException("identity lock sha256 is missing: " + hashPath);
        }

        try {
            byte[] content = Files.readAllBytes(identityPath);
            String expectedHash = Files.readString(hashPath, StandardCharsets.UTF_8).trim();
            if (!expectedHash.matches("[0-9a-f]{64}")) {
                throw new IOException("identity lock sha256 has invalid format: " + hashPath);
            }
            String actualHash = MessageDigestUtils.sha256(content);
            if (!expectedHash.equals(actualHash)) {
                throw new IOException("identity lock sha256 mismatch: " + identityPath);
            }

            ProfileIdentityLock identity = JSON.parseObject(new String(content, StandardCharsets.UTF_8),
                    ProfileIdentityLock.class);
            if (identity == null) {
                throw new IOException("identity lock is empty: " + identityPath);
            }
            ProfileModelValidator.validate(identity);
            if (!profileId.equals(identity.getProfileId())
                    || !applicationId.equals(identity.getApplicationId())) {
                throw new IOException("identity lock applicationId conflict for profileId: " + profileId);
            }
            return identity;
        } catch (IOException e) {
            throw e;
        } catch (ProfileValidationException e) {
            throw new IOException("identity lock validation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("cannot parse identity lock: " + identityPath, e);
        }
    }

    private void writeIdentity(Path identityPath, ProfileIdentityLock identity) throws IOException {
        Files.createDirectories(identityPath.getParent());
        byte[] content = JSON.toJSONString(identity).getBytes(StandardCharsets.UTF_8);
        writeAtomically(identityPath, content);
        writeAtomically(getIdentityHashPath(identity.getProfileId()),
                (MessageDigestUtils.sha256(content) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAtomically(Path targetPath, byte[] content) throws IOException {
        Files.createDirectories(targetPath.getParent());
        Path temporaryPath = Files.createTempFile(targetPath.getParent(),
                "." + targetPath.getFileName() + ".", ".tmp");
        try {
            Files.write(temporaryPath, content, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporaryPath, targetPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("atomic identity replacement is not supported: " + targetPath, e);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private String randomHex(int byteCount) {
        byte[] value = new byte[byteCount];
        secureRandom.nextBytes(value);
        return ProfileIdGenerator.toHex(value);
    }

    private static String derive(String identitySeed, String label, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fromHex(identitySeed), "HmacSHA256"));
            String value = ProfileIdGenerator.toHex(mac.doFinal(label.getBytes(StandardCharsets.UTF_8)));
            return value.substring(0, length);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot derive profile identity", e);
        }
    }

    private static byte[] fromHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
