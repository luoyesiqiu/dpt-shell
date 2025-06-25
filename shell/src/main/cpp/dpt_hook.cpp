//
// Created by luoyesiqiu
//

#include <map>
#include <unordered_map>
#include <sys/system_properties.h>
#include "dex/CodeItem.h"
#include "common/dpt_string.h"
#include "dpt_hook.h"
#include "dpt_risk.h"
#include "bytehook.h"

using namespace dpt;

extern std::unordered_map<int, std::unordered_map<int, data::CodeItem*>*> dexMap;
std::map<int,uint8_t *> dexMemMap;
int g_sdkLevel = 0;

void dpt_hook() {
    bytehook_init(BYTEHOOK_MODE_AUTOMATIC,false);
    g_sdkLevel = android_get_device_api_level();
    hook_execve();
    hook_mmap();
    bool hookSuccess = hook_DefineClass();
    if(!hookSuccess) {
        hook_LoadClass();
    }
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

void change_dex_protective(uint8_t * begin,int dexSize,int dexIndex){
    uintptr_t start = PAGE_START((uintptr_t) (begin));
    uint32_t pageSize = sysconf(_SC_PAGE_SIZE);
    size_t n = (dexSize / pageSize) + (dexSize % pageSize != 0);

    for(int i = 0;i < 10;) {
        DLOGD("mprotect dex[%d] start = " FMT_POINTER ",end = " FMT_POINTER ", page_size = %d, dexSize = %d, block_cnt = %zu",
              dexIndex,
              start,
              start + pageSize * n,
              pageSize,
              dexSize,
              n);
        int ret = mprotect((void *) (start), pageSize * n,
                           PROT_READ | PROT_WRITE);

        if (ret != 0) {
            DLOGE("mprotect fail, address: %p, reason: %d!", begin, ret);
            i++;
        } else {
            dexMemMap.insert(std::pair<int,uint8_t *>(dexIndex,begin));
            DLOGD("mprotect success, address: %p.", begin);
            break;
        }
    }
}

DPT_ENCRYPT void patchMethod(uint8_t *begin,
                             __unused const char *location,
                             uint32_t dexSize,
                             int dexIndex,
                             uint32_t methodIdx,
                             uint32_t codeOff) {

    auto dexIt = dexMap.find(dexIndex);
    if (LIKELY(dexIt != dexMap.end())) {
        auto dexMemIt = dexMemMap.find(dexIndex);
        if(UNLIKELY(dexMemIt == dexMemMap.end())){
            change_dex_protective(begin, dexSize, dexIndex);
        }

        auto codeItemMap = dexIt->second;
        auto codeItemIt = codeItemMap->find(methodIdx);

        if (LIKELY(codeItemIt != codeItemMap->end())) {
            data::CodeItem* codeItem = codeItemIt->second;
            if(codeOff == 0) {
                NLOG("dex: %d methodIndex: %d no need patch!",dexIndex,methodIdx);
                return;
            }

            auto *dexCodeItem = (dex::CodeItem *)(begin + codeOff);

            auto *realInsnsPtr = (uint8_t *)(dexCodeItem->insns_);

            NLOG("codeItem patch, methodIndex = %d, insnsSize = %d >>> %p(0x%x)",
                 codeItem->getMethodIdx(),
                 codeItem->getInsnsSize(),
                 realInsnsPtr,
                 (unsigned int)(realInsnsPtr - begin));

            memcpy(realInsnsPtr,codeItem->getInsns(),codeItem->getInsnsSize());
        }
        else{
            NLOG("cannot find  methodId: %d in codeitem map, dex index: %d(%s)", methodIdx, dexIndex, location);
        }
    }
    else{
        DLOGW("cannot find dex: '%s' in dex map", location);
    }
}

DPT_ENCRYPT void patchClass(__unused const char* descriptor,
                 const void* dex_file,
                 const void* dex_class_def) {

    if(descriptor != nullptr && UNLIKELY(dpt_strstr(descriptor, JUNK_CLASS_FULL_NAME) != nullptr)) {
        size_t descriptorLength = dpt_strlen(descriptor);
        char ch = descriptor[descriptorLength - 2];
        DLOGD("Attempt patch junk class %s ,char is '%c'",descriptor,ch);
        if(isdigit(ch)) {
            DLOGE("Find illegal call, desc: %s!", descriptor);
            dpt_crash();
            return;
        }

    }

    if(LIKELY(dex_file != nullptr)){
        std::string location;
        uint8_t *begin = nullptr;
        uint64_t dexSize = 0;
        if(g_sdkLevel >= 35) {
            auto* dexFileV35 = (V35::DexFile *)dex_file;
            location = dexFileV35->location_;
            begin = (uint8_t *)dexFileV35->begin_;
            dexSize = dexFileV35->header_->file_size_;
        }
        else if(g_sdkLevel >= __ANDROID_API_P__){
            auto* dexFileV28 = (V28::DexFile *)dex_file;
            location = dexFileV28->location_;
            begin = (uint8_t *)dexFileV28->begin_;
            dexSize = dexFileV28->size_ == 0 ? dexFileV28->header_->file_size_ : dexFileV28->size_;
        }
        else {
            auto* dexFileV21 = (V21::DexFile *)dex_file;
            location = dexFileV21->location_;
            begin = (uint8_t *)dexFileV21->begin_;
            dexSize = dexFileV21->size_ == 0 ? dexFileV21->header_->file_size_ : dexFileV21->size_;
        }

        if(location.rfind(DEXES_ZIP_NAME) != std::string::npos && dex_class_def){
            int dexIndex = parse_dex_number(&location);

            auto* class_def = (dex::ClassDef *)dex_class_def;
            NLOG("class_desc = '%s', class_idx_ = 0x%x, class data off = 0x%x",descriptor,class_def->class_idx_,class_def->class_data_off_);

            if(LIKELY(class_def->class_data_off_ != 0)) {
                size_t read = 0;
                auto *class_data = (uint8_t *) ((uint8_t *) begin + class_def->class_data_off_);

                uint64_t static_fields_size = 0;
                read += DexFileUtils::readUleb128(class_data, &static_fields_size);

                uint64_t instance_fields_size = 0;
                read += DexFileUtils::readUleb128(class_data + read, &instance_fields_size);

                uint64_t direct_methods_size = 0;
                read += DexFileUtils::readUleb128(class_data + read, &direct_methods_size);

                uint64_t virtual_methods_size = 0;
                read += DexFileUtils::readUleb128(class_data + read, &virtual_methods_size);

                // staticFields
                read += DexFileUtils::getFieldsSize(class_data + read, static_fields_size);

                // instanceFields
                read += DexFileUtils::getFieldsSize(class_data + read, instance_fields_size);

                auto *directMethods = new dex::ClassDataMethod[direct_methods_size];
                read += DexFileUtils::readMethods(class_data + read, directMethods,
                                                  direct_methods_size);

                auto *virtualMethods = new dex::ClassDataMethod[virtual_methods_size];
                read += DexFileUtils::readMethods(class_data + read, virtualMethods,
                                                  virtual_methods_size);

                for (uint64_t i = 0; i < direct_methods_size; i++) {
                    auto method = directMethods[i];
                    patchMethod(begin, location.c_str(), dexSize, dexIndex,
                                method.method_idx_delta_, method.code_off_);
                }

                for (uint64_t i = 0; i < virtual_methods_size; i++) {
                    auto method = virtualMethods[i];
                    patchMethod(begin, location.c_str(), dexSize, dexIndex,
                                method.method_idx_delta_, method.code_off_);
                }

                delete[] directMethods;
                delete[] virtualMethods;
            }
            else {
                NLOG("class_def->class_data_off_ is zero");
            }
        }
    }
}

DPT_ENCRYPT void LoadClassV23(void* thiz,
                               const void* self,
                               const void* dex_file,
                               const void* dex_class_def,
                               const char* klass) {
    if(LIKELY(g_originLoadClassV23 != nullptr)) {
        patchClass(nullptr,dex_file,dex_class_def);
        g_originLoadClassV23(thiz, self, dex_file, dex_class_def, klass);
    }
}

DPT_ENCRYPT bool hook_LoadClass() {
    if(g_sdkLevel < __ANDROID_API_M__) {
        return false;
    }

    void* loadClassAddress = nullptr;

    char sym[256] = {0};
    find_symbol_in_elf_file(GetClassLinkerDefineClassLibPath(), sym, ARRAY_LENGTH(sym), 2, "ClassLinker", "LoadClass");

    loadClassAddress = DobbySymbolResolver(GetArtLibPath(), sym);

    int hookResult = DobbyHook(loadClassAddress, (dobby_dummy_func_t) LoadClassV23, (dobby_dummy_func_t*) &g_originLoadClassV23);

    DLOGD("hook result: %d", hookResult);
    return hookResult == 0;
}

DPT_ENCRYPT void *DefineClassV22(void* thiz,void* self,
                 const char* descriptor,
                 size_t hash,
                 void* class_loader,
                 const void* dex_file,
                 const void* dex_class_def) {

    if(LIKELY(g_originDefineClassV22 != nullptr)) {

        patchClass(descriptor,dex_file,dex_class_def);

        return g_originDefineClassV22( thiz,self,descriptor,hash,class_loader, dex_file, dex_class_def);

    }
    return nullptr;
}

DPT_ENCRYPT void *DefineClassV21(void* thiz,
                     const char* descriptor,
                     void* class_loader,
                     const void* dex_file,
                     const void* dex_class_def) {

    if(LIKELY(g_originDefineClassV21 != nullptr)) {
        patchClass(descriptor,dex_file,dex_class_def);
        return g_originDefineClassV21( thiz,descriptor,class_loader, dex_file, dex_class_def);

    }
    return nullptr;
}

DPT_ENCRYPT bool hook_DefineClass() {
    char sym[256] = {0};
    find_symbol_in_elf_file(GetClassLinkerDefineClassLibPath(), sym, ARRAY_LENGTH(sym), 2, "ClassLinker", "DefineClass");

    if(strlen(sym) == 0) {
        DLOGW("cannot find symbol: DefineClass");
        return false;
    }

    void* defineClassAddress = DobbySymbolResolver(GetClassLinkerDefineClassLibPath(), sym);

    if(defineClassAddress == nullptr) {
        DLOGE("defineClass address is null, sym: %s", sym);
        return false;
    }

    int hookResult;
    if(g_sdkLevel >= __ANDROID_API_L_MR1__) {
        hookResult = DobbyHook(defineClassAddress, (dobby_dummy_func_t) DefineClassV22, (dobby_dummy_func_t *) &g_originDefineClassV22);
    }
    else {
        hookResult = DobbyHook(defineClassAddress, (dobby_dummy_func_t) DefineClassV21, (dobby_dummy_func_t *) &g_originDefineClassV21);
    }

    if(hookResult == 0) {
        DLOGD("hook success.");
        return true;
    }
    else {
        DLOGE("hook fail!");
        return false;
    }
}

const char *getArtLibName() {
    if (g_sdkLevel >= 29) {
        return "libartbase.so";
    }
    return "libart.so";
}

DPT_ENCRYPT void* fake_mmap(void* __addr, size_t __size, int __prot, int __flags, int __fd, off_t __offset){
    BYTEHOOK_STACK_SCOPE();

    int prot = __prot;
    int hasRead = (__prot & PROT_READ) == PROT_READ;
    int hasWrite = (__prot & PROT_WRITE) == PROT_WRITE;

    char fd_path[256] = {0};
    dpt_readlink(__fd,fd_path, ARRAY_LENGTH(fd_path));

    if(strstr(fd_path,"webview.vdex") != nullptr) {
        DLOGW("link path: %s, no need to change prot",fd_path);
        goto tail;
    }

    if(hasRead && !hasWrite) {
        prot = prot | PROT_WRITE;
        DLOGD("append write flag fd = %d, size = %zu, prot = %d, flag = %d",__fd,__size, prot,__flags);
    }

    if(g_sdkLevel == 30){
        if(strstr(fd_path,"base.vdex") != nullptr){
            DLOGE("want to mmap base.vdex");
            __flags = 0;
        }
    }
    tail:
    void *addr = BYTEHOOK_CALL_PREV(fake_mmap,__addr,  __size, prot,  __flags,  __fd,  __offset);
    return addr;
}

DPT_ENCRYPT void hook_mmap(){
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
    else {
        DLOGE("mmap hook fail!");
    }
}

DPT_ENCRYPT int fake_execve(const char *pathname, char *const argv[], char *const envp[]) {
    BYTEHOOK_STACK_SCOPE();
    DLOGD("execve hooked: %s", pathname);
    if (strstr(pathname, "dex2oat") != nullptr) {
        DLOGD("execve blocked: %s", pathname);
        errno = EACCES;
        return -1;
    }
    return BYTEHOOK_CALL_PREV(fake_execve, pathname, argv, envp);
}

DPT_ENCRYPT void hook_execve(){
    bytehook_stub_t stub = bytehook_hook_single(
            getArtLibName(),
            "libc.so",
            "execve",
            (void *) fake_execve,
            nullptr,
            nullptr);
    if (stub != nullptr) {
        DLOGD("execve hook success!");
    }
    else {
        DLOGE("execve hook fail!");
    }
}
