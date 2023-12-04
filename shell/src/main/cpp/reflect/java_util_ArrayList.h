//
// Created by luoyesiqiu
//

#ifndef DPT_JAVA_UTIL_ARRAYLIST_H
#define DPT_JAVA_UTIL_ARRAYLIST_H

#include "dpt_reflect.h"

using namespace dpt::reflect;

#include "dpt_reflect.h"
namespace dpt {
    namespace reflect {
        class java_util_ArrayList : public Reflect{
        private:
            const char *className = "java/util/ArrayList";
        public:
            java_util_ArrayList(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }

            java_util_ArrayList(JNIEnv *env){
                this->m_env = env;
                jclass ArrayListClass = jni::FindClass(env,className);
                this->m_obj = jni::NewObject(env, ArrayListClass,"()V");
            }
            jboolean remove(jobject obj);
            jobject remove(int i);
            jboolean add(jobject obj);
            void add(int i,jobject obj);

        protected:
            const char *getClassName() {
                return className;
            }
        };
    }
}
#endif //DPT_JAVA_UTIL_ARRAYLIST_H
