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


static void * (*g_originMapFileAtAddress28)(uint8_t*,size_t,int,int,int,off_t,bool,bool,const char*,std::string*) = nullptr;
static void * (*g_originMapFileAtAddress29)(void* thiz,uint8_t* addr,
                                            size_t byte_count,
                                            int prot,
                                            int flags,
                                            int fd,
                                            off_t start,
                                            bool low_4gb,
                                            const char* filename,
                                            bool reuse,
                                            void* reservation,
                                            std::string* error_msg) = nullptr;

static void * (*g_originMapFileAtAddress30)(void* thiz,uint8_t* expected_ptr,
                                            size_t byte_count,
                                            int prot,
                                            int flags,
                                            int fd,
                                            off_t start,
                                            bool low_4gb,
                                            const char* filename,
                                            bool reuse,
                                            void* reservation,
                                            std::string* error_msg) = nullptr;

void hook_ClassLinker_LoadMethod();
void callOriginLoadMethod(void *thiz, void *self, const void *dex_file, const void *it, const void *method,
                          void *klass, void *dst);
uint32_t getDexFileLocationOffset();
uint32_t getDataItemCodeItemOffset();
void hookMapFileAtAddress();
#endif //DPT_DPT_HOOK_H
