//
// Created by luoyesiqiu
//

#include <libgen.h>
#include <ctime>
#include "dpt_util.h"


jclass getContextClass(JNIEnv *env) {
    if (g_ContextClass == nullptr) {
        jclass ContextClass = env->FindClass("android/content/Context");
        g_ContextClass = (jclass) env->NewGlobalRef(ContextClass);
    }
    return g_ContextClass;
}

AAssetManager *getAssetMgr(JNIEnv *env, jobject assetManager) {
    if (g_AssetMgrInstance == nullptr) {
        g_AssetMgrInstance = AAssetManager_fromJava(env, assetManager);
    }
    return g_AssetMgrInstance;
}

AAsset *getAsset(JNIEnv *env, jobject context, const char *filename) {
    if (context != nullptr) {
        jclass contextClass = getContextClass(env);
        jmethodID getAssetsId = env->GetMethodID(contextClass, "getAssets",
                                                 "()Landroid/content/res/AssetManager;");
        jobject assetManagerObj = env->CallObjectMethod(context, getAssetsId);
        AAssetManager *aAssetManager = getAssetMgr(env, assetManagerObj);
        if (aAssetManager != nullptr) {
            AAsset *aAsset = AAssetManager_open(g_AssetMgrInstance,
                                                filename,
                                                AASSET_MODE_BUFFER);
            return aAsset;
        }
    }
    return nullptr;
}

jstring getApkPath(JNIEnv *env,jclass ,jobject classLoader) {
    jstring emptyStr = env->NewStringUTF("");
    jclass BaseDexClassLoaderClass = env->FindClass("dalvik/system/BaseDexClassLoader");
    jfieldID  pathList = env->GetFieldID(BaseDexClassLoaderClass,"pathList","Ldalvik/system/DexPathList;");
    jobject DexPathListObj = env->GetObjectField(classLoader,pathList);
    if(env->ExceptionCheck() || nullptr == DexPathListObj ){
        env->ExceptionClear();
        W_DeleteLocalRef(env,BaseDexClassLoaderClass);
        DLOGW("getApkPath pathList get fail.");
        return emptyStr;
    }

    jclass DexPathListClass = env->FindClass("dalvik/system/DexPathList");
    jfieldID  dexElementField = env->GetFieldID(DexPathListClass,"dexElements","[Ldalvik/system/DexPathList$Element;");
    jobjectArray Elements = (jobjectArray)env->GetObjectField(DexPathListObj,dexElementField);
    if(env->ExceptionCheck() || nullptr == Elements){
        env->ExceptionClear();
        W_DeleteLocalRef(env,BaseDexClassLoaderClass);
        W_DeleteLocalRef(env,DexPathListClass);
        DLOGW("getApkPath Elements get fail.");

        return emptyStr;
    }
    jsize len = env->GetArrayLength(Elements);
    if(len == 0) {
        DLOGW("getApkPath len ==0.");
        return emptyStr;
    }

    for(int i = 0;i < len;i++) {
        jobject elementObj = env->GetObjectArrayElement(Elements, i);
        if (env->ExceptionCheck() || nullptr == elementObj) {
            env->ExceptionClear();
            DLOGW("getApkPath get Elements item fail");
            continue;
        }
        jclass ElementClass = env->FindClass("dalvik/system/DexPathList$Element");

        jfieldID pathFieldId = env->GetFieldID(ElementClass, "path", "Ljava/io/File;");
        jobject fileObj = env->GetObjectField(elementObj, pathFieldId);
        if (env->ExceptionCheck() || nullptr == fileObj) {
            env->ExceptionClear();
            W_DeleteLocalRef(env, BaseDexClassLoaderClass);
            W_DeleteLocalRef(env, DexPathListClass);
            W_DeleteLocalRef(env, ElementClass);
            DLOGW("getApkPath get path fail");
            return emptyStr;
        }
        jclass FileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethodId = env->GetMethodID(FileClass, "getAbsolutePath",
                                                             "()Ljava/lang/String;");
        jstring absolutePath = static_cast<jstring>(env->CallObjectMethod(fileObj,
                                                                          getAbsolutePathMethodId));
        if (env->ExceptionCheck() || nullptr == absolutePath) {
            env->ExceptionClear();
            W_DeleteLocalRef(env, BaseDexClassLoaderClass);
            W_DeleteLocalRef(env, DexPathListClass);
            W_DeleteLocalRef(env, ElementClass);
            W_DeleteLocalRef(env, FileClass);
            DLOGW("getApkPath get absolutePath fail");
            return emptyStr;
        }

        const char* absolutePathChs = env->GetStringUTFChars(absolutePath,nullptr);
        if(endWith(absolutePathChs,"base.apk") == 0){
            return absolutePath;
        }

    }

    return emptyStr;

}

