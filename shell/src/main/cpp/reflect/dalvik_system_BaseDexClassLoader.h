//
// Created by luoyesiqiu
//

#ifndef DPT_DALVIK_SYSTEM_BASEDEXCLASSLOADER_H
#define DPT_DALVIK_SYSTEM_BASEDEXCLASSLOADER_H
#include "dpt_reflect.h"
#include "../dpt_jni.h"

using namespace dpt::reflect;

namespace dpt {
    namespace reflect {
        class dalvik_system_BaseDexClassLoader : public Reflect {
        private:
            const jni::JNINativeField path_list_field = {"pathList",
                                                         "Ldalvik/system/DexPathList;"};
        public:
            dalvik_system_BaseDexClassLoader(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }
            jobjectArray getPathList();
            void setPathList(jobject pathList);

        protected:
            const char *getClassName() {
                return "dalvik/system/BaseDexClassLoader";
            }
        };
    }
}
#endif //DPT_DALVIK_SYSTEM_BASEDEXCLASSLOADER_H
