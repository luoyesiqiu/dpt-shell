//
// Created by luoyesiqiu
//

#include "java_lang_Class.h"

namespace dpt {
    namespace reflect {
        jstring java_lang_Class::getName() {
            return static_cast<jstring>(jni::CallObjectMethod(m_env, m_obj, "getName",
                                                              "()Ljava/lang/String;"));
        }
    } // dpt
} // reflect