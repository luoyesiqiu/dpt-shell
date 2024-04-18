//
// Created by luoyesiqiu
//

#include "dalvik_system_DexPathList.h"

jobjectArray dalvik_system_DexPathList::makePathElements(JNIEnv *env, jobject files, jobject optimizedDirectory,
                        jobject suppressedExceptions) {

    jclass cls = env->FindClass("dalvik/system/DexPathList");

    jmethodID jmethodId = env->GetStaticMethodID(cls,
                                                 "makePathElements",
                                                 "(Ljava/util/List;Ljava/io/File;Ljava/util/List;)[Ldalvik/system/DexPathList$Element;");
    jobjectArray retObj = (jobjectArray)env->CallStaticObjectMethod(cls,
                                                                    jmethodId,
                                                                    files,
                                                                    optimizedDirectory,
                                                                    suppressedExceptions);
    return retObj;
}

jobjectArray dalvik_system_DexPathList::makeDexElements(JNIEnv *env, jobject files, jobject optimizedDirectory,
                                                         jobject suppressedExceptions) {

    jclass cls = env->FindClass("dalvik/system/DexPathList");

    jmethodID jmethodId = env->GetStaticMethodID(cls,
                                                 "makeDexElements",
                                                 "(Ljava/util/ArrayList;Ljava/io/File;Ljava/util/ArrayList;)[Ldalvik/system/DexPathList$Element;");
    jobjectArray retObj = (jobjectArray)env->CallStaticObjectMethod(cls,
                                                                    jmethodId,
                                                                    files,
                                                                    optimizedDirectory,
                                                                    suppressedExceptions);
    return retObj;
}

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
