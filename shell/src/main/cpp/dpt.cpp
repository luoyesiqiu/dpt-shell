//
// Created by luoyesiqiu
//

#include "dpt.h"

using namespace dpt;

static pthread_mutex_t g_write_dexes_mutex = PTHREAD_MUTEX_INITIALIZER;

static jobject g_realApplicationInstance = nullptr;
static jclass g_realApplicationClass = nullptr;
char *appComponentFactoryChs = nullptr;
char *applicationNameChs = nullptr;
void *codeItemFilePtr = nullptr;

DPT_DATA_SECTION uint8_t DATA_SECTION_BITCODE[] = ".bitcode";
DPT_DATA_SECTION uint8_t DATA_SECTION_RO_DATA[] = ".rodata";
KEEP_SYMBOL DPT_DATA_SECTION uint8_t DPT_UNKNOWN_DATA[] = "1234567890abcdef";

static JNINativeMethod gMethods[] = {
        {"craoc", "(Ljava/lang/String;)V",                               (void *) callRealApplicationOnCreate},
        {"craa",  "(Landroid/content/Context;Ljava/lang/String;)V",      (void *) callRealApplicationAttach},
        {"ia",    "()V", (void *) init_app},
        {"gap",   "()Ljava/lang/String;",         (void *) getSourceDirExport},
        {"gdp",   "()Ljava/lang/String;",         (void *) getCompressedDexesPathExport},
        {"rcf",   "()Ljava/lang/String;",         (void *) readAppComponentFactory},
        {"rapn",   "()Ljava/lang/String;",        (void *) readApplicationName},
        {"cbde",   "(Ljava/lang/ClassLoader;)V",  (void *) combineDexElements},
        {"rde",   "(Ljava/lang/ClassLoader;Ljava/lang/String;)V",        (void *) removeDexElements},
        {"ra", "(Ljava/lang/String;)Ljava/lang/Object;",                               (void *) replaceApplication}
};

DPT_ENCRYPT jobjectArray makePathElements(JNIEnv* env,const char *pathChs) {
    jstring path = env->NewStringUTF(pathChs);
    java_io_File file(env,path);

    java_util_ArrayList files(env);
    files.add(file.getInstance());
    java_util_ArrayList suppressedExceptions(env);

    clock_t cl = clock();
    jobjectArray elements;
    if(android_get_device_api_level() >= __ANDROID_API_M__) {
        elements = dalvik_system_DexPathList::makePathElements(env,
                                                    files.getInstance(),
                                                    nullptr,
                                                    suppressedExceptions.getInstance());
    }
    else {
        elements = dalvik_system_DexPathList::makeDexElements(env,
                                                    files.getInstance(),
                                                    nullptr,
                                                    suppressedExceptions.getInstance());
    }
    printTime("makePathElements success, took = ", cl);
    return elements;
}

DPT_ENCRYPT void combineDexElement(JNIEnv* env, jclass __unused, jobject targetClassLoader, const char* pathChs) {
    jobjectArray extraDexElements = makePathElements(env,pathChs);

    dalvik_system_BaseDexClassLoader targetBaseDexClassLoader(env,targetClassLoader);

    jobject originDexPathListObj = targetBaseDexClassLoader.getPathList();

    dalvik_system_DexPathList targetDexPathList(env,originDexPathListObj);

    jobjectArray originDexElements = targetDexPathList.getDexElements();

    jsize extraSize = env->GetArrayLength(extraDexElements);
    jsize originSize = env->GetArrayLength(originDexElements);

    dalvik_system_DexPathList::Element element(env, nullptr);
    jclass ElementClass = element.getClass();
    jobjectArray  newDexElements = env->NewObjectArray(originSize + extraSize,ElementClass, nullptr);

    for(int i = 0;i < originSize;i++) {
        jobject elementObj = env->GetObjectArrayElement(originDexElements, i);
        env->SetObjectArrayElement(newDexElements,i,elementObj);
    }

    for(int i = originSize;i < originSize + extraSize;i++) {
        jobject elementObj = env->GetObjectArrayElement(extraDexElements, i - originSize);
        env->SetObjectArrayElement(newDexElements,i,elementObj);
    }

    targetDexPathList.setDexElements(newDexElements);

    DLOGD("success");
}

