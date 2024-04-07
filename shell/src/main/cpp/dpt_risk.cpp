//
// Created by luoyesiqiu
//

#include "dpt_risk.h"

inline void crash() {
    Dl_info info;
    dladdr((const void *)junkCodeDexProtect, &info);
    void (*func)() = (void (*)())info.dli_fbase;
    func();
}

void junkCodeDexProtect(JNIEnv *env) {
    jclass klass = dpt::jni::FindClass(env,"com/luoye/dpt/junkcode/JunkClass");
    if(klass == nullptr) {
        crash();
    }
}