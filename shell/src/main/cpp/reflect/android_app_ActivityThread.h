//
// Created by luoyesiqiu
//

#ifndef DPT_ANDROID_APP_ACTIVITYTHREAD_H
#define DPT_ANDROID_APP_ACTIVITYTHREAD_H

#include "dpt_reflect.h"

namespace dpt {
    namespace reflect {
        class android_app_ActivityThread : public Reflect {
        private:
            jni::JNINativeField bound_application_field = {"mBoundApplication",
                                                    "Landroid/app/ActivityThread$AppBindData;"};
            jni::JNINativeField m_all_application_field = {"mAllApplications",
                                                         "Ljava/util/ArrayList;"};
            jni::JNINativeField m_initial_application_field = {"mInitialApplication",
                                                            "Landroid/app/Application;"};
        public:
            android_app_ActivityThread(JNIEnv *env){
                this->m_env = env;
                this->m_obj = currentActivityThread();
            }
            jobject currentActivityThread();
            jobject getBoundApplication();
            jobject getAllApplication();
            void setInitialApplication(jobject application);

        protected:
            const char *getClassName() {
                return "android/app/ActivityThread";
            }
        public:
            class AppBindData : public Reflect{
            private:
                jni::JNINativeField app_info_field = {"appInfo",
                                                      "Landroid/content/pm/ApplicationInfo;"};

                jni::JNINativeField info_field = {"info",
                                                      "Landroid/app/LoadedApk;"};
            protected:
                const char *getClassName() {
                    return "android/app/ActivityThread$AppBindData";
                }
            public:
                AppBindData(JNIEnv *env,jobject obj){
                    this->m_env = env;
                    this->m_obj = obj;
                }
                jobject getAppInfo();
                jobject getInfo();

            };
        };
    }

} // dpt

#endif //DPT_ANDROID_APP_ACTIVITYTHREAD_H
