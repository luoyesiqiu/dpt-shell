package com.luoye.dpt.util;

import com.luoye.dpt.config.Const;
import com.luoye.dpt.elf.ReadElf;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Writes the per-build key into a shell library.
 *
 * <p>The key lives in a dedicated section, so no symbol is needed to locate it. After the key is
 * written, the section name is cleared in the output library to make the location less obvious.
 * The runtime never reads section headers, so the erased name does not affect loading.</p>
 */
public class SoKeyUtils {

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

            // Clear sh_name of the section header. Only this 4-byte field is touched, so section
            // name strings shared with other sections are left intact.
            IoUtils.writeFile(soFile.getAbsolutePath(), new byte[4], sectionHeader.getNameOffset());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
