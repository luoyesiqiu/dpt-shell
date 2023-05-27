//
// Created by luoyesiqiu
//

#include "dpt_jni.h"

namespace dpt {
    namespace jni {

        jobject makeBoolean(JNIEnv* env,jboolean value) {
            jclass booleanClass = jni::FindClass(env,"java/lang/Boolean");
            return jni::NewObject(env,booleanClass,"(Z)V",value);
        }

        jobject makeInteger(JNIEnv* env,jint value) {
            jclass integerClass = jni::FindClass(env,"java/lang/Integer");
            return jni::NewObject(env,integerClass,"(I)V",value);
        }

        jobject GetObjectField(JNIEnv* env,jobject obj,const JNINativeField *jniNativeField) {
            return jni::GetObjectField(env,obj,jniNativeField->name,jniNativeField->signature);
        }

        jobject GetObjectField(JNIEnv* env,jobject obj,const char *field_name,const char *sig) {
            if(env == nullptr || obj == nullptr || field_name == nullptr || sig == nullptr){
                return nullptr;
            }
            jclass klass = env->GetObjectClass(obj);
            jfieldID fid = env->GetFieldID(klass,field_name,sig);
            if(env->ExceptionCheck() || fid == nullptr){
                jni::DeleteLocalRef(env,klass);
                return nullptr;
            }
            jobject value = env->GetObjectField(obj,fid);
            if(env->ExceptionCheck() || value == nullptr){
                jni::DeleteLocalRef(env,klass);
                return nullptr;
            }
            return value;
        }

        jobject GetStaticObjectField(JNIEnv* env,jclass klass,const JNINativeField *jniNativeField) {
            return jni::GetStaticObjectField(env,klass,jniNativeField->name,jniNativeField->signature);
        }

        jobject GetStaticObjectField(JNIEnv* env,jclass klass,const char *field_name,const char *sig) {
            if(env == nullptr || klass == nullptr || field_name == nullptr || sig == nullptr){
                return nullptr;
            }
            jfieldID fid = env->GetFieldID(klass,field_name,sig);
            if(env->ExceptionCheck() || fid == nullptr){
                return nullptr;
            }
            jobject value = env->GetStaticObjectField(klass,fid);
            if(env->ExceptionCheck() || value == nullptr){
                return nullptr;
            }
            return value;
        }

        void SetObjectField(JNIEnv* env,jobject obj,const JNINativeField *jniNativeField,jobject value) {
            SetObjectField(env,obj,jniNativeField->name,jniNativeField->signature,value);
        }

        void SetObjectField(JNIEnv* env,jobject obj,const char *field_name,const char *sig,jobject value) {
            if(env == nullptr || obj == nullptr || field_name == nullptr || sig == nullptr){
                return;
            }
            jclass klass = env->GetObjectClass(obj);
            jfieldID fid = env->GetFieldID(klass,field_name,sig);
            if(env->ExceptionCheck() || fid == nullptr){
                jni::DeleteLocalRef(env,klass);
                return;
            }
            env->SetObjectField(obj,fid,value);
            if(env->ExceptionCheck() || value == nullptr){
                jni::DeleteLocalRef(env,klass);
                return;
            }
        }

        void SetStaticObjectField(JNIEnv* env,jclass klass,const JNINativeField *jniNativeField,jobject value) {
            SetStaticObjectField(env,klass,jniNativeField->name,jniNativeField->signature,value);
        }

        void SetStaticObjectField(JNIEnv* env,jclass klass,const char *field_name,const char *sig,jobject value) {
            if(env == nullptr || klass == nullptr || field_name == nullptr || sig == nullptr){
                return;
            }
            jfieldID fid = env->GetFieldID(klass,field_name,sig);
            if(env->ExceptionCheck() || fid == nullptr){
                jni::DeleteLocalRef(env,klass);
                return;
            }
            env->SetStaticObjectField(klass,fid,value);
            if(env->ExceptionCheck() || value == nullptr){
                jni::DeleteLocalRef(env,klass);
                return;
            }
        }

        jclass FindClass(JNIEnv *env, const char *class_name) {
            if (nullptr == env || nullptr == class_name) {
                return nullptr;
            }
            jclass cls = env->FindClass(class_name);
            if (env->ExceptionCheck() || nullptr == cls) {
                env->ExceptionClear();
                return nullptr;
            }
            return cls;
        }

