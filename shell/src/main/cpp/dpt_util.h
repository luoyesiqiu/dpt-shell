//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_UTIL_H
#define DPT_DPT_UTIL_H
#include <string>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <libgen.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <dirent.h>
#include <elf.h>
#include <dlfcn.h>
#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include <mz_zip.h>
#include <mz_strm_mem.h>
#include <mz.h>

#include "dpt_jni.h"
#include "common/dpt_log.h"
#include "common/dpt_macro.h"
#include "common/dpt_string.h"
#include "common/obfuscate.h"

#include "reflect/android_app_ActivityThread.h"
#include "reflect/android_content_pm_ApplicationInfo.h"
#include "reflect/java_lang_Class.h"

static AAssetManager *g_AssetMgrInstance = nullptr;
static jclass g_ContextClass = nullptr;

std::string find_so_path(const char* so_name);
bool checkWebViewInFilename(const std::string& path);
int parse_dex_number(std::string *location);
jclass getContextClass(JNIEnv *env);
AAssetManager *getAssetMgr(JNIEnv *env, jobject assetManager);
AAsset *getAsset(JNIEnv *env, jobject context, const char *filename);
void getSourceDir(JNIEnv *env, char *sourceDirOut, size_t max_out_len);
void getDataDir(JNIEnv *env,char *dataDirOut,size_t max_out_len);
jstring getSourceDirExport(JNIEnv *env,jclass __unused);
void getCompressedDexesPath(JNIEnv *env,char *outDexZipPath,size_t max_len);
void getCodeCachePath(JNIEnv *env,char *outCodeCachePath,size_t max_len);
jstring getCompressedDexesPathExport(JNIEnv *,jclass __unused);
void appendLog(const char* log);
void load_package(JNIEnv *env,void **package_addr,size_t *package_size);
void unload_package(void *apk_addr,size_t package_size);
void extractDexesInNeeded(JNIEnv *env,void *package_addr,size_t package_size);
bool read_zip_file_entry(void* zip_addr,off_t zip_size,const char* entry_name, void ** entry_addr,uint64_t *entry_size);
int find_in_maps(int count,...);
int find_in_threads_list(int count,...);
void find_symbol_in_elf_file(const char *elf_file, char *symbol_name_out, size_t max_out_len, int keyword_count,...);
void get_elf_section(Elf_Shdr *target,const char *elf_path,const char *sh_name);

int dpt_mprotect(void *start,void *end,int prot);
size_t dpt_readlink(int fd, char *result_path,size_t path_max_len);

void getClassName(JNIEnv *env,jobject obj,char *destClassName,size_t max_len);
void parseClassName(const char *src, char *dest);
void printTime(const char* msg,clock_t start);
const char* getThreadName();
#endif //DPT_DPT_UTIL_H
