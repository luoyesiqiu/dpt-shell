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
        public:
            java_io_File(JNIEnv *env,jobject obj){
                this->m_env = env;
                this->m_obj = obj;
            }
            jstring getName();
        protected:
            const char *getClassName() {
                return "java/io/File";
            }
        };
    }
}

#endif //DPT_JAVA_IO_FILE_H
