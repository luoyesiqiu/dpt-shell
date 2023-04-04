//
// Created by luoyesiqiu
//

#include "dalvik_system_BaseDexClassLoader.h"

namespace dpt {
    namespace reflect {
        jobjectArray dalvik_system_BaseDexClassLoader::getPathList()  {
            auto pathListObj = static_cast<jobjectArray>(jni::GetObjectField(m_env,
                                                                                     getInstance(),
                                                                                     &path_list_field));
             return pathListObj;
        }

        void dalvik_system_BaseDexClassLoader::setPathList(jobject pathList) {
            jni::SetObjectField(m_env,
                                getInstance(),
                                &path_list_field,
                                pathList);
        }
    }
}