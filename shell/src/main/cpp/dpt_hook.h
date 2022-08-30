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
#include "dex/ClassDataItemReader.h"
#include "dpt_log.h"
#include "dobby.h"
#define PAGE_START(addr) ((addr) & PAGE_MASK)

void dpt_hook();

//android M,N
static void (*g_originLoadMethod25)(void* thiz, void* self, const void* dex_file, const void* it, void* klass, void* dst) = nullptr;
//android O,P
static void (*g_originLoadMethod28)(void* thiz, const void* dex_file, const void* it, void* klass,void *dst) = nullptr;
//android Q,R
static void (*g_originLoadMethod29)(void* thiz, const void* dex_file, const void* method, void* klass,void *dst) = nullptr;

void hook_ClassLinker_LoadMethod();
void callOriginLoadMethod(void *thiz, void *self, const void *dex_file, const void *it, const void *method,
                          void *klass, void *dst);
uint32_t getDexFileLocationOffset();
uint32_t getDataItemCodeItemOffset();
void hook_mmap();
void hook_GetOatDexFile();
static void (*g_GetOatDexFile)(const char* dex_location,
                         const uint32_t* dex_location_checksum,
                         std::string* error_msg) = nullptr;
#endif //DPT_DPT_HOOK_H
