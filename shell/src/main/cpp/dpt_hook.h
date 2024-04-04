//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_HOOK_H
#define DPT_DPT_HOOK_H
#include <iostream>
#include <cstdint>
#include <sys/mman.h>
#include <android/api-level.h>
#include <cstdint>
#include "dpt_util.h"
#include "dex/dex_file.h"
#include "common/dpt_log.h"
#include "common/dpt_macro.h"
#include "dobby.h"

void dpt_hook();

static void* (*g_originDefineClass)(void* thiz,
        void* self,
        const char* descriptor,
        size_t hash,
        void* class_loader,
        const void* dex_file,
        const void* dex_class_def);

void hook_DefineClass();
void hook_mmap();
#endif //DPT_DPT_HOOK_H
