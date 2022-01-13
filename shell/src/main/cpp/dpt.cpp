//
// Created by luoyesiqiu
//

#include "dpt.h"

//缓存变量
static jobject g_realApplicationInstance = nullptr;
static jclass g_realApplicationClass = nullptr;
static jobject g_context = nullptr;
static jobject g_packageName = nullptr;

static JNINativeMethod gMethods[] = {
        {"craoc", "(Ljava/lang/String;)V",                               (void *) callRealApplicationOnCreate},
        {"craa",  "(Landroid/content/Context;Ljava/lang/String;)V",      (void *) callRealApplicationAttach},
        {"ia",    "(Landroid/content/Context;Ljava/lang/ClassLoader;)V", (void *) init_app},
        {"gap",   "(Ljava/lang/ClassLoader;)Ljava/lang/String;",         (void *) getApkPath},
        {"rcf",   "(Ljava/lang/ClassLoader;)Ljava/lang/String;",         (void *) readAppComponentFactory},
        {"mde",   "(Ljava/lang/ClassLoader;Ljava/lang/ClassLoader;)V",        (void *) mergeDexElements}
};

void mergeDexElements(JNIEnv* env,jclass klass,jobject oldClassLoader,jobject newClassLoader){
    jclass BaseDexClassLoaderClass = env->FindClass("dalvik/system/BaseDexClassLoader");
    jfieldID  pathList = env->GetFieldID(BaseDexClassLoaderClass,"pathList","Ldalvik/system/DexPathList;");
    jobject oldDexPathListObj = env->GetObjectField(oldClassLoader,pathList);
    if(env->ExceptionCheck() || nullptr == oldDexPathListObj ){
        env->ExceptionClear();
        W_DeleteLocalRef(env,BaseDexClassLoaderClass);
        DLOGW("mergeDexElements oldDexPathListObj get fail");
        return;
    }
    jobject newDexPathListObj = env->GetObjectField(newClassLoader,pathList);
    if(env->ExceptionCheck() || nullptr == newDexPathListObj){
        env->ExceptionClear();
        W_DeleteLocalRef(env,BaseDexClassLoaderClass);
        W_DeleteLocalRef(env,oldDexPathListObj);
        DLOGW("mergeDexElements newDexPathListObj get fail");
        return;
    }

    jclass DexPathListClass = env->FindClass("dalvik/system/DexPathList");
    jfieldID  dexElementField = env->GetFieldID(DexPathListClass,"dexElements","[Ldalvik/system/DexPathList$Element;");


    jobjectArray newClassLoaderDexElements = static_cast<jobjectArray>(env->GetObjectField(
            newDexPathListObj, dexElementField));
    if(env->ExceptionCheck() || nullptr == newClassLoaderDexElements){
        env->ExceptionClear();
        W_DeleteLocalRef(env,BaseDexClassLoaderClass);
        W_DeleteLocalRef(env,oldDexPathListObj);
        W_DeleteLocalRef(env,newDexPathListObj);
        W_DeleteLocalRef(env,DexPathListClass);
        DLOGW("mergeDexElements new dexElements get fail");
        return;
    }

    jobjectArray oldClassLoaderDexElements = static_cast<jobjectArray>(env->GetObjectField(
            oldDexPathListObj, dexElementField));
    if(env->ExceptionCheck() || nullptr == oldClassLoaderDexElements){
        env->ExceptionClear();
        W_DeleteLocalRef(env,BaseDexClassLoaderClass);
        W_DeleteLocalRef(env,oldDexPathListObj);
        W_DeleteLocalRef(env,newDexPathListObj);
        W_DeleteLocalRef(env,DexPathListClass);
        W_DeleteLocalRef(env,newClassLoaderDexElements);
        DLOGW("mergeDexElements old dexElements get fail");
        return;
    }

    jint oldLen = env->GetArrayLength(oldClassLoaderDexElements);
    jint newLen = env->GetArrayLength(newClassLoaderDexElements);

    DLOGD("mergeDexElements oldlen = %d , newlen = %d",oldLen,newLen);

    jclass ElementClass = env->FindClass("dalvik/system/DexPathList$Element");

    jobjectArray  newElementArray = env->NewObjectArray(oldLen + newLen,ElementClass, nullptr);

    for(int i = 0;i < newLen;i++) {
        jobject elementObj = env->GetObjectArrayElement(newClassLoaderDexElements, i);
        env->SetObjectArrayElement(newElementArray,i,elementObj);
    }


    for(int i = newLen;i < oldLen + newLen;i++) {
        jobject elementObj = env->GetObjectArrayElement(oldClassLoaderDexElements, i - newLen);
        env->SetObjectArrayElement(newElementArray,i,elementObj);
    }

    env->SetObjectField(oldDexPathListObj, dexElementField,newElementArray);

    DLOGD("mergeDexElements success");
}

