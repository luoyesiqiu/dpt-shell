//
// Created by luoyesiqiu
//

#include "android_app_ActivityThread.h"

using namespace dpt::reflect;

jobject android_app_ActivityThread::currentActivityThread(JNIEnv *env) {

    jclass cls = jni::FindClass(env, "android/app/ActivityThread");

    jobject obj = jni::CallStaticObjectMethod(env, cls,
                                        "currentActivityThread",
                                        "()Landroid/app/ActivityThread;");

    jni::DeleteLocalRef(env, cls);
    return obj;
}

jobject android_app_ActivityThread::getBoundApplication() {
    jobject boundApplication = jni::GetObjectField(m_env,m_obj,&bound_application_field);
    return boundApplication;
}

jobject android_app_ActivityThread::getAllApplication() {
    jobject allApplication = jni::GetObjectField(m_env,m_obj,&m_all_application_field);
    return allApplication;
}

void android_app_ActivityThread::setInitialApplication(jobject application) {
    jni::SetObjectField(m_env,m_obj,&m_initial_application_field,application);
}

jobject android_app_ActivityThread::currentApplication(JNIEnv *env) {
    jclass cls = jni::FindClass(env, "android/app/ActivityThread");

    jobject obj = jni::CallStaticObjectMethod(env, cls,
                                              "currentApplication",
                                              "()Landroid/app/Application;");

    jni::DeleteLocalRef(env, cls);
    return obj;
}

jobject android_app_ActivityThread::AppBindData::getAppInfo() {
    jobject appInfo = jni::GetObjectField(m_env,m_obj,&app_info_field);
    return appInfo;
}

jobject android_app_ActivityThread::AppBindData::getInfo() {
    jobject appInfo = jni::GetObjectField(m_env,m_obj,&info_field);
    return appInfo;
}