//
// Created by luoyesiqiu
//

#include "dpt.h"

//缓存变量
static jobject g_realApplicationInstance = nullptr;
static jclass g_realApplicationClass = nullptr;
static jobject g_context = nullptr;
void* zip_addr = nullptr;
off_t zip_size;
char *appComponentFactoryChs = nullptr;
void *codeItemFilePtr = nullptr;

static JNINativeMethod gMethods[] = {
        {"craoc", "(Ljava/lang/String;)V",                               (void *) callRealApplicationOnCreate},
        {"craa",  "(Landroid/content/Context;Ljava/lang/String;)V",      (void *) callRealApplicationAttach},
        {"ia",    "(Landroid/content/Context;Ljava/lang/ClassLoader;)V", (void *) init_app},
        {"gap",   "()Ljava/lang/String;",         (void *) getCompressedDexesPathExport},
        {"rcf",   "(Ljava/lang/ClassLoader;)Ljava/lang/String;",         (void *) readAppComponentFactory},
        {"mde",   "(Ljava/lang/ClassLoader;Ljava/lang/ClassLoader;)V",        (void *) mergeDexElements},
        {"ra", "(Ljava/lang/String;)V",                               (void *) replaceApplication}
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
    zip_uint64_t entry_size;
    if(zip_addr == nullptr){
        char apkPathChs[256] = {0};
        getApkPath(env,apkPathChs,256);
        load_zip(apkPathChs,&zip_addr,&zip_size);
    }

    if(appComponentFactoryChs == nullptr) {
        appComponentFactoryChs = (char*)read_zip_file_entry(zip_addr, zip_size,"app_acf", &entry_size);
    }
    DLOGD("readAppComponentFactory = %s", appComponentFactoryChs);
    return env->NewStringUTF((appComponentFactoryChs));
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

void replaceApplication(JNIEnv *env, jclass klass, jstring realApplicationClassName){
    const char *applicationClassName = env->GetStringUTFChars(realApplicationClassName, nullptr);

    char *appNameChs = static_cast<char *>(calloc(strlen(applicationClassName) + 1, 1));
    parseClassName(applicationClassName, appNameChs);

    jobject appInstance = getApplicationInstance(env, appNameChs);
    if (appInstance == nullptr) {
        DLOGW("replaceApplication getApplicationInstance fail!");
        env->ReleaseStringUTFChars(realApplicationClassName, applicationClassName);
        free(appNameChs);
        return;
    }

    replaceApplicationOnActivityThread(env,klass,appInstance);

    char pkgChs[128] = {0};
    readPackageName(pkgChs);
    DLOGD("replaceApplication = %s",pkgChs);
    jstring packageName = env->NewStringUTF(pkgChs);
    replaceApplicationOnLoadedApk(env,klass,packageName,appInstance);

    DLOGD("replace application success");
}

void replaceApplicationOnActivityThread(JNIEnv *env,jclass klass, jobject realApplication){
    jclass ActivityThreadClass = env->FindClass("android/app/ActivityThread");
    jfieldID sActivityThreadField = env->GetStaticFieldID(ActivityThreadClass,
                                                          "sCurrentActivityThread",
                                                          "Landroid/app/ActivityThread;");

    jobject sActivityThreadObj = env->GetStaticObjectField(ActivityThreadClass,sActivityThreadField);

    jfieldID  mInitialApplicationField = env->GetFieldID(ActivityThreadClass,
                                                         "mInitialApplication",
                                                         "Landroid/app/Application;");
    env->SetObjectField(sActivityThreadObj,mInitialApplicationField,realApplication);
    DLOGD("replaceApplicationOnActivityThread success");

}

void replaceApplicationOnLoadedApk(JNIEnv *env, jclass klass,jobject proxyApplication, jobject realApplication) {
    jobject ActivityThreadObj = getActivityThreadInstance(env);
    jclass ActivityThreadClass = env->GetObjectClass(ActivityThreadObj);

    jfieldID mBoundApplicationField = env->GetFieldID(ActivityThreadClass,
                                                      "mBoundApplication",
                                                      "Landroid/app/ActivityThread$AppBindData;");
    jobject mBoundApplicationObj = env->GetObjectField(ActivityThreadObj,mBoundApplicationField);

    jclass AppBindDataClass = env->GetObjectClass(mBoundApplicationObj);

    jfieldID infoField = env->GetFieldID(AppBindDataClass,
                                         "info",
                                         "Landroid/app/LoadedApk;");

    jobject loadedApkObj = env->GetObjectField(mBoundApplicationObj,infoField);

    jclass LoadedApkClass = env->GetObjectClass(loadedApkObj);

    jfieldID mApplicationField = env->GetFieldID(LoadedApkClass,
                                                 "mApplication",
                                                 "Landroid/app/Application;");

    //make it null
    env->SetObjectField(loadedApkObj,mApplicationField,nullptr);

    jfieldID mAllApplicationsField = env->GetFieldID(ActivityThreadClass,
                                                    "mAllApplications",
                                                    "Ljava/util/ArrayList;");

    jobject mAllApplicationsObj = env->GetObjectField(ActivityThreadObj,mAllApplicationsField);

    jclass ArrayListClass = env->GetObjectClass(mAllApplicationsObj);

    jmethodID removeMethodId = env->GetMethodID(ArrayListClass,
                                                "remove",
                                                "(Ljava/lang/Object;)Z");

    jmethodID addMethodId = env->GetMethodID(ArrayListClass,
                                             "add",
                                             "(Ljava/lang/Object;)Z");

    env->CallBooleanMethod(mAllApplicationsObj,removeMethodId,proxyApplication);

    env->CallBooleanMethod(mAllApplicationsObj,addMethodId,realApplication);

    jfieldID mApplicationInfoField = env->GetFieldID(LoadedApkClass,
                                                     "mApplicationInfo",
                                                     "Landroid/content/pm/ApplicationInfo;");

    jobject ApplicationInfoObj = env->GetObjectField(loadedApkObj,mApplicationInfoField);

    jclass ApplicationInfoClass = env->GetObjectClass(ApplicationInfoObj);

    char applicationName[128] = {0};
    getClassName(env,realApplication,applicationName);
    char realApplicationNameChs[128] = {0};
    parseClassName(applicationName,realApplicationNameChs);
    jstring realApplicationName = env->NewStringUTF(realApplicationNameChs);
    jstring realApplicationNameGlobal = (jstring)env->NewGlobalRef(realApplicationName);

    jfieldID classNameField = env->GetFieldID(ApplicationInfoClass,"className","Ljava/lang/String;");

    //replace class name
    env->SetObjectField(ApplicationInfoObj,classNameField,realApplicationNameGlobal);

    jmethodID makeApplicationMethodId = env->GetMethodID(LoadedApkClass,"makeApplication","(ZLandroid/app/Instrumentation;)Landroid/app/Application;");
    // call make application
    env->CallObjectMethod(loadedApkObj,makeApplicationMethodId,false,nullptr);

    DLOGD("replaceApplicationOnLoadedApk success!");
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
        clock_t start = clock();
        zip_uint64_t entry_size;

        char apkPathChs[256] = {0};
        getApkPath(env,apkPathChs,256);

        if(zip_addr == nullptr){
            load_zip(apkPathChs,&zip_addr,&zip_size);
        }

        char compressedDexesPathChs[256] = {0};
        getCompressedDexesPath(compressedDexesPathChs, 256);

        if(access(compressedDexesPathChs, F_OK) == -1){
            zip_uint64_t dex_files_size = 0;
            void *dexFilesData = read_zip_file_entry(zip_addr,zip_size,"i11111i111",&dex_files_size);
            DLOGD("zipCode open = %s",compressedDexesPathChs);
            int fd = open(compressedDexesPathChs, O_CREAT | O_WRONLY  ,S_IRWXU);
            if(fd > 0){
                write(fd,dexFilesData,dex_files_size);
                close(fd);
            }
            else {
                DLOGE("zipCode write fail: %s", strerror(fd));
            }
        }

        if(codeItemFilePtr == nullptr) {
            codeItemFilePtr = read_zip_file_entry(zip_addr,zip_size,"OoooooOooo",&entry_size);
        }
        //hexDump("read_zip_file_item item hexdump", (char *) codeItemFilePtr, 1024);
        readCodeItem(env, klass,(uint8_t*)codeItemFilePtr,entry_size);

        printTime("readCodeItem took =" , start);
    } else {
        AAsset *aAsset = getAsset(env, context, "OoooooOooo");

        g_context = env->NewGlobalRef(context);

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
