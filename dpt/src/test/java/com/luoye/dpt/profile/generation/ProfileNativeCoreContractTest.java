package com.luoye.dpt.profile.generation;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileNativeCoreContractTest {

    @Test
    public void declaresAllSixVersionedImmutableNativeCoreCapabilities() {
        ProfileNativeCoreContract contract = ProfileNativeCoreContract.builtIn();

        Assert.assertEquals("immutable-core-v1", contract.getVersion());
        Assert.assertEquals(contract.getVersion(), ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        Assert.assertEquals(Arrays.asList(ProfileNativeCoreContract.Capability.values()),
                contract.getBoundaries().stream()
                        .map(ProfileNativeCoreContract.CoreBoundary::getCapability)
                        .collect(Collectors.toList()));

        assertBoundary(contract, ProfileNativeCoreContract.Capability.DEX_CODE_ITEM_FORMAT,
                "dex/CodeItem.cpp", "DexHeader");
        assertBoundary(contract, ProfileNativeCoreContract.Capability.KEY_DERIVATION,
                "dpt_crypto.cpp", "DPT_BUILD_KEY");
        assertBoundary(contract, ProfileNativeCoreContract.Capability.CONFIG_DECRYPTION,
                "dpt_crypto.cpp", "aes_cbc_decrypt");
        assertBoundary(contract, ProfileNativeCoreContract.Capability.ART_HOOK,
                "dpt_hook.cpp", "hook_DefineClass");
        assertBoundary(contract, ProfileNativeCoreContract.Capability.RISK_CHECK,
                "dpt_risk.cpp", "detectRisk");
        assertBoundary(contract, ProfileNativeCoreContract.Capability.COMPONENT_RESTORATION,
                "dpt.cpp", "readApplicationName");
    }

    @Test
    public void exposesImmutableBoundaryCollections() {
        ProfileNativeCoreContract contract = ProfileNativeCoreContract.builtIn();
        ProfileNativeCoreContract.CoreBoundary boundary = contract.requireBoundary(
                ProfileNativeCoreContract.Capability.DEX_CODE_ITEM_FORMAT);

        assertImmutable(contract.getBoundaries());
        assertImmutable(boundary.getSourcePaths());
        assertImmutable(boundary.getProtectedSymbols());
    }

    private static void assertBoundary(ProfileNativeCoreContract contract,
                                       ProfileNativeCoreContract.Capability capability,
                                       String expectedPath, String expectedSymbol) {
        ProfileNativeCoreContract.CoreBoundary boundary = contract.requireBoundary(capability);
        Assert.assertTrue(boundary.getSourcePaths().contains(expectedPath));
        Assert.assertTrue(boundary.getProtectedSymbols().stream()
                .anyMatch(symbol -> expectedSymbol.equals(symbol.getName())));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertImmutable(List<?> values) {
        try {
            ((List) values).clear();
            Assert.fail("Expected immutable Native core contract collection");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
