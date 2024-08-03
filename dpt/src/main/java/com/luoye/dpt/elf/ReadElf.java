/*
* Copyright (C) 2011 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.luoye.dpt.elf;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * A poor man's implementation of the readelf command. This program is designed
 * to parse ELF (Executable and Linkable Format) files.
 */
public class ReadElf implements AutoCloseable {
    /** The magic values for the ELF identification. */
    private static final byte[] ELFMAG = {
            (byte) 0x7F, (byte) 'E', (byte) 'L', (byte) 'F', };
    private static final int EI_NIDENT = 16;
    private static final int EI_CLASS = 4;
    private static final int EI_DATA = 5;
    private static final int EM_386 = 3;
    private static final int EM_MIPS = 8;
    private static final int EM_ARM = 40;
    private static final int EM_X86_64 = 62;
    // http://en.wikipedia.org/wiki/Qualcomm_Hexagon
    private static final int EM_QDSP6 = 164;
    private static final int EM_AARCH64 = 183;
    private static final int ELFCLASS32 = 1;
    private static final int ELFCLASS64 = 2;
    private static final int ELFDATA2LSB = 1;
    private static final int ELFDATA2MSB = 2;
    private static final int EV_CURRENT = 1;
    private static final long PT_LOAD = 1;
    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_DYNSYM = 11;
    public static class Symbol {
        public static final int STB_LOCAL = 0;
        public static final int STB_GLOBAL = 1;
        public static final int STB_WEAK = 2;
        public static final int STB_LOPROC = 13;
        public static final int STB_HIPROC = 15;
        public static final int STT_NOTYPE = 0;
        public static final int STT_OBJECT = 1;
        public static final int STT_FUNC = 2;
        public static final int STT_SECTION = 3;
        public static final int STT_FILE = 4;
        public static final int STT_COMMON = 5;
        public static final int STT_TLS = 6;
        public final String name;
        public final int bind;
        public final int type;
        Symbol(String name, int st_info) {
            this.name = name;
            this.bind = (st_info >> 4) & 0x0F;
            this.type = st_info & 0x0F;
        }
        @Override
        public String toString() {
            return "Symbol[" + name + "," + toBind() + "," + toType() + "]";
        }
        private String toBind() {
            switch (bind) {
                case STB_LOCAL:
                    return "LOCAL";
                case STB_GLOBAL:
                    return "GLOBAL";
                case STB_WEAK:
                    return "WEAK";
            }
            return "STB_??? (" + bind + ")";
        }
        private String toType() {
            switch (type) {
                case STT_NOTYPE:
                    return "NOTYPE";
                case STT_OBJECT:
                    return "OBJECT";
                case STT_FUNC:
                    return "FUNC";
                case STT_SECTION:
                    return "SECTION";
                case STT_FILE:
                    return "FILE";
                case STT_COMMON:
                    return "COMMON";
                case STT_TLS:
                    return "TLS";
            }
            return "STT_??? (" + type + ")";
        }
    }
    public static class SectionHeader {

        public SectionHeader(String sh_name, long sh_type, long sh_flags, long sh_addr, long sh_offset, long sh_size) {
            this.sh_name = sh_name;
            this.sh_type = sh_type;
            this.sh_flags = sh_flags;
            this.sh_addr = sh_addr;
            this.sh_offset = sh_offset;
            this.sh_size = sh_size;
        }

        public String getName() {
            return sh_name;
        }

        public long getType() {
            return sh_type;
        }

        public long getFlags() {
            return sh_flags;
        }

        public long getAddr() {
            return sh_addr;
        }

        public long getOffset() {
            return sh_offset;
        }

        public long getSize() {
            return sh_size;
        }
        private  String sh_name;
        private long sh_type;
        private long sh_flags;
        private long sh_addr;
        private long sh_offset;
        private long sh_size;


    }
    private final String mPath;
    private final RandomAccessFile mFile;
    private final byte[] mBuffer = new byte[512];
    private int mEndian;
    private boolean mIsDynamic;
    private boolean mIsPIE;
    private int mType;
    private int mAddrSize;
    /** Symbol Table offset */
    private long mSymTabOffset;
    /** Symbol Table size */
    private long mSymTabSize;
    /** Dynamic Symbol Table offset */
    private long mDynSymOffset;
    /** Dynamic Symbol Table size */
    private long mDynSymSize;
    /** Section Header String Table offset */
    private long mShStrTabOffset;
    /** Section Header String Table size */
    private long mShStrTabSize;
    /** String Table offset */
    private long mStrTabOffset;
    /** String Table size */
    private long mStrTabSize;
    /** Dynamic String Table offset */
    private long mDynStrOffset;
    /** Dynamic String Table size */
    private long mDynStrSize;
    /** Symbol Table symbol names */
    private Map<String, Symbol> mSymbols;
    /** Dynamic Symbol Table symbol names */
    private Map<String, Symbol> mDynamicSymbols;
    private List<SectionHeader> mSectionHeaderList;

