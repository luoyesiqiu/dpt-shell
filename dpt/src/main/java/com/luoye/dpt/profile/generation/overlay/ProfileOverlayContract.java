package com.luoye.dpt.profile.generation.overlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileOverlayContract {
    public static final String JAVA_STARTUP_COORDINATION = "java.startup-coordination";
    public static final String JAVA_SO_HANDLING = "java.so-handling";
    public static final String JAVA_PATH_CONFIG = "java.path-config";
    public static final String JAVA_PROFILE_PRIVATE = "java.profile-private";
    public static final String CPP_STARTUP_COORDINATION = "cpp.startup-coordination";
    public static final String CPP_PATH_CONFIG = "cpp.path-config";
    public static final String CPP_PROFILE_PRIVATE = "cpp.profile-private";

    private static final String CONTRACT_VERSION = "profile-overlay-v1";
    private static final List<String> JAVA_SUFFIXES = Collections.singletonList(".java");
    private static final List<String> CPP_SUFFIXES = Collections.unmodifiableList(
            Arrays.asList(".cpp", ".h", ".hpp"));
    private static final ProfileOverlayContract BUILT_IN = new ProfileOverlayContract(
            CONTRACT_VERSION, Arrays.asList(
            point(JAVA_STARTUP_COORDINATION, Language.JAVA, Capability.STARTUP_COORDINATION,
                    "java/startup-coordination", JAVA_SUFFIXES),
            point(JAVA_SO_HANDLING, Language.JAVA, Capability.SO_HANDLING,
                    "java/so-handling", JAVA_SUFFIXES),
            point(JAVA_PATH_CONFIG, Language.JAVA, Capability.PATH_CONFIG,
                    "java/path-config", JAVA_SUFFIXES),
            point(JAVA_PROFILE_PRIVATE, Language.JAVA, Capability.PROFILE_PRIVATE,
                    "java/profile-private", JAVA_SUFFIXES),
            point(CPP_STARTUP_COORDINATION, Language.CPP, Capability.STARTUP_COORDINATION,
                    "cpp/startup-coordination", CPP_SUFFIXES),
            point(CPP_PATH_CONFIG, Language.CPP, Capability.PATH_CONFIG,
                    "cpp/path-config", CPP_SUFFIXES),
            point(CPP_PROFILE_PRIVATE, Language.CPP, Capability.PROFILE_PRIVATE,
                    "cpp/profile-private", CPP_SUFFIXES)));

    private final String version;
    private final List<ExtensionPoint> extensionPoints;
    private final Map<String, ExtensionPoint> byInterfaceId;
    private final Map<String, ExtensionPoint> byDirectory;

    private ProfileOverlayContract(String version, List<ExtensionPoint> extensionPoints) {
        this.version = version;
        this.extensionPoints = Collections.unmodifiableList(new ArrayList<>(extensionPoints));
        LinkedHashMap<String, ExtensionPoint> interfaceIndex = new LinkedHashMap<>();
        LinkedHashMap<String, ExtensionPoint> directoryIndex = new LinkedHashMap<>();
        for (ExtensionPoint extensionPoint : extensionPoints) {
            if (interfaceIndex.put(extensionPoint.getInterfaceId(), extensionPoint) != null
                    || directoryIndex.put(extensionPoint.getRelativeDirectory(), extensionPoint) != null) {
                throw new IllegalArgumentException("Overlay 扩展接口或目录重复");
            }
        }
        byInterfaceId = Collections.unmodifiableMap(interfaceIndex);
        byDirectory = Collections.unmodifiableMap(directoryIndex);
    }

    public static ProfileOverlayContract builtIn() {
        return BUILT_IN;
    }

    public String getVersion() {
        return version;
    }

    public List<ExtensionPoint> getExtensionPoints() {
        return extensionPoints;
    }

    public ExtensionPoint requireInterface(String interfaceId) {
        ExtensionPoint extensionPoint = byInterfaceId.get(interfaceId);
        if (extensionPoint == null) {
            throw new IllegalArgumentException("Overlay 扩展接口未登记：" + interfaceId);
        }
        return extensionPoint;
    }

    public ExtensionPoint requireDirectory(String relativeDirectory) {
        ExtensionPoint extensionPoint = byDirectory.get(relativeDirectory);
        if (extensionPoint == null) {
            throw new IllegalArgumentException("Overlay 目录未登记：" + relativeDirectory);
        }
        return extensionPoint;
    }

    private static ExtensionPoint point(String interfaceId, Language language,
                                        Capability capability, String relativeDirectory,
                                        List<String> sourceSuffixes) {
        return new ExtensionPoint(interfaceId, language, capability, relativeDirectory,
                sourceSuffixes);
    }

    public enum Language {
        JAVA,
        CPP
    }

    public enum Capability {
        STARTUP_COORDINATION,
        SO_HANDLING,
        PATH_CONFIG,
        PROFILE_PRIVATE
    }

    public static final class ExtensionPoint {
        private final String interfaceId;
        private final Language language;
        private final Capability capability;
        private final String relativeDirectory;
        private final List<String> sourceSuffixes;

        private ExtensionPoint(String interfaceId, Language language, Capability capability,
                               String relativeDirectory, List<String> sourceSuffixes) {
            this.interfaceId = interfaceId;
            this.language = language;
            this.capability = capability;
            this.relativeDirectory = relativeDirectory;
            this.sourceSuffixes = Collections.unmodifiableList(new ArrayList<>(sourceSuffixes));
        }

        public String getInterfaceId() {
            return interfaceId;
        }

        public Language getLanguage() {
            return language;
        }

        public Capability getCapability() {
            return capability;
        }

        public String getRelativeDirectory() {
            return relativeDirectory;
        }

        public List<String> getSourceSuffixes() {
            return sourceSuffixes;
        }
    }
}