jbyteArray readFromZip(JNIEnv* env,jstring zipPath,jstring fileName){
    const char* fileNameChs = env->GetStringUTFChars(fileName,nullptr);
    const char* zipPathChs = env->GetStringUTFChars(zipPath,nullptr);

    if(fileNameChs == nullptr || strlen(fileNameChs) == 0){
        return nullptr;
    }

    if(zipPathChs == nullptr || strlen(zipPathChs) == 0){
        return nullptr;
    }
    DLOGD("readFromZip read path = %s,read file = %s",zipPathChs,fileNameChs);
    jclass ZipInputStreamClass = env->FindClass("java/util/zip/ZipInputStream");
    jclass FileInputStreamClass = env->FindClass("java/io/FileInputStream");
    jclass ByteArrayOutputStreamClass = env->FindClass("java/io/ByteArrayOutputStream");
    jclass ZipEntryClass = env->FindClass("java/util/zip/ZipEntry");

    jobject FileInputStreamObj = W_NewObject(env,FileInputStreamClass,"(Ljava/lang/String;)V",zipPath);
    jobject ZipInputStreamObj = W_NewObject(env,ZipInputStreamClass,"(Ljava/io/InputStream;)V",FileInputStreamObj);
    jobject ByteArrayOutputStreamObj = W_NewObject(env,ByteArrayOutputStreamClass,"()V");

    jmethodID  getNextEntryMid = env->GetMethodID(ZipInputStreamClass,"getNextEntry", "()Ljava/util/zip/ZipEntry;");
    jmethodID  readMid = env->GetMethodID(ZipInputStreamClass,"read", "([B)I");
    jmethodID  writeMid = env->GetMethodID(ByteArrayOutputStreamClass,"write", "([BII)V");
    jmethodID  toByteArrayMid = env->GetMethodID(ByteArrayOutputStreamClass,"toByteArray", "()[B");
    jmethodID  getNameMid = env->GetMethodID(ZipEntryClass,"getName", "()Ljava/lang/String;");


    for(;;) {
        jobject ZipEntryObj = env->CallObjectMethod(ZipInputStreamObj,getNextEntryMid);
        if(env->ExceptionCheck() || nullptr == ZipEntryObj){
            env->ExceptionClear();
            DLOGW("readFromZip ZipEntryObj is null");
            break;
        }

        jstring currentName = (jstring)env->CallObjectMethod(ZipEntryObj,getNameMid);
        if(env->ExceptionCheck() || nullptr == currentName){
            env->ExceptionClear();
            DLOGW("readFromZip get name fail.");
            break;
        }
        const char* nameChs = env->GetStringUTFChars(currentName,nullptr);
        if(strcmp(nameChs,fileNameChs) == 0){
            DLOGD("readFromZip zip name = %s",nameChs);
            for(;;){
                jbyteArray  buf = env->NewByteArray(1024);
                jint len = env->CallIntMethod(ZipInputStreamObj,readMid,buf);
                if(env->ExceptionCheck()){
                    env->ExceptionClear();
                    DLOGW("readFromZip call read fail.");
                    break;
                }
                if(len == -1){
                    W_DeleteLocalRef(env,buf);
                    break;
                }

                env->CallVoidMethod(ByteArrayOutputStreamObj,writeMid,buf,0,len);
                if(env->ExceptionCheck()){
                    env->ExceptionClear();
                    DLOGW("readFromZip call write fail.");
                    break;
                }
                W_DeleteLocalRef(env,buf);

            }
        }
        env->ReleaseStringUTFChars(currentName,nameChs);
        W_DeleteLocalRef(env,currentName);
    }
    jbyteArray ret = (jbyteArray)env->CallObjectMethod(ByteArrayOutputStreamObj,toByteArrayMid);
    if(env->ExceptionCheck()){
        env->ExceptionClear();
        DLOGW("readFromZip call toByteArray fail.");
    }
    DLOGD("readFromZip success");

    return ret;
}

int endWith(const char *str,const char* sub){
    if(NULL == str  || NULL == sub){
        return -1;
    }
    size_t target_len = strlen(str);
    size_t sub_len = strlen(sub);

    if(target_len < sub_len){
        return -1;
    }

    int count = 0;
    for(int i = 0; i < sub_len;i++){
        char s_tail = *((str + target_len) - i - 1);
        char sub_tail = *((sub + sub_len) - i - 1);
        if(s_tail == sub_tail){
            count++;
        }
    }
    DLOGD("sublen = %d,count = %d",sub_len,count);

    return count == sub_len ? 0 : -1;
}

void appendLog(const char* log){
    FILE *fp = fopen("nlog.log","aw");
    if(NULL != fp){
        fwrite(log,1,strlen(log),fp);
        fwrite("\n",1,1,fp);
        fclose(fp);
    }
}

void printTime(const char* msg,int start){
    int end = clock();
    DLOGD("%s %lf",msg,(double)(end - start) / CLOCKS_PER_SEC);
}
