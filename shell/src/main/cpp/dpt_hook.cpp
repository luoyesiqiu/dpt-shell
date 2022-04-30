//
// Created by luoyesiqiu
//

#include <map>
#include <unordered_map>
#include <dex/CodeItem.h>
#include "dpt_hook.h"

extern std::unordered_map<int, std::unordered_map<int, CodeItem*>*> dexMap;
std::map<int,uint8_t *> dexMemMap;
int g_sdkLevel = 0;

void dpt_hook() {
    g_sdkLevel = android_get_device_api_level();

    hookMapFileAtAddress();
    hook_ClassLinker_LoadMethod();
}

const char *GetClassLinker_LoadMethod_Sym() {
    switch (g_sdkLevel) {
        case 24:
        case 25:
            return "_ZN3art11ClassLinker10LoadMethodEPNS_6ThreadERKNS_7DexFileERKNS_21ClassDataItemIteratorENS_6HandleINS_6mirror5ClassEEEPNS_9ArtMethodE";
        case 27:
        case 28:
            return "_ZN3art11ClassLinker10LoadMethodERKNS_7DexFileERKNS_21ClassDataItemIteratorENS_6HandleINS_6mirror5ClassEEEPNS_9ArtMethodE";
        case 29:
        case 30:
        case 31:
            return "_ZN3art11ClassLinker10LoadMethodERKNS_7DexFileERKNS_13ClassAccessor6MethodENS_6HandleINS_6mirror5ClassEEEPNS_9ArtMethodE";
        default:
            return "";
    }
}

const char *GetArtLibPath() {
    if(g_sdkLevel < 29)
        return  "/system/" LIB_DIR "/libart.so" ;
    else if(g_sdkLevel == 29) {
        return "/apex/com.android.runtime/" LIB_DIR "/libart.so";
    }
    else if(g_sdkLevel == 30){
        return "/apex/com.android.art/" LIB_DIR "/libart.so";
    }
    else if(g_sdkLevel == 31){
        return "/apex/com.android.art/" LIB_DIR "/libart.so";
    }
}

const char *GetArtBaseLibPath() {
    if(g_sdkLevel == 29) {
        return "/apex/com.android.runtime/" LIB_DIR "/libartbase.so";
    }
    else if(g_sdkLevel == 30) {
        return "/apex/com.android.art/" LIB_DIR "/libartbase.so";
    }
    else if(g_sdkLevel == 31) {
        return "/apex/com.android.art/" LIB_DIR "/libartbase.so";
    }
}

void callOriginLoadMethod(void *thiz, void *self, const void *dex_file, const void *it,
                          const void *method,
                          void *klass, void *dst) {
    switch (android_get_device_api_level()) {
        case 23:
        case 24:
        case 25:
            g_originLoadMethod25(thiz, self, dex_file, it, klass, dst);
            break;
        case 26:
        case 27:
        case 28:
            g_originLoadMethod28(thiz, dex_file, it, klass, dst);
            break;
        case 29:
        case 30:
        case 31:
            g_originLoadMethod29(thiz, dex_file, method, klass, dst);
            break;
    }
}

uint32_t getDexFileLocationOffset() {
    uint32_t location_offset = 0;
    switch (g_sdkLevel) {
        case 23:
        case 24:
        case 25:
#ifndef __LP64__
            location_offset = 12;
#else
            location_offset = 24;
#endif
            break;
        case 26:
        case 27:
#ifndef __LP64__
            location_offset = 12;
#else
            location_offset = 24;
#endif
            break;
        case 28:
#ifndef __LP64__
            location_offset = 20;
#else
            location_offset = 40;
#endif
            break;
        case 29:
        case 30:
        case 31:
#ifndef __LP64__
            location_offset = 20;
#else
            location_offset = 40;
#endif
            break;
    }

    return location_offset;
}

