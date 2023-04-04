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
    } // dpt
} // reflect