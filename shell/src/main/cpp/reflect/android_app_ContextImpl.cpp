//
// Created by luoyesiqiu
//

#include "android_app_ContextImpl.h"

using namespace dpt::reflect;

void android_app_ContextImpl::setOuterContext(jobject context) {
    dpt::jni::SetObjectField(m_env,m_obj,&m_outer_context_field,context);
}