uint32_t getDataItemCodeItemOffset() {
    uint32_t begin_offset = 0;
    switch (g_sdkLevel) {
        case 23:
        case 24:
        case 25:
        case 26:
        case 27:
        case 28:
        case 29:
        case 30:
        case 31:
#ifndef __LP64__
            begin_offset = 4;
#else
            begin_offset = 8;
#endif
            break;
    }

    return begin_offset;
}

int dexNumber(std::string *location){
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
    return dexIndex;
}

void changeDexProtect(uint8_t * begin,const char* name,int dexSize,int dexIndex){
    uintptr_t start = PAGE_START((uintptr_t) (begin));
    uint32_t block = sysconf(_SC_PAGE_SIZE);
    int n = (dexSize / block) + (dexSize % block != 0);

    for(int i = 0;i < 10;) {
        DLOGD("mprotect start = 0x%x,end = 0x%x", start, start + block * n);
        int ret = mprotect((void *) (start), block * n,
                           PROT_READ | PROT_WRITE);

        if (ret != 0) {
            DLOGE("mprotect fail,code = %d,%s", ret, name);
            i++;
        } else {
            dexMemMap.insert(std::pair<int,uint8_t *>(dexIndex,begin));
            DLOGD("mprotect ok,%s", name);
            break;
        }
    }
}

ClassDataItemReader* getClassDataItemReader(const void* it,const void* method){
    switch (g_sdkLevel) {
        case 24:
        case 25:
        case 26:
        case 27:
        case 28:
            return new ClassDataItemReader(it);
        case 29:
        case 30:
        case 31:
            return new ClassDataItemReader(method);
    }
    return nullptr;

}

void LoadMethod(void *thiz, void *self, const void *dex_file, const void *it, const void *method,
                void *klass, void *dst) {

    if (g_originLoadMethod25 != nullptr
        || g_originLoadMethod28 != nullptr
        || g_originLoadMethod29 != nullptr) {
        uint32_t location_offset = getDexFileLocationOffset();
        uint32_t begin_offset = getDataItemCodeItemOffset();
        callOriginLoadMethod(thiz, self, dex_file, it, method, klass, dst);

        ClassDataItemReader *classDataItemReader = getClassDataItemReader(it,method);


        uint8_t **begin_ptr = (uint8_t **) ((uint8_t *) dex_file + begin_offset);
        uint8_t *begin = *begin_ptr;
        // vtable(4|8) + prev_fields_size
        std::string *location = (reinterpret_cast<std::string *>((uint8_t *) dex_file +
                                                                 location_offset));
        if (location->find("base.apk") != std::string::npos) {

            //code_item_offset == 0说明是native方法或者没有代码
            if (classDataItemReader->GetMethodCodeItemOffset() == 0) {
                DLOGW("native method? = %s code_item_offset = 0x%x",
                      classDataItemReader->MemberIsNative() ? "true" : "false",
                      classDataItemReader->GetMethodCodeItemOffset());
                return;
            }

            uint16_t firstDvmCode = *((uint16_t*)(begin + classDataItemReader->GetMethodCodeItemOffset() + 16));
            if(firstDvmCode != 0x0012 && firstDvmCode != 0x0016 && firstDvmCode != 0x000e){
                NLOG("this method has code no need to patch");
                return;
            }


            NLOG("LoadMethod dexfile = %s,code_off = {0x%x => %02x} begin(%p) = %c,%c,%c,%c method_idx = %d",
                  location->c_str(),
                  classDataItemReader->GetMethodCodeItemOffset(),
                  *(begin + classDataItemReader->GetMethodCodeItemOffset() + 16),
                  begin,
                  *(begin + 0),
                  *(begin + 1),
                  *(begin + 2),
                  *(begin + 3),
                  classDataItemReader->GetMemberIndex()
            );
            uint32_t dexSize = *((uint32_t*)(begin + 0x20));

            int dexIndex = dexNumber(location);

            NLOG("dex size = %d",dexSize);


            auto dexIt = dexMap.find(dexIndex - 1);
            if (dexIt != dexMap.end()) {

                auto dexMemIt = dexMemMap.find(dexIndex);
                //没有放进去过，则放进去
                if(dexMemIt == dexMemMap.end()){
                    changeDexProtect(begin,location->c_str(),dexSize,dexIndex);
                }

                auto codeItemMap = dexIt->second;
                int methodIdx = classDataItemReader->GetMemberIndex();
                auto codeItemIt = codeItemMap->find(methodIdx);

                if (codeItemIt != codeItemMap->end()) {
                    NLOG("--> codeitem find! codeItemMap size = %d,codeItemIt = %p,codeItemMapEnd = %p,methodIdx = %d",
                         codeItemMap->size(), codeItemIt, codeItemMap->end(), methodIdx);
                    CodeItem* codeItem = codeItemIt->second;
                    uint8_t  *realCodeItemPtr = (uint8_t*)(begin +
                                                classDataItemReader->GetMethodCodeItemOffset() +
                                                16);

                    NLOG("--> codeItem patch ,tid = %u, methodIndex = %d,insnsSize = %d >>> %p",gettid(),
                              codeItem->getMethodIdx(), codeItem->getInsnsSize(), realCodeItemPtr
                        );

                    memcpy(realCodeItemPtr,codeItem->getInsns(),codeItem->getInsnsSize());
                }
            }
        }

        delete classDataItemReader;
    }
}

