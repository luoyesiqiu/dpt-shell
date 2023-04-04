//
// Created by luoyesiqiu
//

#include "java_util_ArrayList.h"
namespace dpt {
    namespace reflect {
        jboolean ArrayList::remove(jobject obj) {
            return jni::CallBooleanMethod(m_env, m_obj, "remove", "(Ljava/lang/Object;)Z", JNI_FALSE, obj);
        }

        jobject ArrayList::remove(int i) {
            return jni::CallObjectMethod(m_env, m_obj, "remove", "(I)Ljava/lang/Object;", i);
        }

        jboolean ArrayList::add(jobject obj) {
            return jni::CallBooleanMethod(m_env, m_obj, "add", "(Ljava/lang/Object;)Z", JNI_FALSE, obj);
        }

        void ArrayList::add(int i, jobject obj) {
            jni::CallVoidMethod(m_env, m_obj, "add", "(ILjava/lang/Object;)Z", i, obj);
        }
    }
}
