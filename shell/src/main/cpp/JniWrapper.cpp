//
// Created by luoyesiqiu
//

#include "JniWrapper.h"

jobject W_NewObject(JNIEnv* env,jclass klass,const char* sig,...){
    if(nullptr == env || nullptr == klass || nullptr == sig ){
        return nullptr;
    }
    va_list arg;
    va_start(arg,sig);
    jmethodID jmethodId = env->GetMethodID(klass,"<init>",sig);
    jobject obj = env->NewObjectV(klass,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck() || nullptr == obj){
        env->ExceptionClear();
        return nullptr;
    }
    return obj;
}

jobject W_CallObjectMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,...){
    if(nullptr == env || nullptr == obj || nullptr == name || nullptr == sig ){
        return nullptr;
    }
    jclass klass = env->GetObjectClass(obj);
    if(env->ExceptionCheck() || klass == nullptr){
        env->ExceptionClear();
        return nullptr;
    }
    va_list arg;
    va_start(arg,sig);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    jobject retObj = env->CallObjectMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        W_DeleteLocalRef(env,klass);
        return nullptr;
    }

    W_DeleteLocalRef(env,klass);

    return retObj;
}
jint W_CallIntMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,jint defVal,...){
    if(nullptr == env || nullptr == obj || nullptr == name || nullptr == sig ){
        return defVal;
    }
    jclass klass = env->GetObjectClass(obj);
    if(env->ExceptionCheck() || klass == nullptr){
        env->ExceptionClear();
        return defVal;
    }
    va_list arg;
    va_start(arg,defVal);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    jint ret = env->CallIntMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        W_DeleteLocalRef(env,klass);
        return defVal;
    }
    return ret;
}

jboolean W_CallBooleanMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,jboolean defVal,...){
    if(nullptr == env || nullptr == obj || nullptr == name || nullptr == sig ){
        return defVal;
    }
    jclass klass = env->GetObjectClass(obj);
    if(env->ExceptionCheck() || klass == nullptr){
        env->ExceptionClear();
        return defVal;
    }
    va_list arg;
    va_start(arg,defVal);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    jboolean ret = env->CallBooleanMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        W_DeleteLocalRef(env,klass);
        return defVal;
    }
    W_DeleteLocalRef(env,klass);
    return ret;
}

void W_CallVoidMethod(JNIEnv* env,jobject obj,const char* name,const char* sig,...){
    if(nullptr == env || nullptr == obj || nullptr == name || nullptr == sig ){
        return;
    }
    jclass klass = env->GetObjectClass(obj);
    if(env->ExceptionCheck() || klass == nullptr){
        env->ExceptionClear();
        return;
    }
    va_list arg;
    va_start(arg,sig);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    env->CallVoidMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        W_DeleteLocalRef(env,klass);
    }
    W_DeleteLocalRef(env,klass);
}

void W_DeleteLocalRef(JNIEnv* env,jobject obj){
    if(nullptr != obj){
        env->DeleteLocalRef(obj);
    }
}
