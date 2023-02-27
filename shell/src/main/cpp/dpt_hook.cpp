//
// Created by luoyesiqiu
//

#include <map>
#include <unordered_map>
#include <dex/CodeItem.h>
#include "dpt_hook.h"
#include "bytehook.h"

extern std::unordered_map<int, std::unordered_map<int, CodeItem*>*> dexMap;
std::map<int,uint8_t *> dexMemMap;
int g_sdkLevel = 0;

void dpt_hook() {
    bytehook_init(BYTEHOOK_MODE_AUTOMATIC,false);
    g_sdkLevel = android_get_device_api_level();
    hook_mmap();
    hook_ClassLinker_LoadMethod();
    hook_GetOatDexFile();
}

const char *GetArtLibPath() {
    if(g_sdkLevel < 29)
        return  "/system/" LIB_DIR "/libart.so" ;
    else if(g_sdkLevel == 29) {
        return "/apex/com.android.runtime/" LIB_DIR "/libart.so";
    }
    else {
        return "/apex/com.android.art/" LIB_DIR "/libart.so";
    }
}

const char *GetArtBaseLibPath() {
    if(g_sdkLevel == 29) {
        return "/apex/com.android.runtime/" LIB_DIR "/libartbase.so";
    }
    else {
        return "/apex/com.android.art/" LIB_DIR "/libartbase.so";
    }
}

const char *GetClassLinkerLoadMethodLibPath(){
    return GetArtLibPath();
}

const char *getClassLinkerLoadMethodSymbol() {
    const char *sym = find_symbol_in_elf_file(GetClassLinkerLoadMethodLibPath(),2,"ClassLinker","LoadMethod","DexFile","ArtMethod");
    return sym;
}

void callOriginLoadMethod(void *thiz, void *self, const void *dex_file, const void *it,
                          const void *method,
                          void *klass, void *dst) {
    switch (android_get_device_api_level()) {
        case 23:
        case 24:
        case 25:
            g_originLoadMethodM(thiz, self, dex_file, it, klass, dst);
            break;
        case 26:
        case 27:
        case 28:
            g_originLoadMethodO(thiz, dex_file, it, klass, dst);
            break;
        case 29:
        case 30:
        case 31:
        case 32:
        case 33:
            g_originLoadMethodQ(thiz, dex_file, method, klass, dst);
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
        case 32:
        case 33:
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
    #ifndef __LP64__
        return 4;
    #else
        return  8;
    #endif
}

void change_dex_protective(uint8_t * begin,const char* name,int dexSize,int dexIndex){
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
        case 32:
        case 33:
            return new ClassDataItemReader(method);
    }
    return nullptr;
}

