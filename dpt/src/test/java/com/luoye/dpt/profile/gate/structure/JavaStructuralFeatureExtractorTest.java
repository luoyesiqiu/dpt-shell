package com.luoye.dpt.profile.gate.structure;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class JavaStructuralFeatureExtractorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void renamingIdentifiersStringsPackagesAndCommentsKeepsSameFeatures()
            throws Exception {
        Path left = temporaryFolder.newFolder("java-left").toPath();
        Path right = temporaryFolder.newFolder("java-right").toPath();
        write(left.resolve("Alpha.java"),
                "package first.hidden;\n"
                        + "import java.util.List;\n"
                        + "// debug-name-alpha\n"
                        + "final class Alpha { int run(int input) {\n"
                        + "  String text = \"left-secret\";\n"
                        + "  if (input > 7) { return input + 1; }\n"
                        + "  return 0;\n"
                        + "} }\n");
        write(right.resolve("Renamed.java"),
                "package second.privatevalue;\n"
                        + "import java.util.Collection;\n"
                        + "/* debug-name-beta */\n"
                        + "final class Renamed { int execute(int argument) {\n"
                        + "  String message = \"right-secret\";\n"
                        + "  if (argument > 99) { return argument + 42; }\n"
                        + "  return 3;\n"
                        + "} }\n");

        StructuralFeatureSet leftFeatures = new JavaStructuralFeatureExtractor().extract(left);
        StructuralFeatureSet rightFeatures = new JavaStructuralFeatureExtractor().extract(right);

        Assert.assertEquals(leftFeatures.getFeatures(), rightFeatures.getFeatures());
        Assert.assertEquals(1.0d,
                JaccardSimilarity.compare(leftFeatures, rightFeatures), 0.0d);
        String features = leftFeatures.getFeatures().toString();
        Assert.assertFalse(features.contains("first.hidden"));
        Assert.assertFalse(features.contains("Alpha"));
        Assert.assertFalse(features.contains("run"));
        Assert.assertFalse(features.contains("left-secret"));
        Assert.assertFalse(features.contains("debug-name-alpha"));
        Assert.assertTrue(features.contains("if"));
    }

    @Test
    public void controlFlowChangesProduceDifferentFeatures() throws Exception {
        Path branch = temporaryFolder.newFolder("java-branch").toPath();
        Path loop = temporaryFolder.newFolder("java-loop").toPath();
        write(branch.resolve("Branch.java"),
                "final class Branch { int value(int input) {"
                        + "if (input > 0) { return 1; } return 0; } }");
        write(loop.resolve("Loop.java"),
                "final class Loop { int value(int input) {"
                        + "while (input > 0) { input--; } return input; } }");

        StructuralFeatureSet branchFeatures = new JavaStructuralFeatureExtractor().extract(branch);
        StructuralFeatureSet loopFeatures = new JavaStructuralFeatureExtractor().extract(loop);

        Assert.assertNotEquals(branchFeatures.getFeatures(), loopFeatures.getFeatures());
        Assert.assertTrue(JaccardSimilarity.compare(branchFeatures, loopFeatures) < 1.0d);
    }

    @Test
    public void jaccardUsesExactSetIntersectionAndUnion() {
        StructuralFeatureSet left = new StructuralFeatureSet("java", "left",
                new LinkedHashSet<>(Arrays.asList("a", "b")));
        StructuralFeatureSet right = new StructuralFeatureSet("java", "right",
                new LinkedHashSet<>(Arrays.asList("b", "c")));

        Assert.assertEquals(1.0d / 3.0d, JaccardSimilarity.compare(left, right), 0.0d);
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
