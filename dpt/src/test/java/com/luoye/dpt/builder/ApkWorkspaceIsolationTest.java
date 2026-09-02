package com.luoye.dpt.builder;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;

public class ApkWorkspaceIsolationTest {
    @Test
    public void eachApkUsesItsOwnRunRootAndKeepsIntermediatesOutsidePackagedTree() {
        InspectableApk first = new InspectableApk(new Apk.Builder());
        InspectableApk second = new InspectableApk(new Apk.Builder());

        Assert.assertNotEquals(first.workspace(), second.workspace());
        Assert.assertNotEquals(first.lastProcess(), second.lastProcess());
        Assert.assertNotEquals(first.junkDex(), second.junkDex());
        Assert.assertNotEquals(first.renamedDex(), second.renamedDex());

        assertRunLayout(first);
        assertRunLayout(second);
    }

    private static void assertRunLayout(InspectableApk apk) {
        Path workspace = apk.workspace();
        Path runRoot = workspace.getParent();
        Assert.assertEquals("unpacked", workspace.getFileName().toString());
        Assert.assertEquals(runRoot.resolve("last-process"), apk.lastProcess());
        Assert.assertEquals(runRoot.resolve("intermediates/junkcode.dex"), apk.junkDex());
        Assert.assertEquals(runRoot.resolve("intermediates/rename_classes.dex"), apk.renamedDex());
        Assert.assertFalse(apk.junkDex().startsWith(workspace));
        Assert.assertFalse(apk.renamedDex().startsWith(workspace));
    }

    private static final class InspectableApk extends Apk {
        private InspectableApk(Builder builder) {
            super(builder);
        }

        private Path workspace() {
            return getWorkspaceDir().toPath().toAbsolutePath().normalize();
        }

        private Path lastProcess() {
            return getLastProcessDir().toPath().toAbsolutePath().normalize();
        }

        private Path junkDex() {
            return Path.of(getJunkCodeDexPath()).toAbsolutePath().normalize();
        }

        private Path renamedDex() {
            return Path.of(getRenameDexPath()).toAbsolutePath().normalize();
        }
    }
}
