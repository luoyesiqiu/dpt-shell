//
// Created by luoyesiqiu
//

#ifndef DPT_JNIWRAPPER_H
#define DPT_JNIWRAPPER_H

#include <jni.h>
#include "common/dpt_log.h"

jobject W_NewObject(JNIEnv* env,jclass klass,const char* sig,...);
jobject W_CallObjectMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,...);
void W_CallVoidMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,...);
jint W_CallIntMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,...);
jboolean W_CallBooleanMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,...);
void W_DeleteLocalRef(JNIEnv* env,jobject obj);
#endif //DPT_JNIWRAPPER_H
