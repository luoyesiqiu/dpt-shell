package com.luoye.dpt;

import com.luoye.dpt.config.Const;
import com.luoye.dpt.model.Instruction;
import com.luoye.dpt.model.MultiDexCode;
import com.luoye.dpt.util.KeyUtils;
import com.luoye.dpt.util.MultiDexCodeUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProfileRuntimeProtocolRegressionTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void multiDexCodeKeepsVersionIndexesAndLittleEndianInstructionFormat() throws Exception {
        Map<Integer, List<Instruction>> instructions = new LinkedHashMap<>();
        instructions.put(0, Arrays.asList(instruction(0x01020304, new byte[] {10, 11})));
        instructions.put(1, Arrays.asList(instruction(7, new byte[] {12})));

        MultiDexCode value = MultiDexCodeUtils.makeMultiDexCode(instructions);
        Path output = temporaryFolder.newFile("code-items.bin").toPath();
        MultiDexCodeUtils.writeMultiDexCode(output.toString(), value);

        Assert.assertEquals(Const.MULTI_DEX_CODE_VERSION, value.getVersion());
        Assert.assertArrayEquals(new byte[] {
                2, 0, 2, 0,
                12, 0, 0, 0, 24, 0, 0, 0,
                1, 0, 4, 3, 2, 1, 2, 0, 0, 0, 10, 11,
                1, 0, 7, 0, 0, 0, 1, 0, 0, 0, 12
        }, Files.readAllBytes(output));
    }

    @Test
    public void eachApkKeyRemainsIndependentWhileKeepingNativeKeySlots() {
        Set<String> generated = new LinkedHashSet<>();
        for (int index = 0; index < 32; index++) {
            byte[] key = KeyUtils.generateKey();
            Assert.assertEquals(16, key.length);
            Assert.assertEquals((byte) 0x20, key[3]);
            Assert.assertEquals((byte) 0x74, key[9]);
            generated.add(Arrays.toString(key));
        }
        Assert.assertEquals(32, generated.size());
    }

    private static Instruction instruction(int methodIndex, byte[] data) {
        Instruction value = new Instruction();
        value.setMethodIndex(methodIndex);
        value.setInstructionDataSize(data.length);
        value.setInstructionsData(data);
        return value;
    }
}
