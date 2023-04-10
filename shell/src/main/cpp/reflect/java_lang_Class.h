//
// Created by luoyesiqiu
//

#ifndef DPT_JAVA_LANG_CLASS_H
#define DPT_JAVA_LANG_CLASS_H

#include "dpt_reflect.h"
#include "../dpt_jni.h"
namespace dpt {
    namespace reflect {

        class java_lang_Class : Reflect{
        public:
            java_lang_Class(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }
        public:
            jstring getName();
        protected:
            const char * getClassName() override{
                return "java/lang/Class";
            }

        };

    } // dpt
} // reflect

#endif //DPT_JAVA_LANG_CLASS_H
