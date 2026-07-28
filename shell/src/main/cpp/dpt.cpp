//
// Created by luoyesiqiu
//

#include "dpt.h"
#include "dpt_crypto.h"
#include "external/json/json.hpp"

#include <memory>

using namespace dpt;

static pthread_mutex_t g_write_dexes_mutex = PTHREAD_MUTEX_INITIALIZER;

static jobject g_realApplicationInstance = nullptr;
static jclass g_realApplicationClass = nullptr;

std::optional<std::tuple<uint8_t *,size_t>> g_codeItemFileData;
std::unordered_map<int,std::vector<data::CodeItem *> *> dexMap;

DPT_DATA_SECTION uint8_t DATA_SECTION_BITCODE[] = ".bitcode";
DPT_DATA_SECTION uint8_t DATA_SECTION_RO_DATA[] = ".rodata";
KEEP_SYMBOL DPT_DATA_SECTION uint8_t DPT_UNKNOWN_DATA[] = "1234567890abcdef";

ShellConfig g_shell_config;

static JNINativeMethod gMethods[] = {
        {"craoc", "(Ljava/lang/String;)V",                               (void *) callRealApplicationOnCreate},
        {"ia",    "()V", (void *) init_app},
        {"gap",   "()Ljava/lang/String;",         (void *) getSourceDirExport},
        {"gdp",   "()Ljava/lang/String;",         (void *) getCompressedDexesPathExport},
        {"rcf",   "()Ljava/lang/String;",         (void *) readAppComponentFactory},
        {"rapn",   "()Ljava/lang/String;",        (void *) readApplicationName},
        {"cbde",   "(Ljava/lang/ClassLoader;)V",  (void *) combineDexElements},
        {"rde",   "(Ljava/lang/ClassLoader;Ljava/lang/String;)V",        (void *) removeDexElements},
        {"ra", "(Ljava/lang/String;)Ljava/lang/Object;",                               (void *) replaceApplication},
        {"clinit", "()V",                               (void *) clinit}
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
    DLOGD("result: '%s'", g_shell_config.application_component_factory.c_str());
    return env->NewStringUTF(g_shell_config.application_component_factory.c_str());
}

DPT_ENCRYPT jstring readApplicationName(JNIEnv *env, jclass __unused) {

    DLOGD("result: '%s'", g_shell_config.application_name.c_str());
    return env->NewStringUTF(g_shell_config.application_name.c_str());
}

DPT_ENCRYPT void antiRisk() {
    bool needDetect = ((g_shell_config.risk_check_flags & FLAG_DISABLE_FRIDA_DETECT) == 0)
            || ((g_shell_config.risk_check_flags & FLAG_DISABLE_CRC_DETECT) == 0)
            || ((g_shell_config.risk_check_flags & FLAG_DISABLE_ANTI_DEBUG) == 0);
    if (needDetect) {
        detectRisk();
    }
}

