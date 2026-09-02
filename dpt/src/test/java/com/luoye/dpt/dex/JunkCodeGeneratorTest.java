package com.luoye.dpt.dex;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;

public class JunkCodeGeneratorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void profileClassPrefixReplacesLegacyJunkClassName() throws Exception {
        File outputDex = new File(temporaryFolder.getRoot(), "profile-junk.dex");
        String profileClassPrefix = "p.profile.RandomizedJunk";

        Method generate;
        try {
            generate = JunkCodeGenerator.class.getMethod("generateJunkCodeDex",
                    File.class, String.class);
        } catch (NoSuchMethodException e) {
            Assert.fail("profile junk DEX generation entry is missing");
            return;
        }
        generate.invoke(null, outputDex, profileClassPrefix);

        DexBackedDexFile dexFile = DexFileFactory.loadDexFile(outputDex, Opcodes.getDefault());
        Assert.assertFalse(dexFile.getClasses().isEmpty());
        for (ClassDef classDef : dexFile.getClasses()) {
            Assert.assertTrue(classDef.getType(),
                    classDef.getType().startsWith("Lp/profile/RandomizedJunk"));
            Assert.assertFalse(classDef.getType(), classDef.getType().contains("com/luoye"));
        }
    }
}
