//
// Created by luoyesiqiu
//

#include "dalvik_system_DexPathList.h"

jobjectArray dalvik_system_DexPathList::getDexElements() {
    auto dexElements = static_cast<jobjectArray>(jni::GetObjectField(m_env,
                                                                             getInstance(),
                                                                             &dex_elements_field));

    return dexElements;
}

void dalvik_system_DexPathList::setDexElements(jobjectArray dexElements) {
    jni::SetObjectField(m_env,
                        getInstance(),
                        &dex_elements_field,
                        dexElements);
}

jobject dalvik_system_DexPathList::Element::getPath() {
    jobject path = jni::GetObjectField(m_env,
                        getInstance(),
                        &path_field);

    return path;
}

void dalvik_system_DexPathList::Element::setPath(jobject path) {
    jni::SetObjectField(m_env,
                        getInstance(),
                        &path_field,
                        path);
}
