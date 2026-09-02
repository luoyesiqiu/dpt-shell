package com.luoye.dpt.profile.gate.structure;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class NativeStructuralFeatureExtractorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void symbolsAddressesRelocationsAndDebugLinesDoNotAffectFeatures() throws Exception {
        String left = "0000000000001000 <left_secret_function>:\n"
                + "  1000: d10043ff sub sp, sp, #0x10\n"
                + "  1004: 94000000 bl 0x2200 <left_secret_target>\n"
                + "  1008: 7100001f cmp w0, #0\n"
                + "  100c: 54000040 b.eq 0x1014 <left_label>\n"
                + "  1010: d65f03c0 ret\n"
                + "R_AARCH64_CALL26 left_secret_target\n"
                + ".debug_line left/source.cpp:17\n";
        String right = "0000000000009000 <right_private_function>:\n"
                + "  9000: d10043ff sub sp, sp, #64\n"
                + "  9004: 94000000 bl 0xa200 <right_private_target>\n"
                + "  9008: 7100001f cmp w7, #99\n"
                + "  900c: 54000040 b.eq 0x9014 <right_label>\n"
                + "  9010: d65f03c0 ret\n"
                + "R_AARCH64_CALL26 right_private_target\n"
                + ".debug_line right/source.cpp:88\n";

        NativeStructuralFeatureExtractor extractor = new NativeStructuralFeatureExtractor();
        StructuralFeatureSet leftFeatures = extractor.extractDisassembly("left",
                Arrays.asList(left));
        StructuralFeatureSet rightFeatures = extractor.extractDisassembly("right",
                Arrays.asList(right));

        Assert.assertEquals(leftFeatures.getFeatures(), rightFeatures.getFeatures());
        Assert.assertEquals(1.0d, JaccardSimilarity.compare(leftFeatures, rightFeatures), 0.0d);
        String features = leftFeatures.getFeatures().toString();
        Assert.assertFalse(features.contains("left_secret"));
        Assert.assertFalse(features.contains("1000"));
        Assert.assertFalse(features.contains("source.cpp"));
        Assert.assertFalse(features.contains("R_AARCH64"));
        Assert.assertTrue(features.contains("b.eq"));
    }

    @Test
    public void functionBasicBlockAndInstructionChangesProduceDifferentFeatures()
            throws Exception {
        String linear = "0000000000000000 <linear>:\n"
                + "  0: 11000400 add w0, w0, #1\n"
                + "  4: d65f03c0 ret\n";
        String branching = "0000000000000000 <branching>:\n"
                + "  0: 7100001f cmp w0, #0\n"
                + "  4: 54000040 b.eq 0xc <done>\n"
                + "  8: 51000400 sub w0, w0, #1\n"
                + "  c: d65f03c0 ret\n";

        NativeStructuralFeatureExtractor extractor = new NativeStructuralFeatureExtractor();
        StructuralFeatureSet linearFeatures = extractor.extractDisassembly("linear",
                Arrays.asList(linear));
        StructuralFeatureSet branchingFeatures = extractor.extractDisassembly("branching",
                Arrays.asList(branching));

        Assert.assertNotEquals(linearFeatures.getFeatures(), branchingFeatures.getFeatures());
        Assert.assertTrue(JaccardSimilarity.compare(linearFeatures, branchingFeatures) < 1.0d);
        Assert.assertTrue(branchingFeatures.getFeatures().stream()
                .anyMatch(feature -> feature.contains("block-count:2")));
    }

    @Test
    public void extractsVariableObjectDisassemblyWithExplicitObjdumpTool() throws Exception {
        Path objectFile = temporaryFolder.newFile("variable-layer.o").toPath();
        Files.write(objectFile, new byte[]{1});
        Path objdump = temporaryFolder.newFile("fake-llvm-objdump.sh").toPath();
        Files.writeString(objdump, "#!/bin/sh\n"
                + "printf '00000000 <private_symbol>:\\n  0: d65f03c0 ret\\n'\n",
                StandardCharsets.UTF_8);
        Assert.assertTrue(objdump.toFile().setExecutable(true));

        StructuralFeatureSet features = new NativeStructuralFeatureExtractor()
                .extract(objdump, Arrays.asList(objectFile));

        Assert.assertEquals("native", features.getLanguage());
        Assert.assertEquals("arm64-variable-objects", features.getScope());
        Assert.assertTrue(features.getFeatures().stream()
                .anyMatch(feature -> feature.contains("ret")));
    }
}