void LoadMethod(void *thiz, void *self, const void *dex_file, const void *it, const void *method,
                void *klass, void *dst) {

    if (LIKELY(g_originLoadMethodM != nullptr
        || g_originLoadMethodO != nullptr
        || g_originLoadMethodQ != nullptr)) {
        uint32_t location_offset = getDexFileLocationOffset();
        uint32_t begin_offset = getDataItemCodeItemOffset();
        callOriginLoadMethod(thiz, self, dex_file, it, method, klass, dst);

        ClassDataItemReader *classDataItemReader = getClassDataItemReader(it,method);

        uint8_t **begin_ptr = (uint8_t **) ((uint8_t *) dex_file + begin_offset);
        uint8_t *begin = *begin_ptr;
        // vtable(4|8) + prev_fields_size
        std::string *location = (reinterpret_cast<std::string *>((uint8_t *) dex_file +
                                                                 location_offset));
        if (location->find(DEXES_ZIP_NAME) != std::string::npos) {
            //code_item_offset == 0说明是native方法或者抽象方法
            if (classDataItemReader->GetMethodCodeItemOffset() == 0) {
                DLOGW("native or abstract method? = %s code_item_offset = 0x%x",
                      classDataItemReader->MemberIsNative() ? "true" : "false",
                      classDataItemReader->GetMethodCodeItemOffset());
                delete classDataItemReader;
                return;
            }

            uintptr_t insnsPtr = (uintptr_t)(begin + classDataItemReader->GetMethodCodeItemOffset() + 16);

            uint16_t firstDvmCode = *((uint16_t*)insnsPtr);
            if(firstDvmCode != 0x0012 && firstDvmCode != 0x0016 && firstDvmCode != 0x000e){
                NLOG("[*] this method has code no need to patch");
                delete classDataItemReader;
                return;
            }

            NLOG("[*] LoadMethod dexfile = %s,code_off = {0x%x => %02x} begin(%p) = %c,%c,%c,%c method_idx = %d",
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

            int dexIndex = parse_dex_number(location);

            NLOG("[*] dex size = %d",dexSize);

            auto dexIt = dexMap.find(dexIndex);
            if (LIKELY(dexIt != dexMap.end())) {
                auto dexMemIt = dexMemMap.find(dexIndex);
                //没有放进去过，则放进去
                if(UNLIKELY(dexMemIt == dexMemMap.end())){
                    change_dex_protective(begin,location->c_str(),dexSize,dexIndex);
                }

                auto codeItemMap = dexIt->second;
                int methodIdx = classDataItemReader->GetMemberIndex();
                auto codeItemIt = codeItemMap->find(methodIdx);

                if (LIKELY(codeItemIt != codeItemMap->end())) {
                    CodeItem* codeItem = codeItemIt->second;
                    uint8_t  *realCodeItemPtr = (uint8_t *)(insnsPtr);

#ifdef NOICE_LOG
                    char threadName[128] = {0};
                    getThreadName(threadName);
                    NLOG("[*] LoadMethod codeItem patch ,thread = %s, methodIndex = %d,insnsSize = %d >>> %p(0x%lx)",
                         threadName,codeItem->getMethodIdx(), codeItem->getInsnsSize(), realCodeItemPtr,(realCodeItemPtr - begin)
                        );

#endif
                    memcpy(realCodeItemPtr,codeItem->getInsns(),codeItem->getInsnsSize());
                }
                else{
                    DLOGE("[*] LoadMethod cannot find methodId: %d in dex: %d(%s)",methodIdx,dexIndex,location->c_str());
                }
            }
            else{
                DLOGE("[*] LoadMethod cannot find dex: %d",dexIndex);
            }
        }

        delete classDataItemReader;
    }
}

void LoadMethodM(void *thiz, void *self, const void *dex_file, const void *it, void *klass,
                   void *dst) {
    LoadMethod(thiz, self, dex_file, it, nullptr, klass, dst);
}

void LoadMethodO(void *thiz, const void *dex_file, const void *it, void *klass, void *dst) {
    LoadMethod(thiz, nullptr, dex_file, it, nullptr, klass, dst);
}

void LoadMethodQ(void *thiz, const void *dex_file, const void *method, void *klass, void *dst) {
    LoadMethod(thiz, nullptr, dex_file, nullptr, method, klass, dst);
};

void hook_ClassLinker_LoadMethod() {
    void* loadMethodAddress = DobbySymbolResolver(GetArtLibPath(),getClassLinkerLoadMethodSymbol());
    switch (g_sdkLevel) {
        case 23:
        case 24:
        case 25:
            DobbyHook(loadMethodAddress, (void *) LoadMethodM,(void**)&g_originLoadMethodM);
            break;
        case 26:
        case 27:
        case 28:
            DobbyHook(loadMethodAddress, (void *) LoadMethodO,(void**)&g_originLoadMethodO);
            break;
        case 29:
        case 30:
        case 31:
        case 32:
        case 33:
            DobbyHook(loadMethodAddress, (void *) LoadMethodQ,(void**)&g_originLoadMethodQ);
            break;

    }
}

const char *getArtLibName() {
    switch (g_sdkLevel) {
        case 24:
        case 25:
        case 26:
        case 27:
        case 28:
            return "libart.so";
        case 29:
        case 30:
        case 31:
        case 32:
        case 33:
            return "libartbase.so";
    }
}

void* fake_mmap(void* __addr, size_t __size, int __prot, int __flags, int __fd, off_t __offset){
    BYTEHOOK_STACK_SCOPE();
    int hasRead = (__prot & PROT_READ) == PROT_READ;
    int hasWrite = (__prot & PROT_WRITE) == PROT_WRITE;
    int prot = __prot;

    if(hasRead && !hasWrite) {
        prot = prot | PROT_WRITE;
        DLOGD("fake_mmap call fd = %p,size = %d, prot = %d,flag = %d",__fd,__size, prot,__flags);
    }
    if(g_sdkLevel == 30){
        char link_path[128] = {0};
        snprintf(link_path,sizeof(link_path),"/proc/%d/fd/%d",getpid(),__fd);
        char fd_path[256] = {0};
        readlink(link_path,fd_path,sizeof(fd_path));

        DLOGD("fake_mmap link path = %s",fd_path);

        if(strstr(fd_path,"base.vdex") ){
            DLOGE("fake_mmap want to mmap base.vdex");
            __flags = 0;
        }
    }

    void *addr = BYTEHOOK_CALL_PREV(fake_mmap,__addr,  __size, prot,  __flags,  __fd,  __offset);
    return addr;
}

void hook_mmap(){
    bytehook_stub_t stub = bytehook_hook_single(
            getArtLibName(),
            "libc.so",
            "mmap",
            (void*)fake_mmap,
            nullptr,
            nullptr);
    if(stub != nullptr){
        DLOGD("mmap hook success!");
    }
}

void *fake_GetOatDexFile(const char* dex_location,
              const uint32_t* dex_location_checksum,
              std::string* error_msg){
    DLOGD("fake_GetOatDexFile call!");

    return nullptr;
}

void hook_GetOatDexFile(){
    const char *getOatDexFileSymbol = find_symbol_in_elf_file(GetArtLibPath(),2,"OatFile","GetOatDexFile");
    DLOGD("getOatDexFile symbol = %s",getOatDexFileSymbol);
    void *sym = DobbySymbolResolver(GetArtLibPath(),getOatDexFileSymbol);
    if(sym != nullptr){
        switch (g_sdkLevel) {
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
                DobbyHook(sym,(void *)fake_GetOatDexFile,(void **)&g_GetOatDexFile);
                break;
        }
    }
}