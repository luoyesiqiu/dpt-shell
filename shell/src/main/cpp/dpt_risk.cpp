//
// Created by luoyesiqiu
//

#include "dpt_risk.h"

DPT_ENCRYPT NO_INLINE void dpt_crash() {
    asm volatile(
#ifdef __aarch64__
    "mov x30,#0\t\n"
#elif __arm__
    "mov lr,#0\t\n"
#elif __i386__
    "ret\t\n"
#elif __x86_64__
    "pop %rbp\t\n"
#endif
);

}

DPT_ENCRYPT void junkCodeDexProtect(JNIEnv *env) {
    const char *className = AY_OBFUSCATE(JUNK_CLASS_FULL_NAME);
    jclass klass = dpt::jni::FindClass(env, className);
    if(klass == nullptr) {
        dpt_crash();
    }
}

[[noreturn]] DPT_ENCRYPT void *detectFridaOnThread(__unused void *args) {
    const char *frida_agent = AY_OBFUSCATE("frida-agent");
    const char *pool_frida = AY_OBFUSCATE("pool-frida");
    const char *gmain = AY_OBFUSCATE("gmain");
    const char *gbus = AY_OBFUSCATE("gdbus");
    const char *gum_js_loop = AY_OBFUSCATE("gum-js-loop");
    while (true) {

        int frida_so_count = find_in_maps(1, frida_agent);
        if(frida_so_count > 0) {
            DLOGD("found frida so");
            dpt_crash();
        }
        int frida_thread_count = find_in_threads_list(4
                ,pool_frida
                ,gmain
                ,gbus
                ,gum_js_loop);

        if(frida_thread_count >= 2) {
            DLOGD("found frida threads");
            dpt_crash();
        }
        sleep(10);
    }
}


DPT_ENCRYPT void detectFrida() {
    pthread_t t;
    pthread_create(&t, nullptr,detectFridaOnThread,nullptr);
}

DPT_ENCRYPT void doPtrace() {
    __unused int ret = sys_ptrace(PTRACE_TRACEME,0,0,0);
    DLOGD("result: %d",ret);
}

DPT_ENCRYPT void *protectProcessOnThread(void *args) {
    pid_t child = *((pid_t *)args);

    DLOGD("waitpid %d", child);

    free(args);

    int pid = waitpid(child, nullptr, 0);
    if(pid > 0) {
        DLOGW("detect child process %d exited", pid);
        dpt_crash();
    }
    DLOGD("waitpid %d end", child);

    return nullptr;
}

DPT_ENCRYPT void protectChildProcess(pid_t pid) {
    pthread_t t;
    pid_t *child = (pid_t *) malloc(sizeof(pid_t));
    *child = pid;
    pthread_create(&t, nullptr,protectProcessOnThread,child);
}