DPT_ENCRYPT void combineDexElements(JNIEnv* env, jclass klass, jobject targetClassLoader) {
    char compressedDexesPathChs[256] = {0};
    getCompressedDexesPath(env,compressedDexesPathChs, ARRAY_LENGTH(compressedDexesPathChs));

    combineDexElement(env, klass, targetClassLoader, compressedDexesPathChs);

#ifndef DEBUG
    junkCodeDexProtect(env);
#endif
    DLOGD("success");
}

DPT_ENCRYPT void removeDexElements(JNIEnv* env,jclass __unused,jobject classLoader,jstring elementName){
    dalvik_system_BaseDexClassLoader oldBaseDexClassLoader(env,classLoader);

    jobject dexPathListObj = oldBaseDexClassLoader.getPathList();

    dalvik_system_DexPathList dexPathList(env,dexPathListObj);

    jobjectArray dexElements = dexPathList.getDexElements();

    jint oldLen = env->GetArrayLength(dexElements);

    jint newLen = oldLen;
    const char *removeElementNameChs = env->GetStringUTFChars(elementName,nullptr);

    for(int i = 0;i < oldLen;i++) {
        jobject elementObj = env->GetObjectArrayElement(dexElements, i);

        dalvik_system_DexPathList::Element element(env,elementObj);
        jobject fileObj = element.getPath();
        java_io_File javaIoFile(env,fileObj);
        jstring fileName = javaIoFile.getName();
        if(fileName == nullptr){
            DLOGW("got an empty file name");
            continue;
        }
        const char* fileNameChs = env->GetStringUTFChars(fileName,nullptr);
        DLOGD("removeDexElements[%d] old path = %s",i,fileNameChs);

        if(strncmp(fileNameChs,removeElementNameChs,256) == 0){
            newLen--;
        }
        env->ReleaseStringUTFChars(fileName,fileNameChs);
    }

    dalvik_system_DexPathList::Element arrayElement(env, nullptr);
    jclass ElementClass = arrayElement.getClass();
    jobjectArray newElementArray = env->NewObjectArray(newLen,ElementClass,nullptr);

    DLOGD("oldlen = %d , newlen = %d",oldLen,newLen);

    jint newArrayIndex = 0;

    for(int i = 0;i < oldLen;i++) {
        jobject elementObj = env->GetObjectArrayElement(dexElements, i);

        dalvik_system_DexPathList::Element element(env,elementObj);
        jobject fileObj = element.getPath();
        java_io_File javaIoFile(env,fileObj);
        jstring fileName = javaIoFile.getName();
        if(fileName == nullptr){
            DLOGW("got an empty file name");
            continue;
        }
        const char* fileNameChs = env->GetStringUTFChars(fileName,nullptr);

        if(strncmp(fileNameChs,removeElementNameChs,256) == 0){
            DLOGD("will remove item: %s",fileNameChs);
            env->ReleaseStringUTFChars(fileName,fileNameChs);
            continue;
        }
        env->ReleaseStringUTFChars(fileName,fileNameChs);

        env->SetObjectArrayElement(newElementArray,newArrayIndex++,elementObj);
    }

    dexPathList.setDexElements(newElementArray);
    DLOGD("success");
}