jstring readAppComponentFactory(JNIEnv *env, jclass klass, jobject classLoader) {
    jstring apkPath = getApkPath(env, klass, classLoader);
    jstring fileName = env->NewStringUTF("assets/app_acf");
    jbyteArray appComponentFactory = readFromZip(env, apkPath, fileName);
    jclass StringClass = env->FindClass("java/lang/String");
    jstring StringObj = (jstring) W_NewObject(env, StringClass, "([B)V", appComponentFactory);
    const char *StringChs = env->GetStringUTFChars(StringObj, NULL);
    DLOGD("readAppComponentFactory = %s", StringChs);
    return StringObj;
}

void init_dpt(JNIEnv *env) {
    DLOGI("init_dpt!");
}

extern "C" void _init(void) {
    DLOGI("_init!");

    dpt_hook();
}

jclass getRealApplicationClass(JNIEnv *env, const char *applicationClassName) {
    if (g_realApplicationClass == nullptr) {
        jclass applicationClass = env->FindClass(applicationClassName);
        g_realApplicationClass = (jclass) env->NewGlobalRef(applicationClass);
    }
    return g_realApplicationClass;
}

jobject getApplicationInstance(JNIEnv *env, const char *applicationClassName) {
    if (g_realApplicationInstance == nullptr) {
        jclass appClass = getRealApplicationClass(env, applicationClassName);
        jmethodID _init = env->GetMethodID(appClass, "<init>", "()V");
        jobject appInstance = env->NewObject(appClass, _init);
        if (env->ExceptionCheck() || nullptr == appInstance) {
            env->ExceptionClear();
            DLOGW("getApplicationInstance fail!");
            return nullptr;
        }
        g_realApplicationInstance = env->NewGlobalRef(appInstance);
        DLOGD("getApplicationInstance success!");

    }
    return g_realApplicationInstance;
}

void parseClassName(const char *src, char *dest) {

    for (int i = 0; *(src + i) != '\0'; i++) {
        if (*(src + i) == '.') {
            dest[i] = '/';
        } else {
            *(dest + i) = *(src + i);
        }
    }
}

//
// 调用原始apk Application类的onCreate方法
//
JNIEXPORT void
callRealApplicationOnCreate(JNIEnv *env, jclass, jstring realApplicationClassName) {
    const char *applicationClassName = env->GetStringUTFChars(realApplicationClassName, nullptr);

    char *appNameChs = static_cast<char *>(calloc(strlen(applicationClassName) + 1, 1));
    parseClassName(applicationClassName, appNameChs);

    jobject appInstance = getApplicationInstance(env, appNameChs);
    if (appInstance == nullptr) {
        DLOGW("callRealApplicationOnCreate getApplicationInstance fail!");
        env->ReleaseStringUTFChars(realApplicationClassName, applicationClassName);
        free(appNameChs);
        return;
    }
    DLOGD("callRealApplicationOnCreate className %s -> %s", applicationClassName, appNameChs);
    jclass appClass = getRealApplicationClass(env, appNameChs);
    jmethodID onCreate = env->GetMethodID(appClass, "onCreate", "()V");
    env->CallVoidMethod(appInstance, onCreate);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        DLOGW("callRealApplicationOnCreate occur exception!");
        env->ReleaseStringUTFChars(realApplicationClassName, applicationClassName);
        free(appNameChs);
        return;
    }
    DLOGW("callRealApplicationOnCreate success!");
    env->ReleaseStringUTFChars(realApplicationClassName, applicationClassName);

    free(appNameChs);
}

