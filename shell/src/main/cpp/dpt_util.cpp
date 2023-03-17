//
// Created by luoyesiqiu
//

#include <libgen.h>
#include <ctime>
#include <elf.h>
#include "dpt_util.h"
#include "common/dpt_log.h"
#include "minizip-ng/mz_strm.h"

#ifdef __LP64__
#define Elf_Ehdr Elf64_Ehdr
#define Elf_Shdr Elf64_Shdr
#define Elf_Sym  Elf64_Sym
#define Elf_Off  Elf64_Off
#else
#define Elf_Ehdr Elf32_Ehdr
#define Elf_Shdr Elf32_Shdr
#define Elf_Sym  Elf32_Sym
#define Elf_Off  Elf32_Off
#endif

/**
 * location中提取dex的索引
 * 比如：base.apk!classes2.dex会转成1
 */
int parse_dex_number(std::string *location) {
    char buf[3] = {0};
    if (location->find(".dex") != std::string::npos) {
        const char *chs = strchr(location->c_str(), '!');

        if(nullptr != chs) {
            sscanf(chs, "%*[^0-9]%[^.]", buf);
        }
        else{
            const char* chs2 = strchr(location->c_str(), ':');
            if(nullptr != chs2) {
                sscanf(chs2, "%*[^0-9]%[^.]", buf);
            }
            else{
                sprintf(buf, "%s", "1");
            }
        }
    } else {
        sprintf(buf, "%s", "1");
    }

    int dexIndex = 0;
    sscanf(buf, "%d", &dexIndex);
    return dexIndex - 1;
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

void getClassName(JNIEnv *env,jobject obj,char *destClassName){
    jclass objCls = env->GetObjectClass(obj);
    jclass ClassCls = env->GetObjectClass(objCls);
    jmethodID getNameMethodId = env->GetMethodID(ClassCls,"getName","()Ljava/lang/String;");
    jstring classNameInner = (jstring)env->CallObjectMethod(ClassCls,getNameMethodId);

    const char *classNameInnerChs = env->GetStringUTFChars(classNameInner,nullptr);

    strcpy(destClassName,classNameInnerChs);

    env->ReleaseStringUTFChars(classNameInner,classNameInnerChs);
}

void readPackageName(char *packageName,size_t max_len){
    if(packageName == nullptr){
        return;
    }
    char cmdline_path[128] = {0};
    snprintf(cmdline_path,128,"/proc/%d/cmdline",getpid());
    FILE *fp = fopen(cmdline_path,"rb");
    if(nullptr == fp){
        return;
    }
    fgets(packageName,max_len,fp);
    fclose(fp);
    for(int i = 0;i < max_len;i++){
        if(packageName[i] == ':'){
            packageName[i] = '\0';
            break;
        }
    }

}

jobject getActivityThreadInstance(JNIEnv *env) {
    jclass ActivityThreadClass = env->FindClass("android/app/ActivityThread");

    jmethodID currentActivityThread = env->GetStaticMethodID(ActivityThreadClass,
                                                             "currentActivityThread",
                                                             "()Landroid/app/ActivityThread;");
    jobject activityThreadInstance = env->CallStaticObjectMethod(ActivityThreadClass,
                                                                 currentActivityThread);
    if (env->ExceptionCheck() || nullptr == activityThreadInstance) {
        env->ExceptionClear();
        W_DeleteLocalRef(env, ActivityThreadClass);
        DLOGW("currentActivityThread call fail.");
        return nullptr;
    }

    return activityThreadInstance;
}

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

void getApkPath(JNIEnv *env,char *apkPathOut,size_t max_out_len){
    jobject sActivityThreadObj = getActivityThreadInstance(env);

    jclass ActivityThreadClass = env->GetObjectClass(sActivityThreadObj);

    jfieldID mBoundApplicationField = env->GetFieldID(ActivityThreadClass,
                                                      "mBoundApplication",
                                                      "Landroid/app/ActivityThread$AppBindData;");
    jobject mBoundApplicationObj = env->GetObjectField(sActivityThreadObj,mBoundApplicationField);

    jclass AppBindDataClass = env->GetObjectClass(mBoundApplicationObj);

    jfieldID appInfoField = env->GetFieldID(AppBindDataClass,"appInfo","Landroid/content/pm/ApplicationInfo;");

    jobject appInfoObj = env->GetObjectField(mBoundApplicationObj,appInfoField);

    jclass ApplicationInfoClass = env->GetObjectClass(appInfoObj);

    jfieldID sourceDirField = env->GetFieldID(ApplicationInfoClass,"sourceDir","Ljava/lang/String;");

    jstring sourceDir = (jstring)env->GetObjectField(appInfoObj,sourceDirField);

    const char *sourceDirChs = env->GetStringUTFChars(sourceDir,nullptr);
    strncpy(apkPathOut,sourceDirChs,max_out_len);
    DLOGD("getApkPath: %s",apkPathOut);
}

jstring getApkPathExport(JNIEnv *env,jclass) {
    char apkPathChs[256] = {0};
    getApkPath(env,apkPathChs,256);

    return env->NewStringUTF(apkPathChs);
}

void getCompressedDexesPath(char *outDexZipPath,size_t max_len) {
    char packageName[256] = {0};
    readPackageName(packageName,256);
    snprintf(outDexZipPath,max_len,"/data/data/%s/%s/%s",packageName,CACHE_DIR,DEXES_ZIP_NAME);
}

jstring getCompressedDexesPathExport(JNIEnv *env,jclass klass){
    char dexesPath[256] = {0};
    getCompressedDexesPath(dexesPath,256);
    return env->NewStringUTF(dexesPath);
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

    return count == sub_len ? 0 : -1;
}

void load_zip(const char* zip_file_path,void **zip_addr,off_t *zip_size){
    int fd = open(zip_file_path,O_RDONLY);
    if(fd < 0){
        DLOGD("load_zip cannot open file!");
        return;
    }
    struct stat fst;
    fstat(fd,&fst);
    const int page_size = getpagesize();
    const off_t need_zip_size = (fst.st_size / page_size) * page_size + page_size;
    DLOGD("load_zip fst.st_size = %lu,need size = %lu",fst.st_size,need_zip_size);
    *zip_addr = mmap(nullptr, need_zip_size, PROT_READ, MAP_PRIVATE, fd, 0);
    *zip_size = fst.st_size;
}

void *read_zip_file_entry(void* zip_addr,off_t zip_size,const char* entry_name,int64_t *entry_size){

    void *mem_stream = nullptr;
    void *zip_handle = nullptr;

    mz_stream_mem_create(&mem_stream);
    mz_stream_mem_set_buffer(mem_stream, zip_addr, zip_size);
    mz_stream_open(mem_stream, nullptr, MZ_OPEN_MODE_READ);

    mz_zip_create(&zip_handle);
    int32_t err = mz_zip_open(zip_handle, mem_stream, MZ_OPEN_MODE_READ);
    if(err == MZ_OK){
        int32_t i = 0;
        err = mz_zip_goto_first_entry(zip_handle);
        while (err == MZ_OK) {
            mz_zip_file *file_info = nullptr;
            err = mz_zip_entry_get_info(zip_handle, &file_info);
            if (err == MZ_OK) {
                if(strncmp(file_info->filename,entry_name,128) == 0) {
                    DLOGD("read_zip_file_entry entry name = %s,file size = %ld", file_info->filename,file_info->uncompressed_size);
                    err = mz_zip_entry_read_open(zip_handle, 0, nullptr);
                    if(err != MZ_OK){
                        DLOGW("read_zip_file_entry not prepared: %d",err);
                        return nullptr;
                    }
                    char *entry_data = (char *)calloc(file_info->uncompressed_size + 1,1);
                    char buf[1024] = {0};
                    int32_t bytes_read = -1;
                    int32_t cp_index = 0;
                    do {
                        bytes_read = mz_zip_entry_read(zip_handle, buf,
                                                               1024);
                        if(bytes_read < 0){
                            break;
                        }
                        memcpy(entry_data + cp_index,buf,bytes_read);
                        cp_index += bytes_read;

                    } while (bytes_read > 0);
                    DLOGD("read_zip_file_entry reading file: %s,read bytes: %d",entry_name,cp_index);

                    *entry_size = file_info->uncompressed_size;
                    return entry_data;
                }
            }
            else{
                DLOGW("read_zip_file_entry mz_zip_goto_next_entry error!");
                break;
            }
            err = mz_zip_goto_next_entry(zip_handle);
        }
    }
    else{
        DLOGW("read_zip_file_entry mz_zip_open fail: %d",err);
    }

    return nullptr;
}

const char* find_symbol_in_elf_file(const char *elf_file,int keyword_count,...){
    FILE *elf_fp = fopen(elf_file, "r");
    if(elf_fp) {
        fseek(elf_fp, 0L, SEEK_END);
        size_t lib_size = ftell(elf_fp);
        fseek(elf_fp, 0L, SEEK_SET);

        char *data = (char *) calloc(lib_size, 1);
        fread(data, 1, lib_size, elf_fp);

        char *elf_bytes_data = data;
        Elf_Ehdr *ehdr = (Elf_Ehdr*)elf_bytes_data;

        Elf_Shdr *shdr = (Elf_Shdr *)(((uint8_t*) elf_bytes_data) + ehdr->e_shoff);

        va_list kw_list;

        for (int i = 0; i < ehdr->e_shnum;i++) {

            if(shdr->sh_type == SHT_STRTAB){
                const char* str_base = (char *)((uint8_t*)elf_bytes_data + shdr->sh_offset);
                char* ptr = (char *)str_base;

                for(int k = 0; ptr < (str_base + shdr->sh_size);k++){
                    const char* item_value = ptr;
                    size_t item_len = strnlen(item_value,128);
                    ptr += (item_len + 1);

                    if(item_len == 0){
                        continue;
                    }
                    int match_count = 0;
                    va_start(kw_list,keyword_count);
                    for(int n = 0;n < keyword_count;n++){
                        const char *keyword = va_arg(kw_list,const char*);
                        if(strstr(item_value,keyword)){
                            match_count++;
                        }
                    }
                    va_end(kw_list);
                    if(match_count == keyword_count){
                        return item_value;
                    }
                }
                break;
            }

            shdr++;
        }
        fclose(elf_fp);
        free(data);
    }
}

void hexDump(const char* name,const void* data, size_t size){
    char ascii[17];
    size_t i, j;
    ascii[16] = '\0';
    char *buffer = (char*)calloc(size,1);
    const size_t MAX_LEN = size/2;
    char *item = (char*)calloc(MAX_LEN,1);
    for (i = 0; i < size; ++i) {
        memset(item,0,MAX_LEN);
        snprintf(item,MAX_LEN,"%02X ", ((unsigned char*)data)[i]);
        strncat(buffer,item,MAX_LEN);
        if (((unsigned char*)data)[i] >= ' ' && ((unsigned char*)data)[i] <= '~') {
            ascii[i % 16] = ((unsigned char*)data)[i];
        } else {
            ascii[i % 16] = '.';
        }
        if ((i+1) % 8 == 0 || i+1 == size) {
            memset(item,0,MAX_LEN);
            snprintf(item,MAX_LEN,"%s"," ");
            strncat(buffer,item,MAX_LEN);

            if ((i+1) % 16 == 0) {
                memset(item,0,MAX_LEN);
                snprintf(item,MAX_LEN,"|  %s \n", ascii);
                strncat(buffer,item,MAX_LEN);

            } else if (i+1 == size) {
                ascii[(i+1) % 16] = '\0';
                if ((i+1) % 16 <= 8) {
                    memset(item,0,MAX_LEN);
                    snprintf(item,MAX_LEN,"%s"," ");
                    strncat(buffer,item,MAX_LEN);
                }
                for (j = (i+1) % 16; j < 16; ++j) {
                    memset(item,0,MAX_LEN);
                    snprintf(item,MAX_LEN,"%s","   ");
                    strncat(buffer,item,MAX_LEN);
                }
                memset(item,0,MAX_LEN);
                snprintf(item,MAX_LEN,"|  %s \n", ascii);
                strncat(buffer,item,MAX_LEN);
            }
        }
    }
    DLOGD("%s: \n%s",name,buffer);
    free(item);
    free(buffer);
}

void appendLog(const char* log){
    FILE *fp = fopen("nlog.log","aw");
    if(NULL != fp){
        fwrite(log,1,strlen(log),fp);
        fwrite("\n",1,1,fp);
        fclose(fp);
    }
}

void printTime(const char* msg,clock_t start){
    clock_t end = clock();
    DLOGD("%s %lf",msg,(double)(end - start) / CLOCKS_PER_SEC);
}

void getThreadName(char *threadName){
    prctl(PR_GET_NAME, (unsigned long)threadName);
}