        jobject NewObject(JNIEnv *env, jclass klass, const char *sig, ...) {
            if (nullptr == env || nullptr == klass || nullptr == sig) {
                return nullptr;
            }
            va_list arg;
            va_start(arg, sig);
            jmethodID jmethodId = env->GetMethodID(klass, "<init>", sig);
            if(jmethodId == nullptr){
                jni::DeleteLocalRef(env,klass);
                return nullptr;
            }
            jobject obj = env->NewObjectV(klass, jmethodId, arg);
            va_end(arg);
            if (env->ExceptionCheck() || nullptr == obj) {
                env->ExceptionClear();
                return nullptr;
            }
            return obj;
        }

        jobject CallObjectMethod(JNIEnv *env, jobject obj, const char *name, const char *sig, ...) {
            if (nullptr == env || nullptr == obj || nullptr == name || nullptr == sig) {
                return nullptr;
            }
            jclass klass = env->GetObjectClass(obj);
            if (env->ExceptionCheck() || klass == nullptr) {
                env->ExceptionClear();
                return nullptr;
            }
            va_list arg;
            va_start(arg, sig);
            jmethodID jmethodId = env->GetMethodID(klass, name, sig);
            if(jmethodId == nullptr){
                jni::DeleteLocalRef(env,klass);
                return nullptr;
            }
            jobject retObj = env->CallObjectMethodV(obj, jmethodId, arg);
            va_end(arg);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                DeleteLocalRef(env, klass);
                return nullptr;
            }

            DeleteLocalRef(env, klass);
            return retObj;
        }

        jobject CallStaticObjectMethod(JNIEnv *env, jclass cls, const char *name, const char *sig, ...){
            if (nullptr == env || nullptr == cls || nullptr == name || nullptr == sig) {
                return nullptr;
            }

            jmethodID jmethodId = env->GetStaticMethodID(cls, name, sig);
            if(jmethodId == nullptr){
                return nullptr;
            }
            va_list arg;
            va_start(arg, sig);
            jobject retObj = env->CallStaticObjectMethod(cls, jmethodId, arg);
            va_end(arg);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return nullptr;
            }

            return retObj;
        }

        jint CallIntMethod(JNIEnv *env, jobject obj, const char *name, const char *sig, jint defVal,...) {
            if (nullptr == env || nullptr == obj || nullptr == name || nullptr == sig) {
                return defVal;
            }
            jclass klass = env->GetObjectClass(obj);
            if (env->ExceptionCheck() || klass == nullptr) {
                env->ExceptionClear();
                return defVal;
            }
            va_list arg;
            va_start(arg, defVal);
            jmethodID jmethodId = env->GetMethodID(klass, name, sig);
            if(jmethodId == nullptr){
                jni::DeleteLocalRef(env,klass);
                return defVal;
            }
            jint ret = env->CallIntMethodV(obj, jmethodId, arg);
            va_end(arg);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                DeleteLocalRef(env, klass);
                return defVal;
            }
            return ret;
        }

        jboolean CallBooleanMethod(JNIEnv *env, jobject obj, const char *name, const char *sig,uint32_t defVal,...) {
            if (nullptr == env || nullptr == obj || nullptr == name || nullptr == sig) {
                return defVal != 0;
            }
            jclass klass = env->GetObjectClass(obj);
            if (env->ExceptionCheck() || klass == nullptr) {
                env->ExceptionClear();
                return defVal != 0;
            }
            va_list arg;
            va_start(arg, defVal);
            jmethodID jmethodId = env->GetMethodID(klass, name, sig);
            if(jmethodId == nullptr){
                jni::DeleteLocalRef(env,klass);
                return defVal != 0;
            }
            jboolean ret = env->CallBooleanMethodV(obj, jmethodId, arg);
            va_end(arg);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                DeleteLocalRef(env, klass);
                return defVal != 0;
            }
            DeleteLocalRef(env, klass);
            return ret;
        }

        void CallVoidMethod(JNIEnv *env, jobject obj, const char *name, const char *sig, ...) {
            if (nullptr == env || nullptr == obj || nullptr == name || nullptr == sig) {
                return;
            }
            jclass klass = env->GetObjectClass(obj);
            if (env->ExceptionCheck() || klass == nullptr) {
                env->ExceptionClear();
                return;
            }
            va_list arg;
            va_start(arg, sig);
            jmethodID jmethodId = env->GetMethodID(klass, name, sig);
            if(jmethodId == nullptr){
                jni::DeleteLocalRef(env,klass);
                return;
            }
            env->CallVoidMethodV(obj, jmethodId, arg);
            va_end(arg);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                DeleteLocalRef(env, klass);
            }
            DeleteLocalRef(env, klass);
        }

        void DeleteLocalRef(JNIEnv *env, jobject obj) {
            if (nullptr != obj) {
                env->DeleteLocalRef(obj);
            }
        }
    }
}