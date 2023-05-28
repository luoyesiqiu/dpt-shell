//
// Created by luoyesiqiu
//

#include <map>
#include <unordered_map>
#include <dex/CodeItem.h>
#include "dpt_hook.h"
#include "bytehook.h"
using namespace dpt;

extern std::unordered_map<int, std::unordered_map<int, data::CodeItem*>*> dexMap;
std::map<int,uint8_t *> dexMemMap;
int g_sdkLevel = 0;

void dpt_hook() {
    bytehook_init(BYTEHOOK_MODE_AUTOMATIC,false);
    g_sdkLevel = android_get_device_api_level();
    hook_mmap();
    hook_GetOatDexFile();
    hook_DefineClass();
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

const char *GetClassLinkerDefineClassLibPath(){
    return GetArtLibPath();
}

const char *getClassLinkerDefineClassSymbol() {
    const char *sym = find_symbol_in_elf_file(GetClassLinkerDefineClassLibPath(),2,"ClassLinker","DefineClass");
    return sym;
}

void change_dex_protective(uint8_t * begin,int dexSize,int dexIndex){
    uintptr_t start = PAGE_START((uintptr_t) (begin));
    uint32_t block = sysconf(_SC_PAGE_SIZE);
    size_t n = (dexSize / block) + (dexSize % block != 0);

    for(int i = 0;i < 10;) {
        DLOGD("mprotect dex[%d] start = " FMT_POINTER ",end = " FMT_POINTER,dexIndex, start, start + block * n);
        int ret = mprotect((void *) (start), block * n,
                           PROT_READ | PROT_WRITE);

        if (ret != 0) {
            i++;
        } else {
            dexMemMap.insert(std::pair<int,uint8_t *>(dexIndex,begin));
            break;
        }
    }
}

void patchMethod(uint8_t *begin,const char * __unused,uint32_t dexSize,int dexIndex,uint32_t methodIdx,uint32_t codeOff){
    if(codeOff == 0){
        DLOGI("[*] patchMethod dex: %d methodIndex: %d no need patch!",dexIndex,methodIdx);
        return;
    }
    auto *dexCodeItem = (dex::CodeItem *) (begin + codeOff);

    uint16_t firstDvmCode = *((uint16_t*)dexCodeItem->insns_);
    if(firstDvmCode != 0x0012 && firstDvmCode != 0x0016 && firstDvmCode != 0x000e){
        NLOG("[*] this method has code no need to patch");
        return;
    }

    auto dexIt = dexMap.find(dexIndex);
    if (LIKELY(dexIt != dexMap.end())) {
        auto dexMemIt = dexMemMap.find(dexIndex);
        //没有放进去过，则放进去
        if(UNLIKELY(dexMemIt == dexMemMap.end())){
            change_dex_protective(begin,dexSize,dexIndex);
        }

        auto codeItemMap = dexIt->second;
        auto codeItemIt = codeItemMap->find(methodIdx);

        if (LIKELY(codeItemIt != codeItemMap->end())) {
            data::CodeItem* codeItem = codeItemIt->second;
            auto *realCodeItemPtr = (uint8_t *)(dexCodeItem->insns_);

#ifdef NOICE_LOG
            NLOG("[*] patchMethod codeItem patch ,thread = %s, methodIndex = %d,insnsSize = %d >>> %p(0x%lx)",
                 getThreadName(),codeItem->getMethodIdx(), codeItem->getInsnsSize(), realCodeItemPtr,(realCodeItemPtr - begin)
                );

#endif
            memcpy(realCodeItemPtr,codeItem->getInsns(),codeItem->getInsnsSize());
        }
        else{
            NLOG("[*] patchMethod cannot find  methodId: %d in codeitem map, dex index: %d(%s)",methodIdx,dexIndex,location);
        }
    }
    else{
        DLOGE("[*] patchMethod cannot find dex: %d in dex map",dexIndex);
    }
}

void* DefineClass(void* thiz,void* self,
                 const char* descriptor,
                 size_t hash,
                 void* class_loader,
                 const void* dex_file,
                 const void* dex_class_def) {

    if(LIKELY(g_originDefineClass != nullptr)){

        if(LIKELY(dex_file != nullptr)){
            std::string location;
            uint8_t *begin = nullptr;
            uint64_t dexSize = 0;
            if(g_sdkLevel >= 28){
                auto* dexFileV28 = (V28::DexFile *)dex_file;
                location = dexFileV28->location_;
                begin = (uint8_t *)dexFileV28->begin_;
                dexSize = dexFileV28->size_;
            }
            else{
                auto* dexFileV23 = (V23::DexFile *)dex_file;
                location = dexFileV23->location_;
                begin = (uint8_t *)dexFileV23->begin_;
                dexSize = dexFileV23->size_;
            }

            if(location.find(DEXES_ZIP_NAME) != std::string::npos){
                int dexIndex = parse_dex_number(&location);

                NLOG("DefineClass location: %s", location.c_str());
                if(dex_class_def){
                    auto* class_def = (dex::ClassDef *)dex_class_def;
                    NLOG("[+] DefineClass class_idx_ = 0x%x,class data off = 0x%x",class_def->class_idx_,class_def->class_data_off_);

                    size_t read = 0;
                    auto *class_data = (uint8_t *)((uint8_t *)begin + class_def->class_data_off_);

                    uint64_t static_fields_size = 0;
                    read += DexFileUtils::readUleb128(class_data, &static_fields_size);
                    NLOG("[-] DefineClass static_fields_size = %lu,read = %zu",static_fields_size,read);

                    uint64_t instance_fields_size = 0;
                    read += DexFileUtils::readUleb128(class_data + read, &instance_fields_size);
                    NLOG("[-] DefineClass instance_fields_size = %lu,read = %zu",instance_fields_size,read);

                    uint64_t direct_methods_size = 0;
                    read += DexFileUtils::readUleb128(class_data + read, &direct_methods_size);
                    NLOG("[-] DefineClass direct_methods_size = %lu,read = %zu",direct_methods_size,read);

                    uint64_t virtual_methods_size = 0;
                    read += DexFileUtils::readUleb128(class_data + read, &virtual_methods_size);
                    NLOG("[-] DefineClass virtual_methods_size = %lu,read = %zu",virtual_methods_size,read);

                    dex::ClassDataField staticFields[static_fields_size];
                    read += DexFileUtils::readFields(class_data + read,staticFields,static_fields_size);

                    dex::ClassDataField instanceFields[instance_fields_size];
                    read += DexFileUtils::readFields(class_data + read,instanceFields,instance_fields_size);

                    dex::ClassDataMethod directMethods[direct_methods_size];
                    read += DexFileUtils::readMethods(class_data + read,directMethods,direct_methods_size);

                    dex::ClassDataMethod virtualMethods[virtual_methods_size];
                    read += DexFileUtils::readMethods(class_data + read,virtualMethods,virtual_methods_size);

                    for(uint64_t i = 0;i < direct_methods_size;i++){
                        auto method = directMethods[i];
                        NLOG("[-] DefineClass directMethods[%d] methodIndex = %d,code_off = 0x%x",i,method.method_idx_delta_,method.code_off_);
                        patchMethod(begin, location.c_str(), dexSize, dexIndex, method.method_idx_delta_,method.code_off_);
                    }

                    for(uint64_t i = 0;i < virtual_methods_size;i++){
                        auto method = virtualMethods[i];
                        NLOG("[-] DefineClass virtualMethods[%d] methodIndex = %d,code_off = 0x%x",i,method.method_idx_delta_,method.code_off_);
                        patchMethod(begin, location.c_str(), dexSize, dexIndex, method.method_idx_delta_,method.code_off_);
                    }

                }
            }
        }
        return g_originDefineClass( thiz,self,descriptor,hash,class_loader, dex_file, dex_class_def);

    }

    return nullptr;
}

void hook_DefineClass(){
    void* defineClassAddress = DobbySymbolResolver(GetClassLinkerDefineClassLibPath(),getClassLinkerDefineClassSymbol());

    DobbyHook(defineClassAddress, (void *) DefineClass,(void**)&g_originDefineClass);
}

const char *getArtLibName() {
    switch (g_sdkLevel) {
        case 29:
        case 30:
        case 31:
        case 32:
        case 33:
            return "libartbase.so";
    }
    return "libart.so";
}

void* fake_mmap(void* __addr, size_t __size, int __prot, int __flags, int __fd, off_t __offset){
    BYTEHOOK_STACK_SCOPE();
    int hasRead = (__prot & PROT_READ) == PROT_READ;
    int hasWrite = (__prot & PROT_WRITE) == PROT_WRITE;
    int prot = __prot;

    if(hasRead && !hasWrite) {
        prot = prot | PROT_WRITE;
        DLOGD("fake_mmap call fd = %d,size = %zu, prot = %d,flag = %d",__fd,__size, prot,__flags);
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

void *fake_GetOatDexFile(const char* __unused,
              const uint32_t* __unused,
              std::string* __unused){
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