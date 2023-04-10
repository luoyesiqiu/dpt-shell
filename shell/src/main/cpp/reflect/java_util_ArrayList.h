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
        public:
            java_util_ArrayList(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }
            jboolean remove(jobject obj);
            jobject remove(int i);
            jboolean add(jobject obj);
            void add(int i,jobject obj);

        protected:
            const char *getClassName() {
                return "java/util/ArrayList";
            }
        };
    }
}
#endif //DPT_JAVA_UTIL_ARRAYLIST_H
