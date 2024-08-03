//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_MACRO_H
#define DPT_DPT_MACRO_H

#include "banned.h"

#define SECTION_NAME_BITCODE ".bitcode"

#define DEFAULT_RC4_KEY "ncWK&S5wbqU%IX6j"

#define SECTION(name) __attribute__ ((section(name)))
#define KEEP_SYMBOL __attribute__((visibility("default")))
#define INIT_ARRAY_SECTION __attribute__ ((constructor))
#define ALWAYS_INLINE static inline __attribute__((always_inline))
#define SYS_INLINE ALWAYS_INLINE
#define DPT_ENCRYPT SECTION(SECTION_NAME_BITCODE)

#define DEXES_ZIP_NAME "i11111i111.zip"
#define CACHE_DIR "code_cache"

#define ACF_NAME_IN_ZIP "assets/app_acf"
#define APP_NAME_IN_ZIP "assets/app_name"
#define CODE_ITEM_NAME_IN_ZIP "assets/OoooooOooo"
#define DEX_FILES_NAME_IN_ZIP "assets/i11111i111.zip"

#define CODE_ITEM_NAME_IN_ASSETS "OoooooOooo"

#define PAGE_START(addr) ((addr) & (uintptr_t)PAGE_MASK)

#ifdef __LP64__
#define LIB_DIR "lib64"

#define FMT_POINTER "0x%lx"
#define FMT_UNSIGNED_INT "%u"
#define FMT_UNSIGNED_LONG "%lu"
#define FMT_INT64_T "%ld"
#else
#define LIB_DIR "lib"

#define FMT_POINTER "0x%x"
#define FMT_UNSIGNED_INT "%u"
#define FMT_UNSIGNED_LONG "%lu"
#define FMT_INT64_T "%lld"

#endif

#ifdef __LP64__
#define Elf_Ehdr Elf64_Ehdr
#define Elf_Shdr Elf64_Shdr
#define Elf_Sym  Elf64_Sym
#define Elf_Off  Elf64_Off
#define Elf_Word  Elf64_Word
#define Elf_Half  Elf64_Half
#else
#define Elf_Ehdr Elf32_Ehdr
#define Elf_Shdr Elf32_Shdr
#define Elf_Sym  Elf32_Sym
#define Elf_Off  Elf32_Off
#define Elf_Word  Elf32_Word
#define Elf_Half  Elf32_Half
#endif

#ifndef LIKELY
#define LIKELY(x)   __builtin_expect(!!(x), 1)
#endif
#ifndef UNLIKELY
#define UNLIKELY(x) __builtin_expect(!!(x), 0)
#endif

#define ARRAY_LENGTH(array) (sizeof(array) / sizeof(array[0]))

#define DPT_FREE(addr) do { \
    if(addr) {              \
        free(addr);         \
        addr = nullptr;     \
    }                       \
}                           \
while(0)

#endif //DPT_DPT_MACRO_H
