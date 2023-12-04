//
// Created by luoyesiqiu
//

#ifndef DPT_JAVA_IO_FILE_H
#define DPT_JAVA_IO_FILE_H

#include "dpt_reflect.h"
#include "../dpt_jni.h"

using namespace dpt::reflect;

namespace dpt{
    namespace reflect{
        class java_io_File : public Reflect{
        private:
            const char *className = "java/io/File";
        public:
            java_io_File(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }
            java_io_File(JNIEnv *env,jstring pathname){
                this->m_env = env;
                jclass FileClass = jni::FindClass(env,className);
                this->m_obj = jni::NewObject(env,
                                             FileClass,
                                             "(Ljava/lang/String;)V",
                                             pathname);
            }
            jstring getName();
        protected:
            const char *getClassName() {
                return className;
            }
        };
    }
}

#endif //DPT_JAVA_IO_FILE_H
