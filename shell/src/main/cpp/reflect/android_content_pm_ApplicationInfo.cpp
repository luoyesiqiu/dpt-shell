//
// Created by luoyesiqiu
//

#include "android_content_pm_ApplicationInfo.h"

namespace dpt {
    namespace reflect {
        jstring android_content_pm_ApplicationInfo::getSourceDir() {
            auto sourceDir = (jstring)jni::GetObjectField(m_env,m_obj,&source_dir_field);
            return sourceDir;
        }

        void android_content_pm_ApplicationInfo::setClassName(jobject className) {
            jni::SetObjectField(m_env,m_obj,&class_name_field,className);
        }
    } // dpt
} // reflect