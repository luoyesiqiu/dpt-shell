//
// Created by luoyesiqiu
//

#include "java_util_ArrayList.h"
namespace dpt {
    namespace reflect {
        jboolean java_util_ArrayList::remove(jobject obj) {
            return jni::CallBooleanMethod(m_env, m_obj, "remove", "(Ljava/lang/Object;)Z", JNI_FALSE, obj);
        }

        jobject java_util_ArrayList::remove(int i) {
            return jni::CallObjectMethod(m_env, m_obj, "remove", "(I)Ljava/lang/Object;", i);
        }

        jboolean java_util_ArrayList::add(jobject obj) {
            return jni::CallBooleanMethod(m_env, m_obj, "add", "(Ljava/lang/Object;)Z", JNI_FALSE, obj);
        }

        void java_util_ArrayList::add(int i, jobject obj) {
            jni::CallVoidMethod(m_env, m_obj, "add", "(ILjava/lang/Object;)V", i, obj);
        }
    }
}
