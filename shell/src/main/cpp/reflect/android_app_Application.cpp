//
// Created by luoyesiqiu
//

#include "android_app_Application.h"

namespace dpt {
    namespace reflect {
        void android_app_Application::onCreate() {
            jni::CallVoidMethod(m_env,m_obj,"onCreate", "()V");
        }

        void android_app_Application::attach(jobject context) {
            jni::CallVoidMethod(m_env,m_obj,"attach",
                                "(Landroid/content/Context;)V",context);
        }

        jobject android_app_Application::newInstance() {
            if(m_class_name == nullptr) {
                return m_obj;
            }
            jclass applicationCls = jni::FindClass(m_env,m_class_name);
            jobject instance = jni::NewObject(m_env,applicationCls,"()V");
            return instance;
        }
    } // dpt
} // reflect