DPT_ENCRYPT jstring readAppComponentFactory(JNIEnv *env, jclass __unused) {

    if(appComponentFactoryChs == nullptr) {
        void *package_addr = nullptr;
        size_t package_size = 0;
        load_package(env,&package_addr,&package_size);

        uint64_t entry_size = 0;
        void *entry_addr = nullptr;
        bool needFree = read_zip_file_entry(package_addr, package_size , AY_OBFUSCATE(ACF_NAME_IN_ZIP), &entry_addr, &entry_size);
        if(!needFree) {
            char *newChs = (char *) calloc(entry_size + 1, sizeof(char));
            if (entry_size != 0) {
                memcpy(newChs, entry_addr, entry_size);
            }
            appComponentFactoryChs = (char *)newChs;
        }
        else {
            appComponentFactoryChs = (char *) entry_addr;

        }
        unload_package(package_addr, package_size);
    }

    DLOGD("result: %s", appComponentFactoryChs);
    return env->NewStringUTF((appComponentFactoryChs));
}

DPT_ENCRYPT jstring readApplicationName(JNIEnv *env, jclass __unused) {
    if(applicationNameChs == nullptr) {
        void *package_addr = nullptr;
        size_t package_size = 0;
        load_package(env, &package_addr, &package_size);

        uint64_t entry_size = 0;
        void *entry_addr = nullptr;
        bool needFree = read_zip_file_entry(package_addr, package_size, AY_OBFUSCATE(APP_NAME_IN_ZIP), &entry_addr,
                                            &entry_size);
        if (!needFree) {
            char *newChs = (char *) calloc(entry_size + 1, sizeof(char));
            if (entry_size != 0) {
                memcpy(newChs, entry_addr, entry_size);
            }
            applicationNameChs = newChs;
        } else {
            applicationNameChs = (char *) entry_addr;
        }
        unload_package(package_addr, package_size);
    }
    DLOGD("result: %s", applicationNameChs);
    return env->NewStringUTF((applicationNameChs));
}

DPT_ENCRYPT void createAntiRiskProcess() {
    pid_t child = fork();
    if(child < 0) {
        DLOGW("fork fail!");
        detectFrida();
    }
    else if(child == 0) {
        DLOGD("in child process");
        detectFrida();
        doPtrace();
    }
    else {
        DLOGD("in main process, child pid: %d", child);
        protectChildProcess(child);
        detectFrida();
    }
}

void decrypt_section(const char* section_name, int temp_prot, int target_prot) {
    Dl_info info;
    dladdr((const void *)decrypt_section,&info);
    Elf_Shdr shdr;

    get_elf_section(&shdr,info.dli_fname,section_name);
    Elf_Off offset = shdr.sh_offset;
    Elf_Word size = shdr.sh_size;

    void *target = (u_char *)info.dli_fbase + offset;

    int ret = dpt_mprotect(target,(void *)((uint8_t *)target + size),temp_prot);
    if(ret == -1) {
        abort();
    }

    u_char *bitcode = (u_char *)malloc(size);
    struct rc4_state dec_state;
    rc4_init(&dec_state, reinterpret_cast<const u_char *>(DPT_UNKNOWN_DATA), 16);
    rc4_crypt(&dec_state, reinterpret_cast<const u_char *>(target),
              reinterpret_cast<u_char *>(bitcode),
              size);

    memcpy(target,bitcode,size);
    DPT_FREE(bitcode);

    int mprotect_ret = dpt_mprotect(target,(void *)((uint8_t *)target + size),target_prot);
    if(mprotect_ret == -1) {
        abort();
    }
}

void decrypt_bitcode() {
    decrypt_section((char *)DATA_SECTION_BITCODE, PROT_READ | PROT_WRITE | PROT_EXEC, PROT_READ | PROT_EXEC);
}

void init_dpt() {
#ifdef DECRYPT_BITCODE
    decrypt_bitcode();
#endif
    DLOGI("call!");

    dpt_hook();
    createAntiRiskProcess();
}

jclass getRealApplicationClass(JNIEnv *env, const char *applicationClassName) {
    if (g_realApplicationClass == nullptr) {
        jclass applicationClass = env->FindClass(applicationClassName);
        g_realApplicationClass = (jclass) env->NewGlobalRef(applicationClass);
    }
    return g_realApplicationClass;
}

