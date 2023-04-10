//
// Created by luoyesiqiu
//

#include "android_app_LoadedApk.h"

namespace dpt {
    namespace reflect {

        void android_app_LoadedApk::setApplication(jobject application) {
            jni::SetObjectField(m_env,m_obj,&m_application_field,application);
        }

        void android_app_LoadedApk::setApplicationInfo(jobject applicationInfo) {
            jni::SetObjectField(m_env,m_obj,&m_application_info_field,applicationInfo);
        }

        jobject android_app_LoadedApk::getApplicationInfo() {
            return jni::GetObjectField(m_env,m_obj,&m_application_info_field);
        }

        jobject android_app_LoadedApk::makeApplication(jboolean forceDefaultAppClass,
                                                       jobject instrumentation) {
            return jni::CallObjectMethod(m_env,m_obj,"makeApplication","(ZLandroid/app/Instrumentation;)Landroid/app/Application;"
                                         ,forceDefaultAppClass
                                         ,instrumentation);
        }

    } // dpt
} // reflect