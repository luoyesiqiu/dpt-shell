//
// Created by luoyesiqiu
//

//
// improve by Cynthia-cnn
//

#include "dpt_risk.h"

static bool dpt_read_maps_direct(const char* needle) {
    long fd = syscall(__NR_openat, (long)AT_FDCWD, (long)"/proc/self/maps", (long)O_RDONLY, (long)0);
    if (fd < 0) return false;

    char buf[16384]; // up buffer
    ssize_t total = 0;
    ssize_t n;
    while (total < (ssize_t)sizeof(buf) - 1) {
        n = syscall(__NR_read, (long)fd, (long)(buf + total), (long)(sizeof(buf) - 1 - total));
        if (n <= 0) break;
        total += n;
    }
    syscall(__NR_close, (long)fd);

    if (total <= 0) return false;
    buf[total] = '\0';
    return !!dpt_strstr(buf, needle); 
}

static bool dpt_check_port_direct(int port) {
    long sock = syscall(__NR_socket, (long)AF_INET, (long)SOCK_STREAM, (long)0);
    if (sock < 0) return false;

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr)); // memset
    addr.sin_family = AF_INET;
    addr.sin_port = htons((uint16_t)port);
    uint32_t ip = 0x0100007f; // 127.0.0.1
    memcpy(&addr.sin_addr.s_addr, &ip, 4);

    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 200000; // 200ms

    syscall(__NR_setsockopt, (long)sock, (long)SOL_SOCKET, (long)SO_RCVTIMEO, (long)&tv, (long)sizeof(tv));
    syscall(__NR_setsockopt, (long)sock, (long)SOL_SOCKET, (long)SO_SNDTIMEO, (long)&tv, (long)sizeof(tv));

    long r = syscall(__NR_connect, (long)sock, (long)&addr, (long)sizeof(addr));
    syscall(__NR_close, (long)sock);
    return r == 0;
}

static bool dpt_is_debugged() {
    long fd = syscall(__NR_openat, (long)AT_FDCWD, (long)"/proc/self/status", (long)O_RDONLY, (long)0);
    if (fd >= 0) {
        char buf[2048];
        ssize_t n = syscall(__NR_read, (long)fd, (long)buf, (long)(sizeof(buf) - 1));
        syscall(__NR_close, (long)fd);
        if (n > 0) {
            buf[n] = '\0';
            char* p = dpt_strstr(buf, "TracerPid:");
            if (p) {
                int tracer_pid = atoi(p + 10);
                if (tracer_pid != 0) {
                    return true;
                }
            }
        }
    }
    return false;
}

DPT_ENCRYPT NO_INLINE void dpt_crash() {
#ifdef __aarch64__
    asm volatile("brk #0" ::: "memory");
#elif defined(__arm__)
    asm volatile(".word 0xe7f001f0" ::: "memory");
#elif defined(__i386__)
    asm volatile("ud2" ::: "memory");
#elif defined(__x86_64__)
    asm volatile("ud2" ::: "memory");
#else
    raise(SIGILL);
#endif
    _exit(1);
}

DPT_ENCRYPT void junkCodeDexProtect(JNIEnv *env) {
    const char *className = AY_OBFUSCATE(JUNK_CLASS_FULL_NAME);
    void* sym = dlsym(RTLD_DEFAULT, "FindClass");
    if (sym && sym != (void*)env->functions->FindClass) {
        dpt_crash();
    }
    jclass klass = dpt::jni::FindClass(env, className);
    if (klass == nullptr) {
        dpt_crash();
    }
}

[[noreturn]] DPT_ENCRYPT void *detectFridaOnThread(__unused void *args) {
    const char *frida_agent = AY_OBFUSCATE("frida-agent");
    const char *pool_frida = AY_OBFUSCATE("pool-frida");
    const char *gmain = AY_OBFUSCATE("gmain");
    const char *gbus = AY_OBFUSCATE("gdbus");
    const char *gum_js_loop = AY_OBFUSCATE("gum-js-loop");

    struct timespec ts;
    clock_gettime(CLOCK_BOOTTIME, &ts);
    srand((unsigned int)(ts.tv_nsec ^ ts.tv_sec));

    while (true) {
        if (dpt_read_maps_direct(frida_agent)) {
            dpt_crash();
        }

        int cnt = find_in_threads_list(4, pool_frida, gmain, gbus, gum_js_loop);
        if (cnt >= 2) {
            dpt_crash();
        }

        if (dpt_check_port_direct(27042) || dpt_check_port_direct(27043)) {
            dpt_crash();
        }

        if (dpt_is_debugged()) {
            dpt_crash();
        }

        ts.tv_sec = 5 + (rand() % 11);
        ts.tv_nsec = (rand() % 1000) * 1000000ULL;
        while (syscall(__NR_nanosleep, (long)&ts, (long)&ts) == -1 && errno == EINTR) {
        }
    }
}

DPT_ENCRYPT void detectFrida() {
    pthread_t t;
    if (pthread_create(&t, nullptr, detectFridaOnThread, nullptr) != 0) {
        dpt_crash();
    }
    pthread_detach(t);
}

DPT_ENCRYPT void doPtrace() {
    __unused int ret = sys_ptrace(PTRACE_TRACEME, 0, 0, 0);
    if (dpt_is_debugged()) {
        dpt_crash();
    }
}

DPT_ENCRYPT void *protectProcessOnThread(void *args) {
    pid_t child = *((pid_t *)args);
    free(args);

    int status;
    int pid = waitpid(child, &status, 0);
    if (pid > 0 && WIFSIGNALED(status)) {
        dpt_crash();
    }
    return nullptr;
}

DPT_ENCRYPT void protectChildProcess(pid_t pid) {
    pid_t *child = (pid_t *)malloc(sizeof(pid_t));
    if (!child) {
        dpt_crash();
    }
    *child = pid;
    pthread_t t;
    if (pthread_create(&t, nullptr, protectProcessOnThread, child) != 0) {
        free(child);
        dpt_crash();
    }
    pthread_detach(t);
}
