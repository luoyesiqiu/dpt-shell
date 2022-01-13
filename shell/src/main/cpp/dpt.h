//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_H
#define DPT_DPT_H
#include <jni.h>
#include <string>
#include <inttypes.h>
#include <map>
#include <unordered_map>
#include <vector>
#include <dlfcn.h>
#include <unistd.h>
#include <pthread.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include "dex/CodeItem.h"
#include "dpt_log.h"
#include "dpt_hook.h"
#include "dex/MultiDexCode.h"
#include "JniWrapper.h"
#include "dpt_util.h"
JNIEXPORT void
callRealApplicationOnCreate(JNIEnv *env, jclass, jstring realApplicationClassName);

JNIEXPORT void callRealApplicationAttach(JNIEnv *env, jclass, jobject context,
                                         jstring realApplicationClassName);

void init_dpt(JNIEnv *env);
void init_app(JNIEnv* env,jclass,jobject context,jobject classLoader);
void readCodeItem(JNIEnv *env, jclass klass,uint8_t *data,size_t data_len);
jstring readAppComponentFactory(JNIEnv *env,jclass,jobject classLoader);
void mergeDexElements(JNIEnv* env,jclass klass,jobject oldClassLoader,jobject newClassLoader);

std::unordered_map<int,std::unordered_map<int,CodeItem*>*> dexMap;

#endif //DPT_DPT_H
