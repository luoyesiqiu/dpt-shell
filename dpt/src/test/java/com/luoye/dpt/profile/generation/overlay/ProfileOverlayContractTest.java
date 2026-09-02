package com.luoye.dpt.profile.generation.overlay;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileOverlayContractTest {

    @Test
    public void definesOnlyApprovedVersionedJavaAndCppExtensionInterfaces() {
        ProfileOverlayContract contract = ProfileOverlayContract.builtIn();

        Assert.assertEquals("profile-overlay-v1", contract.getVersion());
        Assert.assertEquals(Arrays.asList(
                        ProfileOverlayContract.JAVA_STARTUP_COORDINATION,
                        ProfileOverlayContract.JAVA_SO_HANDLING,
                        ProfileOverlayContract.JAVA_PATH_CONFIG,
                        ProfileOverlayContract.JAVA_PROFILE_PRIVATE,
                        ProfileOverlayContract.CPP_STARTUP_COORDINATION,
                        ProfileOverlayContract.CPP_PATH_CONFIG,
                        ProfileOverlayContract.CPP_PROFILE_PRIVATE),
                contract.getExtensionPoints().stream()
                        .map(ProfileOverlayContract.ExtensionPoint::getInterfaceId)
                        .collect(Collectors.toList()));

        assertPoint(contract, ProfileOverlayContract.JAVA_STARTUP_COORDINATION,
                ProfileOverlayContract.Language.JAVA,
                ProfileOverlayContract.Capability.STARTUP_COORDINATION,
                "java/startup-coordination", Collections.singletonList(".java"));
        assertPoint(contract, ProfileOverlayContract.JAVA_SO_HANDLING,
                ProfileOverlayContract.Language.JAVA,
                ProfileOverlayContract.Capability.SO_HANDLING,
                "java/so-handling", Collections.singletonList(".java"));
        assertPoint(contract, ProfileOverlayContract.JAVA_PATH_CONFIG,
                ProfileOverlayContract.Language.JAVA,
                ProfileOverlayContract.Capability.PATH_CONFIG,
                "java/path-config", Collections.singletonList(".java"));
        assertPoint(contract, ProfileOverlayContract.JAVA_PROFILE_PRIVATE,
                ProfileOverlayContract.Language.JAVA,
                ProfileOverlayContract.Capability.PROFILE_PRIVATE,
                "java/profile-private", Collections.singletonList(".java"));
        assertPoint(contract, ProfileOverlayContract.CPP_STARTUP_COORDINATION,
                ProfileOverlayContract.Language.CPP,
                ProfileOverlayContract.Capability.STARTUP_COORDINATION,
                "cpp/startup-coordination", Arrays.asList(".cpp", ".h", ".hpp"));
        assertPoint(contract, ProfileOverlayContract.CPP_PATH_CONFIG,
                ProfileOverlayContract.Language.CPP,
                ProfileOverlayContract.Capability.PATH_CONFIG,
                "cpp/path-config", Arrays.asList(".cpp", ".h", ".hpp"));
        assertPoint(contract, ProfileOverlayContract.CPP_PROFILE_PRIVATE,
                ProfileOverlayContract.Language.CPP,
                ProfileOverlayContract.Capability.PROFILE_PRIVATE,
                "cpp/profile-private", Arrays.asList(".cpp", ".h", ".hpp"));
    }

    @Test
    public void exposesImmutableLookupByInterfaceAndAllowedDirectory() {
        ProfileOverlayContract contract = ProfileOverlayContract.builtIn();
        ProfileOverlayContract.ExtensionPoint point = contract.requireInterface(
                ProfileOverlayContract.JAVA_STARTUP_COORDINATION);

        Assert.assertSame(point, contract.requireDirectory("java/startup-coordination"));
        assertRejected(() -> contract.requireInterface("java.jni-registration"));
        assertRejected(() -> contract.requireDirectory("cpp/hook"));
        try {
            contract.getExtensionPoints().clear();
            Assert.fail("Expected immutable extension point list");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertPoint(ProfileOverlayContract contract, String interfaceId,
                                    ProfileOverlayContract.Language language,
                                    ProfileOverlayContract.Capability capability,
                                    String relativeDirectory, List<String> sourceSuffixes) {
        ProfileOverlayContract.ExtensionPoint point = contract.requireInterface(interfaceId);
        Assert.assertEquals(language, point.getLanguage());
        Assert.assertEquals(capability, point.getCapability());
        Assert.assertEquals(relativeDirectory, point.getRelativeDirectory());
        Assert.assertEquals(sourceSuffixes, point.getSourceSuffixes());
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected unknown Overlay extension point failure");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Overlay"));
        }
    }
}