void decrypt_section(const char* section_name, int temp_prot, int target_prot) {
    Dl_info info;
    dladdr((const void *)decrypt_section,&info);
    std::string so_path = {};

    if (info.dli_fname != nullptr) {
        if (info.dli_fname[0] == '/') {
            so_path.assign(info.dli_fname);
        } else {
            auto path = find_so_path(info.dli_fname);
            so_path.assign(path);
        }
    }

    if(so_path.empty()) {
        auto path = find_so_path(SO_NAME);
        so_path.assign(path);
    }

    Elf_Shdr shdr = {};

    get_elf_section(&shdr, so_path.c_str(), section_name);
    Elf_Off offset = shdr.sh_offset;
    Elf_Word size = shdr.sh_size;

    DLOGD("section name: %s, offset: %p, size: %d", section_name, (uint8_t *)offset, size);
    void *target = (u_char *)info.dli_fbase + offset;

    int ret = dpt_mprotect(target, (void *)((uint8_t *)target + size), temp_prot);
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

int getRandom(int l, int r) {
    static std::mt19937 gen(std::random_device{}());
    std::uniform_int_distribution<> dist(l, r);
    return dist(gen);
}

DPT_ENCRYPT void clinit(JNIEnv *env, jclass) {
    int rand = getRandom(1, 100);
    if(rand % 2 == 0) {
        veritySignature(env);
    }
}

DPT_ENCRYPT void callRealApplicationOnCreate(JNIEnv *env, jclass, jstring realApplicationClassName) {

    jobject appInstance = getApplicationInstance(env,realApplicationClassName);
    android_app_Application application(env,appInstance);
    application.onCreate();

    DLOGD("Application.onCreate() called!");

}

DPT_ENCRYPT jobject replaceApplication(JNIEnv *env, jclass klass, jstring realApplicationClassName){

    // replaceApplicationOnLoadedApk now creates the real Application via makeApplication()
    // and calls attach() on it internally. Use that single instance everywhere to avoid
    // the two-instance problem that caused SDK initialization state mismatches on API < 28.
    jobject appInstance = replaceApplicationOnLoadedApk(env, klass, realApplicationClassName);
    if (appInstance == nullptr) {
        DLOGW("replaceApplicationOnLoadedApk returned null!");
        return nullptr;
    }

    // Cache the correct instance so callRealApplicationOnCreate can find it later.
    if (g_realApplicationInstance != nullptr) {
        env->DeleteGlobalRef(g_realApplicationInstance);
    }
    g_realApplicationInstance = env->NewGlobalRef(appInstance);

    replaceApplicationOnActivityThread(env, klass, appInstance);
    DLOGD("replace application success");
    return appInstance;
}

DPT_ENCRYPT void replaceApplicationOnActivityThread(JNIEnv *env,jclass __unused, jobject realApplication){
    android_app_ActivityThread activityThread(env);
    activityThread.setInitialApplication(realApplication);
    DLOGD("setInitialApplication() called!");
}

DPT_ENCRYPT jobject replaceApplicationOnLoadedApk(JNIEnv *env, jclass __unused, jstring realApplicationClassName) {
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

    // Get class name from the jstring parameter directly
    const char *applicationNameChs = env->GetStringUTFChars(realApplicationClassName, nullptr);
    DLOGD("applicationName = %s", applicationNameChs);
    char realApplicationNameChs[128] = {0};
    parseClassName(applicationNameChs, realApplicationNameChs);
    env->ReleaseStringUTFChars(realApplicationClassName, applicationNameChs);

    jstring realApplicationName = env->NewStringUTF(realApplicationNameChs);
    auto realApplicationNameGlobal = (jstring)env->NewGlobalRef(realApplicationName);

    android_content_pm_ApplicationInfo appInfo(env,appBindData.getAppInfo());

    //replace class name
    applicationInfo.setClassName(realApplicationNameGlobal);
    appInfo.setClassName(realApplicationNameGlobal);

    // makeApplication creates the real Application instance and calls attach() on it.
    // Capture and return the newly created instance so callers can use the same object.
    jobject newApp = loadedApk.makeApplication(JNI_FALSE, nullptr);

    DLOGD("makeApplication() called, newApp = %p", newApp);
    return newApp;
}


DPT_ENCRYPT static bool registerNativeMethods(JNIEnv *env) {
    jclass JniBridgeClass = env->FindClass(g_shell_config.jni_class_name.c_str());
    if(JniBridgeClass == nullptr) {
        DLOGF("cannot find class: %s!", g_shell_config.jni_class_name.c_str());
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

    if(!g_codeItemFileData.has_value()) {
        auto entry_data = read_zip_file_entry(package_addr, package_size, AY_OBFUSCATE(CODE_ITEM_NAME_IN_ZIP));
        if(entry_data.has_value()) {
            g_codeItemFileData = std::move(entry_data);
        }
        printTime("read codeitem data took =" , start);

    }
    else {
        DLOGD("no need read codeitem from zip");
    }
    auto [entry_data, entry_size] = g_codeItemFileData.value();
    readCodeItem((uint8_t *)entry_data, entry_size);

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
        dexMap.reserve(indexCount);
        for (int i = 0; i < indexCount; i++) {
            DLOGI("dexCodeIndex[%d] = %d", i, *(dexCodeIndex + i));
            uint32_t dexCodeOffset = *(dexCodeIndex + i);
            uint16_t methodCount = dexCode->readUInt16(dexCodeOffset);

            DLOGD("dexCodeOffset[%d] = %d, methodCount[%d] = %d", i, dexCodeOffset, i,
                  methodCount);
            auto codeItemVec = new std::vector<data::CodeItem *>(65536);
            uint32_t codeItemIndex = dexCodeOffset + 2;
            for (int k = 0; k < methodCount; k++) {
                data::CodeItem *codeItem = dexCode->nextCodeItem(&codeItemIndex);
                uint32_t methodIdx = codeItem->getMethodIdx();
                codeItemVec->at(methodIdx) = codeItem;
            }
            dexMap.emplace(i, codeItemVec);

        }
        DLOGD("map size = %lu", (unsigned long)dexMap.size());
    }
}

DPT_ENCRYPT void read_shell_config(JNIEnv *env) {
    void *package_addr = nullptr;
    size_t package_size = 0;
    load_package(env, &package_addr, &package_size);

    auto entry = read_zip_file_entry(package_addr, package_size , AY_OBFUSCATE(SHELL_CONFIG_IN_ZIP));
    if(entry.has_value()) {
        auto [entry_data, entry_size] = entry.value();
        std::unique_ptr<uint8_t[]> entry_guard(entry_data);
        if(entry_size > 0) {
            reflect::android_app_ActivityThread activityThread(env);
            jobject mBoundApplicationObj = activityThread.getBoundApplication();
            if (mBoundApplicationObj == nullptr) {
                DLOGE("bound application is null");
                unload_package(package_addr, package_size);
                return;
            }

            reflect::android_app_ActivityThread::AppBindData appBindData(env, mBoundApplicationObj);
            jobject appInfoObj = appBindData.getAppInfo();
            if (appInfoObj == nullptr) {
                DLOGE("app info is null");
                unload_package(package_addr, package_size);
                return;
            }

            reflect::android_content_pm_ApplicationInfo applicationInfo(env, appInfoObj);
            jstring packageNameJstr = applicationInfo.getPackageName();
            if (packageNameJstr == nullptr) {
                DLOGE("package name is null");
                unload_package(package_addr, package_size);
                return;
            }

            const char *packageNameChs = env->GetStringUTFChars(packageNameJstr, nullptr);
            if (packageNameChs == nullptr || packageNameChs[0] == '\0') {
                DLOGE("package name is empty");
                if (packageNameChs != nullptr) {
                    env->ReleaseStringUTFChars(packageNameJstr, packageNameChs);
                }
                unload_package(package_addr, package_size);
                return;
            }

            std::string packageName(packageNameChs);
            env->ReleaseStringUTFChars(packageNameJstr, packageNameChs);
            const char *buildKey = AY_OBFUSCATE(DPT_BUILD_KEY);
            const char *keySep = AY_OBFUSCATE("_");
            std::string key_material = packageName + keySep + buildKey;
            DLOGD("key material for config key: %s", key_material.c_str());

            auto aes_key = hmac_sha256(DPT_UNKNOWN_DATA,
                                       16,
                                       reinterpret_cast<const uint8_t *>(key_material.data()),
                                       key_material.size());
            if (aes_key.size() != 32) {
                DLOGE("derive config aes key failed");
                unload_package(package_addr, package_size);
                return;
            }

            std::vector<uint8_t> indata(entry_data, entry_data + entry_size);

            uint8_t iv[16] = {0};
            memcpy(iv, DPT_UNKNOWN_DATA, 16);
            iv[3] = 0x2f;
            iv[9] = 0x76;
            auto decrypted_data = aes_cbc_decrypt(aes_key.data(), 256, iv, indata.data(), entry_size);
            if (decrypted_data.empty()) {
                DLOGE("decrypt shell config failed");
                unload_package(package_addr, package_size);
                return;
            }

            try {
                std::string jsonStr = std::string(decrypted_data.begin(), decrypted_data.end());
                DLOGD("raw config: '%s'", jsonStr.c_str());

                nlohmann::json shell_config = nlohmann::json::parse(jsonStr);
                const char *keyAppName = AY_OBFUSCATE("app_name");
                const char *keyAcfName = AY_OBFUSCATE("acf_name");
                const char *keyJniClsName = AY_OBFUSCATE("jni_cls_name");
                const char *keyAppSignSha256 = AY_OBFUSCATE("app_sign_sha256");
                const char *keyDexSign = AY_OBFUSCATE("dex_sign");
                const char *keyInsnsXorKey = AY_OBFUSCATE("insns_xor_key");
                const char *keyRiskCheckFlags = AY_OBFUSCATE("risk_check_flags");
                g_shell_config.application_name = shell_config.value(keyAppName, "");
                g_shell_config.application_component_factory = shell_config.value(keyAcfName, "");
                g_shell_config.jni_class_name = shell_config.value(keyJniClsName, "");
                g_shell_config.app_sign_sha256 = shell_config.value(keyAppSignSha256, "");
                g_shell_config.dex_sign = shell_config.value(keyDexSign, "");
                g_shell_config.insns_xor_key = shell_config.value(keyInsnsXorKey, 0);
                g_shell_config.risk_check_flags = shell_config.value(keyRiskCheckFlags, 0);

                DLOGD("application_name = %s", g_shell_config.application_name.c_str());
                DLOGD("application_component_factory = %s", g_shell_config.application_component_factory.c_str());
                DLOGD("jni_class_name = %s", g_shell_config.jni_class_name.c_str());
                DLOGD("app_sign_sha256 = %s", g_shell_config.app_sign_sha256.c_str());
                DLOGD("dex_sign = %s", g_shell_config.dex_sign.c_str());
                DLOGD("insns_xor_key = 0x%x", g_shell_config.insns_xor_key);
                DLOGD("risk_check_flags = 0x%x", g_shell_config.risk_check_flags);
            } catch (const std::exception &e) {
                DLOGE("parse shell config failed: %s", e.what());
            }
        }
    }

    unload_package(package_addr, package_size);
}


void veritySignature(JNIEnv *env) {
    if (!g_shell_config.app_sign_sha256.empty()) {
        jobject application = android_app_ActivityThread::currentApplication(env);

        verifyAppSignature(env, application, g_shell_config.app_sign_sha256.c_str());
    }
}

DPT_ENCRYPT JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {

    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        DLOGF("GetEnv() fail!");
        return JNI_ERR;
    }

    read_shell_config(env);

    antiRisk();

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