DPT_ENCRYPT jobject getApplicationInstance(JNIEnv *env, jstring applicationClassName) {
    if (g_realApplicationInstance == nullptr) {
        const char *applicationClassNameChs = env->GetStringUTFChars(applicationClassName, nullptr);

        size_t len = strnlen(applicationClassNameChs,128) + 1;
        char *appNameChs = static_cast<char *>(calloc(len, 1));
        parseClassName(applicationClassNameChs, appNameChs);

        DLOGD("getApplicationInstance %s -> %s",applicationClassNameChs,appNameChs);


        jclass appClass = getRealApplicationClass(env, appNameChs);
        jmethodID _init = env->GetMethodID(appClass, "<init>", "()V");
        jobject appInstance = env->NewObject(appClass, _init);
        if (env->ExceptionCheck() || nullptr == appInstance) {
            env->ExceptionClear();
            DLOGW("getApplicationInstance fail!");
            return nullptr;
        }
        g_realApplicationInstance = env->NewGlobalRef(appInstance);

        free(appNameChs);
        DLOGD("getApplicationInstance success!");

    }
    return g_realApplicationInstance;
}

DPT_ENCRYPT void callRealApplicationOnCreate(JNIEnv *env, jclass, jstring realApplicationClassName) {

    jobject appInstance = getApplicationInstance(env,realApplicationClassName);
    android_app_Application application(env,appInstance);
    application.onCreate();

    DLOGD("Application.onCreate() called!");

}

DPT_ENCRYPT void callRealApplicationAttach(JNIEnv *env, jclass, jobject context,
                                         jstring realApplicationClassName) {

    jobject appInstance = getApplicationInstance(env,realApplicationClassName);

    android_app_Application application(env,appInstance);
    application.attach(context);

    DLOGD("Application.attach() called!");

}

DPT_ENCRYPT jobject replaceApplication(JNIEnv *env, jclass klass, jstring realApplicationClassName){

    jobject appInstance = getApplicationInstance(env, realApplicationClassName);
    if (appInstance == nullptr) {
        DLOGW("getApplicationInstance fail!");
        return nullptr;
    }
    replaceApplicationOnLoadedApk(env,klass,appInstance);
    replaceApplicationOnActivityThread(env,klass,appInstance);
    DLOGD("replace application success");
    return appInstance;
}

DPT_ENCRYPT void replaceApplicationOnActivityThread(JNIEnv *env,jclass __unused, jobject realApplication){
    android_app_ActivityThread activityThread(env);
    activityThread.setInitialApplication(realApplication);
    DLOGD("setInitialApplication() called!");
}

DPT_ENCRYPT void replaceApplicationOnLoadedApk(JNIEnv *env, jclass __unused,jobject realApplication) {
    android_app_ActivityThread activityThread(env);

    jobject mBoundApplicationObj = activityThread.getBoundApplication();

    android_app_ActivityThread::AppBindData appBindData(env,mBoundApplicationObj);
    jobject loadedApkObj = appBindData.getInfo();

    android_app_LoadedApk loadedApk(env,loadedApkObj);

    //make it null
    loadedApk.setApplication(nullptr);

    jobject mAllApplicationsObj = activityThread.getAllApplication();

    java_util_ArrayList arrayList(env,mAllApplicationsObj);

    jobject removed = (jobject)arrayList.remove(0);
    if(removed != nullptr){
        DLOGD("proxy application removed");
    }

    jobject ApplicationInfoObj = loadedApk.getApplicationInfo();

    android_content_pm_ApplicationInfo applicationInfo(env,ApplicationInfoObj);

    char applicationName[128] = {0};
    getClassName(env,realApplication, applicationName, ARRAY_LENGTH(applicationName));

    DLOGD("applicationName = %s",applicationName);
    char realApplicationNameChs[128] = {0};
    parseClassName(applicationName,realApplicationNameChs);
    jstring realApplicationName = env->NewStringUTF(realApplicationNameChs);
    auto realApplicationNameGlobal = (jstring)env->NewGlobalRef(realApplicationName);

    android_content_pm_ApplicationInfo appInfo(env,appBindData.getAppInfo());

    //replace class name
    applicationInfo.setClassName(realApplicationNameGlobal);
    appInfo.setClassName(realApplicationNameGlobal);

    // call make application
    loadedApk.makeApplication(JNI_FALSE,nullptr);

    DLOGD("makeApplication() called!");
}


