//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_JNI_H
#define DPT_DPT_JNI_H

#include <jni.h>
#include "common/dpt_log.h"

namespace dpt {
    namespace jni {

        typedef struct {
            const char* name;
            const char* signature;
        } JNINativeField;
        jobject makeBoolean(JNIEnv* env,jboolean value);
        jobject makeInteger(JNIEnv* env,jint value);

        void SetObjectField(JNIEnv* env,jobject obj,const JNINativeField *jniNativeField,jobject value);
        void SetObjectField(JNIEnv* env,jobject obj,const char *field_name,const char *sig,jobject value);

        void SetStaticObjectField(JNIEnv* env,jclass klass,const JNINativeField *jniNativeField,jobject value);
        void SetStaticObjectField(JNIEnv* env,jclass klass,const char *field_name,const char *sig,jobject value);

        jobject GetObjectField(JNIEnv* env,jobject obj,const JNINativeField *jniNativeField);
        jobject GetObjectField(JNIEnv* env,jobject obj,const char *field_name,const char *sig);

        jobject GetStaticObjectField(JNIEnv* env,jclass klass,const JNINativeField *jniNativeField);
        jobject GetStaticObjectField(JNIEnv* env,jclass klass,const char *field_name,const char *sig);

        jclass FindClass(JNIEnv* env,const char* class_name);

        jobject NewObject(JNIEnv *env, jclass klass, const char *sig, ...);

        jobject CallStaticObjectMethod(JNIEnv *env, jclass cls, const char *name, const char *sig, ...);
        jobject CallObjectMethod(JNIEnv *env, jobject obj, const char *name, const char *sig, ...);

        void CallVoidMethod(JNIEnv *env, jobject obj, const char *name, const char *sig, ...);

        jint CallIntMethod(JNIEnv *env, jobject obj, const char *name, const char *sig,
                           jint defVal, ...);
        jboolean CallBooleanMethod(JNIEnv *env, jobject obj, const char *name, const char *sig,
                                   uint32_t defVal, ...);

        void DeleteLocalRef(JNIEnv *env, jobject obj);
    }
}
#endif //DPT_DPT_JNI_H
