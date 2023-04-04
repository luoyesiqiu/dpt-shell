//
// Created by luoyesiqiu
//

#ifndef DPT_REFLECT_H
#define DPT_REFLECT_H

#include <jni.h>
#include <string>
#include "dpt_jni.h"
#include "dpt_log.h"

namespace dpt{
    namespace reflect {
        class Reflect {
        protected:
            JNIEnv *m_env = nullptr;
            jobject m_obj = nullptr;

            virtual const char *getClassName() = 0;

        public:
            jobject getInstance() {
                if (m_obj != nullptr) {
                    return m_obj;
                }
                return nullptr;
            }
            jclass getClass() {
                if (m_obj == nullptr) {
                    return jni::FindClass(m_env, getClassName());
                } else {
                    return m_env->GetObjectClass(m_obj);
                }
            }

        };
    }
}

#endif //DPT_REFLECT_H