    public static ReadElf read(File file) throws IOException {
        return new ReadElf(file);
    }
    public boolean isDynamic() {
        return mIsDynamic;
    }
    public int getType() {
        return mType;
    }
    public boolean isPIE() {
        return mIsPIE;
    }
    public ReadElf(File file) throws IOException {
        mPath = file.getPath();
        mFile = new RandomAccessFile(file, "r");
        if (mFile.length() < EI_NIDENT) {
            throw new IllegalArgumentException("Too small to be an ELF file: " + file);
        }
        readHeader();
    }

    @Override
    public void close() {
        try {
            mFile.close();
        } catch (IOException ignored) {
        }
    }

    private void readHeader() throws IOException {
        mFile.seek(0);
        mFile.readFully(mBuffer, 0, EI_NIDENT);
        if (mBuffer[0] != ELFMAG[0] || mBuffer[1] != ELFMAG[1] ||
                mBuffer[2] != ELFMAG[2] || mBuffer[3] != ELFMAG[3]) {
            throw new IllegalArgumentException("Invalid ELF file: " + mPath);
        }
        int elfClass = mBuffer[EI_CLASS];
        if (elfClass == ELFCLASS32) {
            mAddrSize = 4;
        } else if (elfClass == ELFCLASS64) {
            mAddrSize = 8;
        } else {
            throw new IOException("Invalid ELF EI_CLASS: " + elfClass + ": " + mPath);
        }
        mEndian = mBuffer[EI_DATA];
        if (mEndian == ELFDATA2LSB) {
        } else if (mEndian == ELFDATA2MSB) {
            throw new IOException("Unsupported ELFDATA2MSB file: " + mPath);
        } else {
            throw new IOException("Invalid ELF EI_DATA: " + mEndian + ": " + mPath);
        }
        mType = readHalf();
        int e_machine = readHalf();
        if (e_machine != EM_386 && e_machine != EM_X86_64 &&
                e_machine != EM_AARCH64 && e_machine != EM_ARM &&
                e_machine != EM_MIPS &&
                e_machine != EM_QDSP6) {
            throw new IOException("Invalid ELF e_machine: " + e_machine + ": " + mPath);
        }
        // AbiTest relies on us rejecting any unsupported combinations.
        if ((e_machine == EM_386 && elfClass != ELFCLASS32) ||
                (e_machine == EM_X86_64 && elfClass != ELFCLASS64) ||
                (e_machine == EM_AARCH64 && elfClass != ELFCLASS64) ||
                (e_machine == EM_ARM && elfClass != ELFCLASS32) ||
                (e_machine == EM_QDSP6 && elfClass != ELFCLASS32)) {
            throw new IOException("Invalid e_machine/EI_CLASS ELF combination: " +
                    e_machine + "/" + elfClass + ": " + mPath);
        }
        long e_version = readWord();
        if (e_version != EV_CURRENT) {
            throw new IOException("Invalid e_version: " + e_version + ": " + mPath);
        }
        long e_entry = readAddr();
        long ph_off = readOff();
        long sh_off = readOff();
        long e_flags = readWord();
        int e_ehsize = readHalf();
        int e_phentsize = readHalf();
        int e_phnum = readHalf();
        int e_shentsize = readHalf();
        int e_shnum = readHalf();
        int e_shstrndx = readHalf();
        readSectionHeaders(sh_off, e_shnum, e_shentsize, e_shstrndx);
        readProgramHeaders(ph_off, e_phnum, e_phentsize);
    }
    private void readSectionHeaders(long sh_off, int e_shnum, int e_shentsize, int e_shstrndx)
            throws IOException {
        // Read the Section Header String Table offset first.
        if(mSectionHeaderList == null) {
            mSectionHeaderList = new ArrayList<>();
        }

        {
            mFile.seek(sh_off + e_shstrndx * e_shentsize);
            long sh_name = readWord();
            long sh_type = readWord();
            long sh_flags = readX(mAddrSize);
            long sh_addr = readAddr();
            long sh_offset = readOff();
            long sh_size = readX(mAddrSize);
            // ...
            if (sh_type == SHT_STRTAB) {
                mShStrTabOffset = sh_offset;
                mShStrTabSize = sh_size;
            }
        }
        for (int i = 0; i < e_shnum; ++i) {
            // Don't bother to re-read the Section Header StrTab.
            if (i == e_shstrndx) {
                continue;
            }
            mFile.seek(sh_off + i * e_shentsize);
            long sh_name = readWord();
            long sh_type = readWord();
            long sh_flags = readX(mAddrSize);
            long sh_addr = readAddr();
            long sh_offset = readOff();
            long sh_size = readX(mAddrSize);
            final String shName = readShStrTabEntry(sh_name);

            mSectionHeaderList.add(new SectionHeader(shName,sh_type,sh_flags,sh_addr,sh_offset,sh_size));

            if (sh_type == SHT_SYMTAB || sh_type == SHT_DYNSYM) {
                if (".symtab".equals(shName)) {
                    mSymTabOffset = sh_offset;
                    mSymTabSize = sh_size;
                } else if (".dynsym".equals(shName)) {
                    mDynSymOffset = sh_offset;
                    mDynSymSize = sh_size;
                }
            } else if (sh_type == SHT_STRTAB) {
                if (".strtab".equals(shName)) {
                    mStrTabOffset = sh_offset;
                    mStrTabSize = sh_size;
                } else if (".dynstr".equals(shName)) {
                    mDynStrOffset = sh_offset;
                    mDynStrSize = sh_size;
                }
            } else if (sh_type == SHT_DYNAMIC) {
                mIsDynamic = true;
            }
        }
    }
    private void readProgramHeaders(long ph_off, int e_phnum, int e_phentsize) throws IOException {
        for (int i = 0; i < e_phnum; ++i) {
            mFile.seek(ph_off + i * e_phentsize);
            long p_type = readWord();
            if (p_type == PT_LOAD) {
                if (mAddrSize == 8) {
                    // Only in Elf64_phdr; in Elf32_phdr p_flags is at the end.
                    long p_flags = readWord();
                }
                long p_offset = readOff();
                long p_vaddr = readAddr();
                // ...
                if (p_vaddr == 0) {
                    mIsPIE = true;
                }
            }
        }
    }
    private HashMap<String, Symbol> readSymbolTable(long symStrOffset, long symStrSize,
                                                    long tableOffset, long tableSize) throws IOException {
        HashMap<String, Symbol> result = new HashMap<String, Symbol>();
        mFile.seek(tableOffset);
        while (mFile.getFilePointer() < tableOffset + tableSize) {
            long st_name = readWord();
            int st_info;
            if (mAddrSize == 8) {
                st_info = readByte();
                int st_other = readByte();
                int st_shndx = readHalf();
                long st_value = readAddr();
                long st_size = readX(mAddrSize);
            } else {
                long st_value = readAddr();
                long st_size = readWord();
                st_info = readByte();
                int st_other = readByte();
                int st_shndx = readHalf();
            }
            if (st_name == 0) {
                continue;
            }
            final String symName = readStrTabEntry(symStrOffset, symStrSize, st_name);
            if (symName != null) {
                Symbol s = new Symbol(symName, st_info);
                result.put(symName, s);
            }
        }
        return result;
    }
    private String readShStrTabEntry(long strOffset) throws IOException {
        if (mShStrTabOffset == 0 || strOffset < 0 || strOffset >= mShStrTabSize) {
            return null;
        }
        return readString(mShStrTabOffset + strOffset);
    }
    private String readStrTabEntry(long tableOffset, long tableSize, long strOffset)
            throws IOException {
        if (tableOffset == 0 || strOffset < 0 || strOffset >= tableSize) {
            return null;
        }
        return readString(tableOffset + strOffset);
    }
    private int readHalf() throws IOException {
        return (int) readX(2);
    }
    private long readWord() throws IOException {
        return readX(4);
    }
    private long readOff() throws IOException {
        return readX(mAddrSize);
    }
    private long readAddr() throws IOException {
        return readX(mAddrSize);
    }
    private long readX(int byteCount) throws IOException {
        mFile.readFully(mBuffer, 0, byteCount);
        int answer = 0;
        if (mEndian == ELFDATA2LSB) {
            for (int i = byteCount - 1; i >= 0; i--) {
                answer = (answer << 8) | (mBuffer[i] & 0xff);
            }
        } else {
            final int N = byteCount - 1;
            for (int i = 0; i <= N; ++i) {
                answer = (answer << 8) | (mBuffer[i] & 0xff);
            }
        }
        return answer;
    }
    private String readString(long offset) throws IOException {
        long originalOffset = mFile.getFilePointer();
        mFile.seek(offset);
        mFile.readFully(mBuffer, 0, (int) Math.min(mBuffer.length, mFile.length() - offset));
        mFile.seek(originalOffset);
        for (int i = 0; i < mBuffer.length; ++i) {
            if (mBuffer[i] == 0) {
                return new String(mBuffer, 0, i);
            }
        }
        return null;
    }
    private int readByte() throws IOException {
        return mFile.read() & 0xff;
    }
    public Symbol getSymbol(String name) {
        if (mSymbols == null) {
            try {
                mSymbols = readSymbolTable(mStrTabOffset, mStrTabSize, mSymTabOffset, mSymTabSize);
            } catch (IOException e) {
                return null;
            }
        }
        return mSymbols.get(name);
    }
    public Symbol getDynamicSymbol(String name) {
        if (mDynamicSymbols == null) {
            try {
                mDynamicSymbols = readSymbolTable(
                        mDynStrOffset, mDynStrSize, mDynSymOffset, mDynSymSize);
            } catch (IOException e) {
                return null;
            }
        }
        return mDynamicSymbols.get(name);
    }

    public List<SectionHeader> getSectionHeaders() {
      return mSectionHeaderList;
    }
}