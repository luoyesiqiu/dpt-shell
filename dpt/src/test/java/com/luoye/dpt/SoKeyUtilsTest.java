package com.luoye.dpt;

import com.luoye.dpt.config.Const;
import com.luoye.dpt.elf.ReadElf;
import com.luoye.dpt.util.SoKeyUtils;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Tests the section based key writing for shell libraries.
 */
public class SoKeyUtilsTest {

    private static final int ELF_HEADER_SIZE = 52;
    private static final int SECTION_HEADER_SIZE = 40;
    private static final int SECTION_COUNT = 3;
    private static final int SECTION_HEADER_OFFSET = 0x100;
    private static final int SECTION_DATA_OFFSET = 0x40;
    private static final int SECTION_NAME_OFFSET = 0x60;

    @Test
    public void testWriteKeyErasesSectionName() throws Exception {
        byte[] key = testKey();
        File soFile = writeElf(Const.KEY_DATA_PLACEHOLDER.getBytes(StandardCharsets.US_ASCII));

        Assert.assertTrue(SoKeyUtils.writeKey(soFile, key));

        byte[] data = Files.readAllBytes(soFile.toPath());
        Assert.assertArrayEquals(key, Arrays.copyOfRange(data, SECTION_DATA_OFFSET,
                SECTION_DATA_OFFSET + key.length));
        // The sh_name field of the first real section header must be cleared.
        Assert.assertEquals(0, ByteBuffer.wrap(data,
                        SECTION_HEADER_OFFSET + SECTION_HEADER_SIZE, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt());

        try (ReadElf readElf = new ReadElf(soFile)) {
            Assert.assertNull(readElf.getSection(Const.KEY_DATA_SECTION_NAME));
            Assert.assertEquals(SECTION_COUNT - 1, readElf.getSectionHeaders().size());
        }
    }

    @Test
    public void testWriteKeyRejectsUnexpectedData() throws Exception {
        byte[] key = testKey();
        byte[] unexpectedData = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        File soFile = writeElf(unexpectedData);

        Assert.assertFalse(SoKeyUtils.writeKey(soFile, key));

        byte[] data = Files.readAllBytes(soFile.toPath());
        Assert.assertArrayEquals(unexpectedData, Arrays.copyOfRange(data, SECTION_DATA_OFFSET,
                SECTION_DATA_OFFSET + unexpectedData.length));
    }

    @Test
    public void testWriteKeyWithoutSection() throws Exception {
        byte[] key = testKey();
        File soFile = writeElfWithSectionName(".junk",
                Const.KEY_DATA_PLACEHOLDER.getBytes(StandardCharsets.US_ASCII));

        Assert.assertFalse(SoKeyUtils.writeKey(soFile, key));
    }

    private static byte[] testKey() {
        byte[] key = new byte[16];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (0x40 + i);
        }
        return key;
    }

    private static File writeElf(byte[] sectionData) throws Exception {
        return writeElfWithSectionName(Const.KEY_DATA_SECTION_NAME, sectionData);
    }

    private static File writeElfWithSectionName(String sectionName, byte[] sectionData) throws Exception {
        byte[] sectionNameTable = buildSectionNameTable(sectionName);
        // Align the section header table after the section data it describes.
        int sectionHeaderOffset = Math.max(SECTION_HEADER_OFFSET,
                Math.max(SECTION_DATA_OFFSET + sectionData.length,
                        SECTION_NAME_OFFSET + sectionNameTable.length) + 15) & ~15;
        int fileSize = sectionHeaderOffset + SECTION_COUNT * SECTION_HEADER_SIZE;

        ByteBuffer buffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);
        // ELF header (ELF32, little endian, ARM).
        buffer.put((byte) 0x7f).put((byte) 'E').put((byte) 'L').put((byte) 'F');
        buffer.put((byte) 1); // EI_CLASS: ELFCLASS32
        buffer.put((byte) 1); // EI_DATA: ELFDATA2LSB
        buffer.put((byte) 1); // EI_VERSION
        buffer.put(new byte[9]);
        buffer.putShort((short) 3);                      // e_type: ET_DYN
        buffer.putShort((short) 40);                     // e_machine: EM_ARM
        buffer.putInt(1);                                // e_version
        buffer.putInt(0);                                // e_entry
        buffer.putInt(0);                                // e_phoff
        buffer.putInt(sectionHeaderOffset);              // e_shoff
        buffer.putInt(0);                                // e_flags
        buffer.putShort((short) ELF_HEADER_SIZE);        // e_ehsize
        buffer.putShort((short) 32);                     // e_phentsize
        buffer.putShort((short) 0);                      // e_phnum
        buffer.putShort((short) SECTION_HEADER_SIZE);    // e_shentsize
        buffer.putShort((short) SECTION_COUNT);          // e_shnum
        buffer.putShort((short) 2);                      // e_shstrndx

        buffer.position(SECTION_DATA_OFFSET);
        buffer.put(sectionData);
        buffer.position(SECTION_NAME_OFFSET);
        buffer.put(sectionNameTable);

        // Section header #0: SHT_NULL, all zero.
        // Section header #1: the key section.
        buffer.position(sectionHeaderOffset + SECTION_HEADER_SIZE);
        putSectionHeader(buffer, 1, 1, 3, 0x1000, SECTION_DATA_OFFSET, sectionData.length);
        // Section header #2: .shstrtab.
        buffer.position(sectionHeaderOffset + 2 * SECTION_HEADER_SIZE);
        putSectionHeader(buffer, 1 + sectionName.length() + 1, 3, 0, 0,
                SECTION_NAME_OFFSET, sectionNameTable.length);

        File soFile = File.createTempFile("dpt-key-test", ".so");
        soFile.deleteOnExit();
        Files.write(soFile.toPath(), buffer.array());
        return soFile;
    }

    private static void putSectionHeader(ByteBuffer buffer, int name, int type, int flags,
                                         int addr, int offset, int size) {
        buffer.putInt(name);
        buffer.putInt(type);
        buffer.putInt(flags);
        buffer.putInt(addr);
        buffer.putInt(offset);
        buffer.putInt(size);
        buffer.putInt(0); // sh_link
        buffer.putInt(0); // sh_info
        buffer.putInt(1); // sh_addralign
        buffer.putInt(0); // sh_entsize
    }

    private static byte[] buildSectionNameTable(String sectionName) {
        ByteBuffer table = ByteBuffer.allocate(sectionName.length() + 1
                + ".shstrtab".length() + 1 + 1);
        table.put((byte) 0);
        putString(table, sectionName);
        putString(table, ".shstrtab");
        return table.array();
    }

    private static void putString(ByteBuffer buffer, String value) {
        buffer.put(value.getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) 0);
    }
}
