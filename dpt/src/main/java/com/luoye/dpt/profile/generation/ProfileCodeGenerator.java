package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.overlay.ProfileOverlayProcessor;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;
import com.luoye.dpt.util.HexUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProfileCodeGenerator {
    public static final String GENERATOR_VERSION = "profile-generator-v1";
    public static final String IMMUTABLE_CORE_VERSION = ProfileNativeCoreContract.VERSION;
    public static final String PROFILE_ABI = "arm64-v8a";

    private static final String GENERATED_DIRECTORY_NAME = "generated";
    private static final String MANIFEST_FILE_NAME = "AndroidManifest.xml";
    private static final String EMPTY_SOURCE_SHA256 = MessageDigestUtils.sha256(new byte[0]);

    private final Path generatedRoot;

    public ProfileCodeGenerator(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profiles root must not be null");
        }
        this.generatedRoot = profilesRoot.resolve(GENERATED_DIRECTORY_NAME);
    }

    public GenerationLayout prepare(String profileId, String revisionId) throws IOException {
        ProfileModelValidator.validateProfileId(profileId);
        ProfileModelValidator.validateRevisionId(revisionId);

        Path revisionRoot = generatedRoot.resolve(profileId).resolve(revisionId);
        Path javaDirectory = Files.createDirectories(revisionRoot.resolve("java"));
        Path cppDirectory = Files.createDirectories(revisionRoot.resolve("cpp"));
        Path r8Directory = Files.createDirectories(revisionRoot.resolve("r8"));
        Path cmakeDirectory = Files.createDirectories(revisionRoot.resolve("cmake"));
        Path manifestDirectory = Files.createDirectories(revisionRoot.resolve("manifest"));
        return new GenerationLayout(revisionRoot, javaDirectory, cppDirectory, r8Directory,
                cmakeDirectory, manifestDirectory.resolve(MANIFEST_FILE_NAME),
                EMPTY_SOURCE_SHA256);
    }

    public GenerationLayout generateJava(ProfileIdentityLock identity, String revisionId,
                                         ProfileRecipeTemplate template) throws IOException {
        ProfileModelValidator.validate(identity);
        GenerationLayout layout = prepare(identity.getProfileId(), revisionId);
        new ProfileJavaSourceGenerator().generate(layout, identity, template);
        writeManifest(layout, identity);
        return layout.withGeneratedSourceSha256(hashGeneratedSources(layout.revisionRoot));
    }

    public GenerationLayout generate(ProfileIdentityLock identity, String revisionId,
                                     ProfileRecipeTemplate template) throws IOException {
        return generate(identity, revisionId, template, null);
    }

    public GenerationLayout generate(ProfileIdentityLock identity, String revisionId,
                                     ProfileRecipeTemplate template,
                                     ProfileOverlayProcessor.PreparedOverlay overlay)
            throws IOException {
        ProfileModelValidator.validate(identity);
        GenerationLayout layout = prepare(identity.getProfileId(), revisionId);
        new ProfileJavaSourceGenerator().generate(layout, identity, template);
        writeManifest(layout, identity);
        List<String> overlaySourceFileNames = overlay == null
                ? Collections.emptyList() : overlay.getCppSourcePaths();
        new ProfileNativeSourceGenerator().generate(layout, identity, template,
                overlaySourceFileNames);
        if (overlay != null) {
            overlay.copyTo(layout);
        }
        return layout.withGeneratedSourceSha256(hashGeneratedSources(layout.revisionRoot));
    }

    private static void writeManifest(GenerationLayout layout, ProfileIdentityLock identity)
            throws IOException {
        String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <application\n"
                + "        android:appComponentFactory=\""
                + escapeXml(identity.getProxyComponentFactoryClassName()) + "\"\n"
                + "        android:name=\"" + escapeXml(identity.getProxyApplicationClassName())
                + "\" />\n"
                + "</manifest>\n";
        Files.writeString(layout.getManifestFile(), content, StandardCharsets.UTF_8);
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;");
    }

    private static String hashGeneratedSources(Path revisionRoot) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(revisionRoot)) {
            files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> revisionRoot.relativize(path).toString()))
                    .collect(Collectors.toList());
        }
        for (Path file : files) {
            String relativePath = revisionRoot.relativize(file).toString()
                    .replace(file.getFileSystem().getSeparator(), "/");
            digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return HexUtils.toHexString(digest.digest());
    }

    public static final class GenerationLayout {
        private final Path revisionRoot;
        private final Path javaDirectory;
        private final Path cppDirectory;
        private final Path r8Directory;
        private final Path cmakeDirectory;
        private final Path manifestFile;
        private final String generatedSourceSha256;

        private GenerationLayout(Path revisionRoot, Path javaDirectory, Path cppDirectory,
                                 Path r8Directory, Path cmakeDirectory,
                                 Path manifestFile,
                                 String generatedSourceSha256) {
            this.revisionRoot = revisionRoot;
            this.javaDirectory = javaDirectory;
            this.cppDirectory = cppDirectory;
            this.r8Directory = r8Directory;
            this.cmakeDirectory = cmakeDirectory;
            this.manifestFile = manifestFile;
            this.generatedSourceSha256 = generatedSourceSha256;
        }

        public Path getRevisionRoot() {
            return revisionRoot;
        }

        public Path getJavaDirectory() {
            return javaDirectory;
        }

        public Path getCppDirectory() {
            return cppDirectory;
        }

        public Path getR8Directory() {
            return r8Directory;
        }

        public Path getCmakeDirectory() {
            return cmakeDirectory;
        }

        public Path getManifestFile() {
            return manifestFile;
        }

        public String getGeneratedSourceSha256() {
            return generatedSourceSha256;
        }

        private GenerationLayout withGeneratedSourceSha256(String value) {
            return new GenerationLayout(revisionRoot, javaDirectory, cppDirectory, r8Directory,
                    cmakeDirectory, manifestFile, value);
        }
    }
}
