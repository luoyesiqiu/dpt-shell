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

jobject W_CallObjectMethod(JNIEnv* env,jclass klass,jobject obj,const char* name,const char* sig,...){
    if(nullptr == env || nullptr == klass || nullptr == obj || nullptr == name || nullptr == sig ){
        return nullptr;
    }
    va_list arg;
    va_start(arg,sig);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    jobject retObj = env->CallObjectMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        return nullptr;
    }

    return retObj;
}
jint W_CallIntMethod(JNIEnv* env,jclass klass,jobject obj,const char* name,const char* sig,...){
    if(nullptr == env || nullptr == klass || nullptr == obj || nullptr == name || nullptr == sig ){
        return 0;
    }
    va_list arg;
    va_start(arg,sig);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    jint ret = env->CallIntMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        return 0;
    }

    return ret;
}

jboolean W_CallBooleanMethod(JNIEnv* env,jclass klass,jobject obj,const char* name,const char* sig,...){
    if(nullptr == env || nullptr == klass || nullptr == obj || nullptr == name || nullptr == sig ){
        return JNI_FALSE;
    }
    va_list arg;
    va_start(arg,sig);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    jboolean ret = env->CallBooleanMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        return JNI_FALSE;
    }

    return ret;
}


void W_CallVoidMethod(JNIEnv* env,jclass klass,jobject obj,const char* name,const char* sig,...){
    if(nullptr == env || nullptr == klass || nullptr == obj || nullptr == name || nullptr == sig ){
        return;
    }
    va_list arg;
    va_start(arg,sig);
    jmethodID jmethodId = env->GetMethodID(klass,name,sig);
    env->CallVoidMethodV(obj,jmethodId,arg);
    va_end(arg);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
    }
}

void W_DeleteLocalRef(JNIEnv* env,jobject obj){
    if(nullptr != obj){
        env->DeleteLocalRef(obj);
    }
}
