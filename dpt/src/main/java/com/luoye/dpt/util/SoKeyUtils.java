package com.luoye.dpt.util;

import com.luoye.dpt.config.Const;
import com.luoye.dpt.elf.ReadElf;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Writes the per-build key into a shell library.
 *
 * <p>The key lives in a dedicated section, so no symbol is needed to locate it. After the key is
 * written, the section header entry is removed from the output library, so the section is gone
 * from the section header table instead of only losing its name. The runtime never reads the key
 * section, and it looks up the other sections by name, so removing the entry does not affect
 * loading.</p>
 */
public class SoKeyUtils {

    private static final int SECTION_TYPE_RELA = 4;
    private static final int SECTION_TYPE_REL = 9;
    private static final int SECTION_TYPE_NULL = 0;
    private static final int SHN_XINDEX = 0xffff;

    private SoKeyUtils() {
    }

    /**
     * Writes {@code key} at the beginning of the key section of {@code soFile}.
     *
     * @return true if the key was written
     */
    public static boolean writeKey(File soFile, byte[] key) {
        byte[] placeholder = Const.KEY_DATA_PLACEHOLDER.getBytes(StandardCharsets.US_ASCII);
        if (key.length != placeholder.length) {
            LogUtils.error("invalid key length %d for %s", key.length, soFile.getName());
            return false;
        }

        try (ReadElf readElf = new ReadElf(soFile)) {
            ReadElf.SectionHeader sectionHeader = readElf.getSection(Const.KEY_DATA_SECTION_NAME);
            if (sectionHeader == null) {
                LogUtils.warn("cannot find section %s in %s, no need write key",
                        Const.KEY_DATA_SECTION_NAME, soFile.getName());
                return false;
            }

            long keyDataOffset = sectionHeader.getOffset();
            if (sectionHeader.getSize() < key.length) {
                LogUtils.error("section %s in %s is too small, skip writing key",
                        Const.KEY_DATA_SECTION_NAME, soFile.getName());
                return false;
            }

            byte[] currentData = IoUtils.readFile(soFile.getAbsolutePath(), keyDataOffset, key.length);
            if (!Arrays.equals(placeholder, currentData)) {
                LogUtils.error("unexpected data in section %s of %s, skip writing key",
                        Const.KEY_DATA_SECTION_NAME, soFile.getName());
                return false;
            }

            LogUtils.info("write key to section %s of %s(%s)",
                    Const.KEY_DATA_SECTION_NAME, soFile.getName(),
                    HexUtils.toHexString(keyDataOffset));
            IoUtils.writeFile(soFile.getAbsolutePath(), key, keyDataOffset);

            // Drop the section header entry, so the section no longer shows up in the section
            // header table. Fall back to clearing sh_name if the table cannot be rewritten.
            if (!removeSection(soFile, readElf, sectionHeader)) {
                LogUtils.warn("cannot remove section %s from %s, clear its name instead",
                        Const.KEY_DATA_SECTION_NAME, soFile.getName());
                IoUtils.writeFile(soFile.getAbsolutePath(), new byte[4],
                        sectionHeader.getNameOffset());
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Removes the section header entry of {@code section} from the section header table.
     *
     * <p>Entries after the removed one are shifted up, and every section index stored in the ELF
     * header or in other section headers is adjusted. {@code sh_info} is only a section index for
     * {@code SHT_REL}/{@code SHT_RELA} sections; for {@code SHT_SYMTAB} it holds a symbol index,
     * so it must be left alone.</p>
     */
    private static boolean removeSection(File soFile, ReadElf readElf,
                                         ReadElf.SectionHeader section) {
        long shoff = readElf.getSectionHeaderOffset();
        int shentsize = readElf.getSectionHeaderEntrySize();
        int shnum = readElf.getSectionCount();
        int shstrndx = readElf.getSectionNameTableIndex();
        int index = section.getIndex();

        // Extended numbering stores the real values in the null section header; do not touch it.
        if (shoff <= 0 || shentsize < 40 || shnum <= 1 || shstrndx == SHN_XINDEX
                || index <= 0 || index >= shnum) {
            return false;
        }

        int tableSize = shnum * shentsize;
        byte[] table = IoUtils.readFile(soFile.getAbsolutePath(), shoff, tableSize);
        if (table.length != tableSize) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN);

        // Drop the entry and clear the now unused trailing entry.
        System.arraycopy(table, (index + 1) * shentsize, table, index * shentsize,
                (shnum - index - 1) * shentsize);
        Arrays.fill(table, (shnum - 1) * shentsize, tableSize, (byte) 0);
        int newShnum = shnum - 1;

        // sh_link is a section index for every section type that uses it; sh_info is a section
        // index only for relocation sections.
        int linkOffset = readElf.is64Bit() ? 40 : 24;
        int infoOffset = linkOffset + 4;
        for (int i = 0; i < newShnum; i++) {
            int entryOffset = i * shentsize;
            long type = buffer.getInt(entryOffset + 4) & 0xffffffffL;
            if (type == SECTION_TYPE_NULL) {
                continue;
            }
            int link = buffer.getInt(entryOffset + linkOffset);
            if (link == index) {
                buffer.putInt(entryOffset + linkOffset, 0);
            } else if (link > index) {
                buffer.putInt(entryOffset + linkOffset, link - 1);
            }
            if (type == SECTION_TYPE_REL || type == SECTION_TYPE_RELA) {
                int info = buffer.getInt(entryOffset + infoOffset);
                if (info == index) {
                    buffer.putInt(entryOffset + infoOffset, 0);
                } else if (info > index) {
                    buffer.putInt(entryOffset + infoOffset, info - 1);
                }
            }
        }

        // e_shnum and e_shstrndx share the same offsets in ELF32 and ELF64 headers.
        int headerFieldsOffset = readElf.is64Bit() ? 60 : 48;
        int newShstrndx = shstrndx > index ? shstrndx - 1 : shstrndx;
        byte[] headerFields = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) newShnum)
                .putShort((short) newShstrndx)
                .array();

        IoUtils.writeFile(soFile.getAbsolutePath(), table, shoff);
        IoUtils.writeFile(soFile.getAbsolutePath(), headerFields, headerFieldsOffset);
        return true;
    }
}