//
// 调用原始apk Application类的attach方法
//
JNIEXPORT void callRealApplicationAttach(JNIEnv *env, jclass, jobject context,
                                         jstring realApplicationClassName) {
    const char *applicationClassName = env->GetStringUTFChars(realApplicationClassName, nullptr);
    char *appNameChs = static_cast<char *>(calloc(strlen(applicationClassName) + 1, 1));
    parseClassName(applicationClassName, appNameChs);
    DLOGD("callRealApplicationAttach className %s -> %s", applicationClassName, appNameChs);

    jclass appClass = getRealApplicationClass(env, appNameChs);
    jmethodID attachBaseContextMethod = env->GetMethodID(appClass, "attach",
                                                         "(Landroid/content/Context;)V");

    jobject realAppInstance = getApplicationInstance(env, applicationClassName);
    if (realAppInstance == nullptr) {
        DLOGW("callRealApplicationAttach getApplicationInstance fail!");

        env->ReleaseStringUTFChars(realApplicationClassName, applicationClassName);
        free(appNameChs);
        return;
    }
    env->CallVoidMethod(realAppInstance, attachBaseContextMethod, context);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        DLOGW("callRealApplicationAttach occur exception!");
        env->ReleaseStringUTFChars(realApplicationClassName, applicationClassName);
        free(appNameChs);
        return;
    }
    DLOGD("callRealApplicationAttach success!");


    env->ReleaseStringUTFChars(realApplicationClassName, applicationClassName);

    free(appNameChs);

}

bool registerNativeMethods(JNIEnv *env) {
    jclass JniBridgeClass = env->FindClass("com/luoyesiqiu/shell/JniBridge");
    if (env->RegisterNatives(JniBridgeClass, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) ==
        0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}


void init_app(JNIEnv *env, jclass klass, jobject context, jobject classLoader) {
    DLOGD("init_app!");
    if (nullptr == context) {

        jstring apkPath = getApkPath(env, klass, classLoader);
        jstring fileName = env->NewStringUTF("assets/OoooooOooo");
        jbyteArray data = readFromZip(env, apkPath, fileName);


        int len = env->GetArrayLength(data);
        if (len <= 0) {
            DLOGE("readCodeItem Cannot read code item file!");
            return;
        }
        DLOGD("readCodeItem data len = %d", len);
        auto *buf = (uint8_t *) env->GetByteArrayElements(data, nullptr);


        readCodeItem(env, klass, buf,len);
    } else {
        AAsset *aAsset = getAsset(env, context, "OoooooOooo");

        g_context = env->NewGlobalRef(context);

        jclass contextClass = env->GetObjectClass(context);
        jstring packageName = (jstring) W_CallObjectMethod(env, contextClass, context,
                                                           "getPackageName",
                                                           "()Ljava/lang/String;");
        const char *packageNameChs = env->GetStringUTFChars(packageName, nullptr);

        g_packageName = env->NewGlobalRef(packageName);

        DLOGD("init_app %s", packageNameChs);

        if (aAsset != nullptr) {
            int len = AAsset_getLength(aAsset);
            auto buf = (uint8_t *) AAsset_getBuffer(aAsset);
            readCodeItem(env, klass,buf,len);
        }
    }
}

void readCodeItem(JNIEnv *env, jclass klass,uint8_t *data,size_t data_len) {

    if (data != nullptr && data_len >= 0) {

        MultiDexCode *dexCode = MultiDexCode::getInst();

        dexCode->init(data, data_len);
        DLOGI("readCodeItem : version = %d , dexCount = %d", dexCode->readVersion(),
              dexCode->readDexCount());
        int indexCount = 0;
        uint32_t *dexCodeIndex = dexCode->readDexCodeIndex(&indexCount);
        for (int i = 0; i < indexCount; i++) {
            DLOGI("readCodeItem : dexCodeIndex[%d] = %d", i, *(dexCodeIndex + i));
            uint32_t dexCodeOffset = *(dexCodeIndex + i);
            uint16_t methodCount = dexCode->readUInt16(dexCodeOffset);

            DLOGD("readCodeItem : dexCodeOffset[%d] = %d,methodCount[%d] = %d", i, dexCodeOffset, i,
                  methodCount);
            auto codeItemMap = new std::unordered_map<int, CodeItem *>();
            uint32_t codeItemIndex = dexCodeOffset + 2;
            for (int k = 0; k < methodCount; k++) {
                CodeItem *codeItem = dexCode->nextCodeItem(&codeItemIndex);
                uint32_t methodIdx = codeItem->getMethodIdx();
                codeItemMap->insert(std::pair<int, CodeItem *>(methodIdx, codeItem));
            }
            dexMap.insert(std::pair<int, std::unordered_map<int, CodeItem *> *>(i, codeItemMap));

        }
        DLOGD("readCodeItem map size = %ld", dexMap.size());
    }
}


JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {

    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return JNI_ERR;
    }

    if (registerNativeMethods(env) == JNI_FALSE) {
        return JNI_ERR;
    }


    init_dpt(env);

    DLOGI("JNI_OnLoad called!");
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    DLOGI("JNI_OnUnload called!");
}