void LoadMethod_MN(void *thiz, void *self, const void *dex_file, const void *it, void *klass,
                   void *dst) {
    LoadMethod(thiz, self, dex_file, it, nullptr, klass, dst);
}

void LoadMethod_OP(void *thiz, const void *dex_file, const void *it, void *klass, void *dst) {
    LoadMethod(thiz, nullptr, dex_file, it, nullptr, klass, dst);
}

void LoadMethod_QR(void *thiz, const void *dex_file, const void *method, void *klass, void *dst) {
    LoadMethod(thiz, nullptr, dex_file, nullptr, method, klass, dst);
};

void hook_ClassLinker_LoadMethod() {
    void* loadMethodAddress = DobbySymbolResolver(GetArtLibPath(),GetClassLinker_LoadMethod_Sym());
    switch (g_sdkLevel) {
        case 23:
        case 24:
        case 25:
            DobbyHook(loadMethodAddress, (void *) LoadMethod_MN,(void**)&g_originLoadMethod25);
            break;
        case 26:
        case 27:
        case 28:
            DobbyHook(loadMethodAddress, (void *) LoadMethod_OP,(void**)&g_originLoadMethod28);
            break;
        case 29:
        case 30:
        case 31:
            DobbyHook(loadMethodAddress, (void *) LoadMethod_QR,(void**)&g_originLoadMethod29);
            break;

    }
}

const char *getMapFileAtAddressLibPath() {
    switch (g_sdkLevel) {
        case 24:
        case 25:
        case 26:
        case 27:
        case 28:
            return GetArtLibPath();
        case 29:
        case 30:
        case 31:
            return GetArtBaseLibPath();
    }
}

const char* getMapFileAtAddressSymbol(){
    pointer_t start,size;
    char full_path[128] = {0};
    int found = find_in_maps(getMapFileAtAddressLibPath(),&start,&size,full_path);
    if(found){
        FILE *lib_fp = fopen(full_path,"r");
        if(lib_fp){
            struct stat st;
            stat(full_path,&st);
            off_t lib_size = st.st_size;
            char *data = (char *)calloc(lib_size,1);
            fread(data,1,lib_size,lib_fp);
            const char * symbol = find_symbol_in_elf((void*)data,2,"MemMap","MapFileAtAddress");
            if(symbol != nullptr) {
                DLOGD("getMapFileAtAddressSymbol find symbol = %s", symbol);
                fclose(lib_fp);
                return symbol;
            }
            else{
                DLOGE("getMapFileAtAddressSymbol no found symbol!");
            }

            free(data);
        }
    }

    return "";
}

