//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_MACRO_H
#define DPT_DPT_MACRO_H

#define DEXES_ZIP_NAME "i11111i111"

#define PAGE_START(addr) ((addr) & PAGE_MASK)

#ifdef __LP64__
#define LIB_DIR "lib64"
#define pointer_t uint64_t
#else
#define LIB_DIR "lib"
#define pointer_t uint32_t
#endif

#ifndef LIKELY
#define LIKELY(x)   __builtin_expect(!!(x), 1)
#endif
#ifndef UNLIKELY
#define UNLIKELY(x) __builtin_expect(!!(x), 0)
#endif

#endif //DPT_DPT_MACRO_H
