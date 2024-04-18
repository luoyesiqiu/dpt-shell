//
// Created by luoyesiqiu
//

#ifndef DPT_DALVIK_SYSTEM_DEXPATHLIST_H
#define DPT_DALVIK_SYSTEM_DEXPATHLIST_H

#include "dpt_reflect.h"

using namespace dpt::reflect;

namespace dpt{
    namespace reflect {
        //DexPathList
        class dalvik_system_DexPathList : public Reflect {
        private:
            const jni::JNINativeField  dex_elements_field = {"dexElements",
                                                             "[Ldalvik/system/DexPathList$Element;"};
            public:
                dalvik_system_DexPathList(JNIEnv *env,jobject obj){
                    this->m_env = env;
                    this->m_obj = obj;
                }

                jobjectArray getDexElements();

                void setDexElements(jobjectArray dexElements);
                static jobjectArray makePathElements(JNIEnv *env, jobject files, jobject optimizedDirectory,
                                        jobject suppressedExceptions);

                static jobjectArray makeDexElements(JNIEnv *env, jobject files, jobject optimizedDirectory,
                                                 jobject suppressedExceptions);

            protected:
                const char *getClassName() {
                    return "dalvik/system/DexPathList";
                }
        public:
            //DexPathList$Element
            class Element : public Reflect {
                private:
                    const jni::JNINativeField path_field = {"path",
                                                            "Ljava/io/File;"};
                public:
                    Element(JNIEnv *env,jobject obj){
                        this->m_env = env;
                        this->m_obj = obj;
                    }
                    jobject getPath();
                    void setPath(jobject path);

                protected:
                    const char *getClassName() {
                        return "dalvik/system/DexPathList$Element";
                    }
            };

        };
    }
}
#endif //DPT_DALVIK_SYSTEM_DEXPATHLIST_H