void* MapFileAtAddress28(uint8_t* expected_ptr,
              size_t byte_count,
              int prot,
              int flags,
              int fd,
              off_t start,
              bool low_4gb,
              bool reuse,
              const char* filename,
              std::string* error_msg){
    int new_prot = (prot | PROT_WRITE);
    if(filename == nullptr) {
        DLOGD("MemMap::MapFileAtAddress call,start = %p,new_prot = %d", start,new_prot);
    }
    else {
        DLOGD("MemMap::MapFileAtAddress call,start = %p,new_prot = %d,filename = %s", start,new_prot,filename);
    }
    if(nullptr != g_originMapFileAtAddress28) {
        return g_originMapFileAtAddress28(expected_ptr,byte_count,new_prot,flags,fd,start,low_4gb,reuse,filename,error_msg);
    }
}

void* MapFileAtAddress29(void* thiz,uint8_t* addr,
                         size_t byte_count,
                         int prot,
                         int flags,
                         int fd,
                         off_t start,
                         bool low_4gb,
                         const char* filename,
                         bool reuse,
                         void* reservation,
                         std::string* error_msg){
    int new_prot = (prot | PROT_WRITE);
    if(filename == nullptr) {
        DLOGD("MemMap::MapFileAtAddress call,prot = %d,new_prot = %d", prot,new_prot);
    }
    else {
        DLOGD("MemMap::MapFileAtAddress call,prot = %d,new_prot = %d,filename = %s", prot,new_prot,filename);
    }
    if(nullptr != g_originMapFileAtAddress29) {
        return g_originMapFileAtAddress29(thiz,addr,byte_count,new_prot,flags,fd,start,low_4gb,filename,reuse,reservation,error_msg);
    }
}

void* MapFileAtAddress30(uint8_t* expected_ptr,
                         size_t byte_count,
                         int prot,
                         int flags,
                         int fd,
                         off_t start,
                         bool low_4gb,
                         const char* filename,
                         bool reuse,
                         void* reservation,
                         std::string* error_msg){
    int new_prot = (prot | PROT_WRITE);

    if(filename == nullptr) {
        DLOGD("MemMap::MapFileAtAddress call,prot = %d,new_prot = %d", prot,new_prot);
    }
    else {
        DLOGD("MemMap::MapFileAtAddress call,prot = %d,new_prot = %d,filename = %s", prot,new_prot,filename);
    }

    if(endWith(filename,"base.vdex") == 0 || endWith(filename,"base.odex") == 0){
        return nullptr;
    }

    if(nullptr != g_originMapFileAtAddress30) {
        return g_originMapFileAtAddress30(expected_ptr,byte_count,new_prot,flags,fd,start,low_4gb,filename,reuse,reservation,error_msg);
    }
    return nullptr;
}

void hookMapFileAtAddress(){

    switch (g_sdkLevel) {
        case 24:
        case 25:
        case 26:
        case 27:
        case 28: {
            void* MapFileAtAddressAddr = DobbySymbolResolver(GetArtLibPath(),getMapFileAtAddressSymbol());

            DobbyHook(MapFileAtAddressAddr, (void *) MapFileAtAddress28,
                      (void **) &g_originMapFileAtAddress28);
        }
            break;
        case 29: {
            void *MapFileAtAddressAddr = DobbySymbolResolver(GetArtBaseLibPath(),
                                                             getMapFileAtAddressSymbol());

            DobbyHook(MapFileAtAddressAddr, (void *) MapFileAtAddress29,
                      (void **) &g_originMapFileAtAddress29);
        }
            break;
        case 30: {
            void *MapFileAtAddressAddr = DobbySymbolResolver(GetArtBaseLibPath(),
                                                             getMapFileAtAddressSymbol());

            DobbyHook(MapFileAtAddressAddr, (void *) MapFileAtAddress30,
                      (void **) &g_originMapFileAtAddress30);
        }
            break;


    }
}