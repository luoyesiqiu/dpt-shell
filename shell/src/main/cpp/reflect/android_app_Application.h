//
// Created by luoyesiqiu
//

#ifndef DPT_ANDROID_APP_APPLICATION_H
#define DPT_ANDROID_APP_APPLICATION_H

#include "dpt_reflect.h"

namespace dpt {
    namespace reflect {

        class android_app_Application : public Reflect{
        private:
            const char * m_class_name;
        public:
            android_app_Application(JNIEnv *env,char *class_name){
                this->m_env = env;
                this->m_class_name = class_name;
                this->m_obj = newInstance();
            }

            android_app_Application(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }
            jobject newInstance();
            void onCreate();
            void attach(jobject context);

        protected:
            const char * getClassName() override{
                if(m_class_name != nullptr){
                    return m_class_name;
                }
                return "android/app/Application";
            }

        };

    } // dpt
} // reflect

#endif //DPT_ANDROID_APP_APPLICATION_H
