package com.luoye.dpt.profile;

import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.validation.ProfileValidationException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileJniSemanticTest {
    private static final String FIRST_PROFILE_ID = "pf_0123456789abcdef";
    private static final String SECOND_PROFILE_ID = "pf_fedcba9876543210";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void definesEveryCurrentJniOperationWithItsFixedSignature() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("CALL_REAL_APPLICATION_ON_CREATE", "(Ljava/lang/String;)V");
        expected.put("INIT_APP", "()V");
        expected.put("GET_SOURCE_DIR", "()Ljava/lang/String;");
        expected.put("GET_COMPRESSED_DEXES_PATH", "()Ljava/lang/String;");
        expected.put("READ_APP_COMPONENT_FACTORY", "()Ljava/lang/String;");
        expected.put("READ_APPLICATION_NAME", "()Ljava/lang/String;");
        expected.put("COMBINE_DEX_ELEMENTS", "(Ljava/lang/ClassLoader;)V");
        expected.put("REMOVE_DEX_ELEMENTS", "(Ljava/lang/ClassLoader;Ljava/lang/String;)V");
        expected.put("REPLACE_APPLICATION", "(Ljava/lang/String;)Ljava/lang/Object;");
        expected.put("STATIC_INITIALIZE", "()V");

        Assert.assertEquals(expected.size(), ProfileJniSemantic.values().length);
        int index = 0;
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            ProfileJniSemantic semantic = ProfileJniSemantic.values()[index++];
            Assert.assertEquals(entry.getKey(), semantic.getSemanticId());
            Assert.assertEquals(entry.getValue(), semantic.getJvmSignature());
            Assert.assertSame(semantic, ProfileJniSemantic.fromSemanticId(entry.getKey()));
        }
        Assert.assertNull(ProfileJniSemantic.fromSemanticId("UNKNOWN_OPERATION"));
    }

    @Test
    public void identityUsesCompleteCatalogWithProfileSpecificMethodNames() throws IOException {
        ProfileIdentityStore store = new ProfileIdentityStore(
                temporaryFolder.newFolder("profiles").toPath());
        ProfileIdentityLock first = store.createIfAbsent("com.example.alpha", FIRST_PROFILE_ID);
        ProfileIdentityLock second = store.createIfAbsent("com.example.beta", SECOND_PROFILE_ID);

        Map<String, JniMethodMapping> firstMappings = index(first.getJniMethods());
        Map<String, JniMethodMapping> secondMappings = index(second.getJniMethods());
        Assert.assertEquals(ProfileJniSemantic.values().length, firstMappings.size());
        Assert.assertEquals(ProfileJniSemantic.values().length, secondMappings.size());
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            JniMethodMapping firstMapping = firstMappings.get(semantic.getSemanticId());
            JniMethodMapping secondMapping = secondMappings.get(semantic.getSemanticId());
            Assert.assertNotNull(firstMapping);
            Assert.assertNotNull(secondMapping);
            Assert.assertEquals(semantic.getJvmSignature(), firstMapping.getJvmSignature());
            Assert.assertEquals(semantic.getJvmSignature(), secondMapping.getJvmSignature());
            Assert.assertNotEquals(firstMapping.getMethodName(), secondMapping.getMethodName());
        }
    }

    @Test
    public void resolvesProfileMethodNameBySemanticIdInsteadOfListPosition() {
        List<JniMethodMapping> mappings = Arrays.asList(
                new JniMethodMapping(ProfileJniSemantic.INIT_APP.getSemanticId(), "mInit",
                        ProfileJniSemantic.INIT_APP.getJvmSignature()),
                new JniMethodMapping(ProfileJniSemantic.STATIC_INITIALIZE.getSemanticId(),
                        "mProfileStaticInit",
                        ProfileJniSemantic.STATIC_INITIALIZE.getJvmSignature()),
                new JniMethodMapping(ProfileJniSemantic.GET_SOURCE_DIR.getSemanticId(), "mSource",
                        ProfileJniSemantic.GET_SOURCE_DIR.getJvmSignature()));

        Assert.assertEquals("mProfileStaticInit",
                ProfileJniSemantic.STATIC_INITIALIZE.requireMethodName(mappings));
    }

    @Test
    public void validatorRejectsMissingUnknownAndSignatureMismatchedSemantics() throws IOException {
        ProfileIdentityStore store = new ProfileIdentityStore(
                temporaryFolder.newFolder("validation-profiles").toPath());
        ProfileIdentityLock identity = store.createIfAbsent("com.example.alpha", FIRST_PROFILE_ID);
        List<JniMethodMapping> complete = new ArrayList<>(identity.getJniMethods());

        identity.setJniMethods(new ArrayList<>(complete.subList(0, complete.size() - 1)));
        assertInvalid("jniMethods", () -> ProfileModelValidator.validate(identity));

        List<JniMethodMapping> withUnknown = new ArrayList<>(complete);
        withUnknown.add(new JniMethodMapping("UNKNOWN_OPERATION", "mUnknown", "()V"));
        identity.setJniMethods(withUnknown);
        assertInvalid("semanticId", () -> ProfileModelValidator.validate(identity));

        List<JniMethodMapping> withWrongSignature = new ArrayList<>();
        for (JniMethodMapping mapping : complete) {
            String signature = "INIT_APP".equals(mapping.getSemanticId())
                    ? "(Ljava/lang/String;)V"
                    : mapping.getJvmSignature();
            withWrongSignature.add(new JniMethodMapping(mapping.getSemanticId(),
                    mapping.getMethodName(), signature));
        }
        identity.setJniMethods(withWrongSignature);
        assertInvalid("jvmSignature", () -> ProfileModelValidator.validate(identity));
    }

    private static Map<String, JniMethodMapping> index(List<JniMethodMapping> mappings) {
        Map<String, JniMethodMapping> indexed = new LinkedHashMap<>();
        for (JniMethodMapping mapping : mappings) {
            indexed.put(mapping.getSemanticId(), mapping);
        }
        return indexed;
    }

    private static void assertInvalid(String field, Runnable action) {
        try {
            action.run();
            Assert.fail("Expected ProfileValidationException for " + field);
        } catch (ProfileValidationException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(field));
        }
    }
}
