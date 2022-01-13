//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_UTIL_H
#define DPT_DPT_UTIL_H
#include <jni.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <libgen.h>
#include <string.h>
#include <unistd.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "dpt_log.h"
#include "JniWrapper.h"

#ifdef __LP64__
#define LIB_DIR "lib64"
#else
#define LIB_DIR "lib"
#endif

static AAssetManager *g_AssetMgrInstance = nullptr;
static jclass g_ContextClass = nullptr;

jclass getContextClass(JNIEnv *env);
AAssetManager *getAssetMgr(JNIEnv *env, jobject assetManager);
AAsset *getAsset(JNIEnv *env, jobject context, const char *filename);
jbyteArray readFromZip(JNIEnv* env,jstring zipPath,jstring fileName);
jstring getApkPath(JNIEnv *env,jclass ,jobject classLoader);
int endWith(const char *str,const char* sub);
void appendLog(const char* log);
#endif //DPT_DPT_UTIL_H
