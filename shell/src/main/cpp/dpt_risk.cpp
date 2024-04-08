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

[[noreturn]] void *detectFridaOnThread(__unused void *args) {
    while (true) {
        int frida_so_count = find_in_maps(1,"frida-agent.so");
        if(frida_so_count > 0) {
            DLOGD("detectFridaOnThread found frida so");
            crash();
        }
        int frida_thread_count = find_in_threads_list(4
                ,"pool-frida"
                ,"gmain"
                ,"gdbus"
                ,"gum-js-loop");

        if(frida_thread_count >= 2) {
            DLOGD("detectFridaOnThread found frida threads");
            crash();
        }
        DLOGD("detectFridaOnThread pass");
        sleep(10);
    }
}


void detectFrida() {
    pthread_t t;
    pthread_create(&t, nullptr,detectFridaOnThread,nullptr);
}