DPT_ENCRYPT static bool registerNativeMethods(JNIEnv *env) {
    jclass JniBridgeClass = env->FindClass(AY_OBFUSCATE("com/luoyesiqiu/shell/JniBridge"));
    if(JniBridgeClass == nullptr) {
        DLOGF("cannot find JniBridge class!");
    }
    if (env->RegisterNatives(JniBridgeClass, gMethods, sizeof(gMethods) / sizeof(gMethods[0])) ==
        0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}


DPT_ENCRYPT void init_app(JNIEnv *env, jclass __unused) {
    DLOGD("called!");
    clock_t start = clock();

    void *package_addr = nullptr;
    size_t package_size = 0;
    load_package(env, &package_addr, &package_size);

    uint64_t entry_size = 0;
    if(codeItemFilePtr == nullptr) {
        read_zip_file_entry(package_addr, package_size, AY_OBFUSCATE(CODE_ITEM_NAME_IN_ZIP), &codeItemFilePtr, &entry_size);
    }
    else {
        DLOGD("no need read codeitem from zip");
    }
    readCodeItem((uint8_t *)codeItemFilePtr,entry_size);

    pthread_mutex_lock(&g_write_dexes_mutex);
    extractDexesInNeeded(env, package_addr, package_size);
    pthread_mutex_unlock(&g_write_dexes_mutex);

    unload_package(package_addr, package_size);
    printTime("read package data took =" , start);
}

DPT_ENCRYPT void readCodeItem(uint8_t *data,size_t data_len) {

    if (data != nullptr && data_len >= 0) {
        data::MultiDexCode *dexCode = data::MultiDexCode::getInst();

        dexCode->init(data, data_len);
        DLOGI("version = %d, dexCount = %d", dexCode->readVersion(),
              dexCode->readDexCount());
        int indexCount = 0;
        uint32_t *dexCodeIndex = dexCode->readDexCodeIndex(&indexCount);
        for (int i = 0; i < indexCount; i++) {
            DLOGI("dexCodeIndex[%d] = %d", i, *(dexCodeIndex + i));
            uint32_t dexCodeOffset = *(dexCodeIndex + i);
            uint16_t methodCount = dexCode->readUInt16(dexCodeOffset);

            DLOGD("dexCodeOffset[%d] = %d, methodCount[%d] = %d", i, dexCodeOffset, i,
                  methodCount);
            auto codeItemMap = new std::unordered_map<int, data::CodeItem *>();
            uint32_t codeItemIndex = dexCodeOffset + 2;
            for (int k = 0; k < methodCount; k++) {
                data::CodeItem *codeItem = dexCode->nextCodeItem(&codeItemIndex);
                uint32_t methodIdx = codeItem->getMethodIdx();
                codeItemMap->insert(std::pair<int, data::CodeItem *>(methodIdx, codeItem));
            }
            dexMap.insert(std::pair<int, std::unordered_map<int, data::CodeItem *> *>(i, codeItemMap));

        }
        DLOGD("map size = %lu", (unsigned long)dexMap.size());
    }
}

DPT_ENCRYPT JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {

    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        DLOGF("GetEnv() fail!");
        return JNI_ERR;
    }

    if (registerNativeMethods(env) == JNI_FALSE) {
        DLOGF("register native methods fail!");
        return JNI_ERR;
    }

    DLOGI("called!");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(__unused JavaVM* vm,__unused void* reserved) {
    DLOGI("called!");
}