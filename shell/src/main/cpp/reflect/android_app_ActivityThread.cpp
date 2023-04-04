//
// Created by luoyesiqiu
//

#include "android_app_ActivityThread.h"

namespace dpt {
    namespace reflect {
        jobject android_app_ActivityThread::currentActivityThread() {
            if(m_obj == nullptr) {
                jclass cls = getClass();

                m_obj = jni::CallStaticObjectMethod(m_env, cls,
                                                    "currentActivityThread",
                                                    "()Landroid/app/ActivityThread;");
            }
            return m_obj;
        }

        jobject android_app_ActivityThread::getBoundApplication() {
            jobject boundApplication = jni::GetObjectField(m_env,m_obj,&bound_application_field);
            return boundApplication;
        }

        jobject android_app_ActivityThread::AppBindData::getAppInfo() {
            jobject appInfo = jni::GetObjectField(m_env,m_obj,&app_info_field);
            return appInfo;
        }
    }

} // dpt