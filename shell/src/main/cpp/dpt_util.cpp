//
// Created by luoyesiqiu
//

#include <libgen.h>
#include <ctime>
#include <elf.h>
#include "dpt_util.h"
#include "dpt_log.h"

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
        return;
    }
    struct stat fst;
    fstat(fd,&fst);
    *zip_addr = mmap(nullptr, fst.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    *zip_size = fst.st_size;
}

void *read_zip_file_entry(const void* zip_addr,off_t zip_size,const char* entry_name,zip_uint64_t *entry_size){

    DLOGD("read_zip_file_item start = %llx,size = %ld",zip_addr,zip_size);
    zip_error_t  err;
    zip_source_t *zip_src = zip_source_buffer_create(zip_addr,zip_size,0,&err);
    if(nullptr == zip_src){
        DLOGE("read_zip_file_item zip_source_buffer_create err = %s",err.str);
        return nullptr;
    }

    zip_source_keep(zip_src);
    zip_t *achieve = zip_open_from_source(zip_src,ZIP_RDONLY,&err);

    if(achieve != nullptr) {
        size_t entry_number = zip_get_num_files(achieve);
        DLOGD("read_zip_file_item read from mem success,entry num = %zu", entry_number);

        for (size_t i = 0; i < entry_number; i++) {
            const char* entry_name_tmp = zip_get_name(achieve,i,ZIP_FL_ENC_GUESS);
            zip_file_t *file = zip_fopen(achieve, entry_name_tmp, ZIP_FL_ENC_GUESS);
            if (file != nullptr) {
                zip_stat_t zst;
                zip_stat(achieve, entry_name_tmp, 0, &zst);
                if(endWith(entry_name_tmp,entry_name) == 0){
                    DLOGD("read_zip_file_item entry name = %s,size = %u",entry_name_tmp,zst.size);

                    void *entry_data = calloc(zst.size, 1);
                    zip_uint64_t entry_size_tmp = zip_fread(file, entry_data, zst.size);
                    *entry_size = entry_size_tmp;

                    return entry_data;
                }
            } else {
                DLOGE("read_zip_file_item zip entry(%s) open fail!",entry_name_tmp);
            }
        }
    }
    else{
        DLOGE("read_zip_file_item read from mem fail");
    }

    return nullptr;
}

const char* find_symbol_in_elf(void* elf_bytes_data,int keyword_count,...) {
    Elf_Ehdr* ehdr = (Elf_Ehdr*)elf_bytes_data;

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
    return nullptr;
}

int find_in_maps(const char* find_name,pointer_t *start,pointer_t *size,char *full_path) {
    const int MAX_READ_LINE = 10 * 1024;
    char maps_path[128] = {0};
    snprintf(maps_path,128,"/proc/%d/maps",getpid());
    FILE *fp = fopen(maps_path,"r");
    int found = 0;
    if(fp != nullptr) {
        DLOGD("find_in_maps open file success!");
        char *line = (char *)calloc(256,1);
        int read_line = 0;
        while(fgets(line,256,fp) != nullptr){
            if(read_line++ >= MAX_READ_LINE){
                break;
            }
            char flag[10] = {0};
            char item_path[128] = {0};

#ifdef __LP64__
            pointer_t item_start,item_size;
            int ret = sscanf(line, "%llx-%*llx %s %llx %*s %*s %s", &item_start,flag, &item_size, item_path);
            if(ret != 4){
                continue;
            }
#else
            pointer_t item_start,item_size;
            int ret = sscanf(line, "%x-%*x %s %x %*s %*s %s", &item_start,flag,&item_size, item_path);
            if(ret != 4){
                continue;
            }
#endif

            if(flag[0] == 'r' && endWith(item_path,find_name) == 0) {
                *start = item_start;
                *size = item_size;
                for(int i = 0; i < strnlen(item_path,128);i ++){
                    char ch = item_path[i];
                    if(ch == '\n' || ch == '\r'){
                        continue;
                    }
                    *(full_path + i) = ch;
                }
                DLOGD("find_in_maps path = %s",item_path);
                found = 1;
                break;
            }

        }
        free(line);
    }

    return found;
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
