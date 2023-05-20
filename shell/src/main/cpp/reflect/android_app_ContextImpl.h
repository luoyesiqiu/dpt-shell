//
// Created by luoyesiqiu
//

#ifndef DPT_ANDROID_APP_CONTEXTIMPL_H
#define DPT_ANDROID_APP_CONTEXTIMPL_H

#include "dpt_reflect.h"
#include "../dpt_jni.h"
namespace dpt {
    namespace reflect {

        class android_app_ContextImpl : public Reflect{
            private:
            const char * m_class_name;
            jni::JNINativeField m_outer_context_field = {"mOuterContext",
                                                               "Landroid/content/Context;"};
        public:
            void setOuterContext(jobject context);
            android_app_ContextImpl(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }
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

#endif //DPT_ANDROID_APP_CONTEXTIMPL_H
