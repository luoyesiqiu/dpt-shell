//
// Created by luoyesiqiu
//

#include "dpt_risk.h"

void crash() {
#ifdef __aarch64__
    asm volatile(
            "mov x30,#0\t\n"
            );
#elif __arm__
    asm volatile(
        "mov lr,#0\t\n"
    );
#elif __i386__
    asm volatile(
            "ret\t\n"
            );
#elif __x86_64__
    asm volatile(
            "pop %rbp\t\n"
    );
#endif
}

void junkCodeDexProtect(JNIEnv *env) {
    jclass klass = dpt::jni::FindClass(env,"com/luoye/dpt/junkcode/JunkClass");
    if(klass == nullptr) {
        crash();
    }
}

[[noreturn]] void *detectFridaOnThread(__unused void *args) {
    while (true) {
        int frida_so_count = find_in_maps(1,"frida-agent");
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

void doPtrace() {
    __unused int ret = sys_ptrace(PTRACE_TRACEME,0,0,0);
    DLOGD("doPtrace result: %d",ret);
}

void *protectProcessOnThread(void *args) {
    pid_t child = *((pid_t *)args);

    DLOGD("%s waitpid %d", __FUNCTION__ ,child);

    free(args);

    int pid = waitpid(child, nullptr, 0);
    if(pid > 0) {
        DLOGW("%s detect child process %d exited", __FUNCTION__, pid);
        crash();
    }
    DLOGD("%s waitpid %d end", __FUNCTION__ ,child);

    return nullptr;
}

void protectChildProcess(pid_t pid) {
    pthread_t t;
    pid_t *child = (pid_t *) malloc(sizeof(pid_t));
    *child = pid;
    pthread_create(&t, nullptr,protectProcessOnThread,child);
}