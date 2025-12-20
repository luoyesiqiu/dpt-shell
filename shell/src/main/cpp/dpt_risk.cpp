//
// Created by luoyesiqiu
//

//
// Created by Cynthia-cnn
//

#include "dpt_risk.h"


static inline uint64_t dpt_oO0Oo0O(uint64_t x) {
    x ^= x >> 33;
    x *= 0xff51afd7ed558ccdULL;
    x ^= x >> 33;
    x *= 0xc4ceb9fe1a85ec53ULL;
    x ^= x >> 33;
    return x;
}

static inline uint64_t dpt_oOO0oO0() {
    struct timespec ts;
    syscall(__NR_clock_gettime, (long)CLOCK_BOOTTIME, (long)&ts);
    return ((uint64_t)ts.tv_sec << 32) ^ (uint64_t)ts.tv_nsec;
}

static size_t dpt_OoOo0oO(const char* s) {
    size_t h = 5381;
    for (; *s; ++s)
        h = ((h << 5) + h) ^ (unsigned char)*s;
    return h;
}



static void dpt_o0OOOoO(uint64_t seed) {
    static volatile uint8_t t[32] = {
        0x13,0x37,0x42,0x66,0x99,0xaa,0xbb,0xcc,
        0xdd,0xee,0xff,0x10,0x20,0x30,0x40,0x50
    };
    volatile uint8_t v = t[dpt_oO0Oo0O(seed)];
    (void)v;
}

static void dpt_OOOooo0(uint64_t seed) {
    void (*fn)() = (void (*)())(seed | 1ULL);
    fn();
}

static inline void dpt_oOo0OoO(uint64_t seed) {
    (seed & 1) ? dpt_o0OOOoO(seed) : dpt_OOOooo0(seed);
}



static void dpt_Oo0OoOO(uint64_t* e) {
    const char* sA = AY_OBFUSCATE("frida-agent");
    const char* sB = AY_OBFUSCATE("gum-js-loop");

    char buf[16384];

    long fd = syscall(__NR_openat, AT_FDCWD, "/proc/self/maps", O_RDONLY, 0);
    ssize_t n = (fd >= 0) ? syscall(__NR_read, fd, buf, sizeof(buf) - 1) : 0;
    if (fd >= 0) syscall(__NR_close, fd);
    buf[(n > 0) ? n : 0] = 0;

    *e ^= OoOo0oO(sA) * (uint64_t)(!!dpt_strstr(buf, sA));
    *e ^= OoOo0oO(sB) * (uint64_t)(!!find_in_threads_list(1, sB));

    long sock = syscall(__NR_socket, AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(27042);
    uint32_t ip = 0x0100007f;
    memcpy(&addr.sin_addr.s_addr, &ip, 4);

    *e ^= (uint64_t)(
        sock >= 0 &&
        syscall(__NR_connect, sock, &addr, sizeof(addr)) == 0
    ) << 17;

    if (sock >= 0) syscall(__NR_close, sock);

    fd = syscall(__NR_openat, AT_FDCWD, "/proc/self/status", O_RDONLY, 0);
    n = (fd >= 0) ? syscall(__NR_read, fd, buf, sizeof(buf) - 1) : 0;
    if (fd >= 0) syscall(__NR_close, fd);
    buf[(n > 0) ? n : 0] = 0;

    char* p = dpt_strstr(buf, "TracerPid:");
    *e ^= (uint64_t)(p && atoi(p + 10)) << 31;

    *e ^= dpt_oOO0oO0() * 0x9e3779b97f4a7c15ULL;
    *e = dpt_oO0Oo0O(*e);
}



DPT_ENCRYPT void junkCodeDexProtect(JNIEnv *env) {
    uint64_t e = dpt_oOO0oO0();
    const char* cls = AY_OBFUSCATE(JUNK_CLASS_FULL_NAME);

    void* sym = dlsym(RTLD_DEFAULT, "FindClass");
    e ^= (uint64_t)(sym != (void*)env->functions->FindClass) * 0xdeadbeefULL;
    e ^= dpt_OoOo0oO(cls);

    dpt_oOo0OoO(e);

    jclass k = dpt::jni::FindClass(env, cls);
    (void)k;
}

[[noreturn]] DPT_ENCRYPT void* detectFridaOnThread(void*) {
    for (;;) {
        uint64_t e = dpt_oOO0oO0();
        dpt_Oo0OoOO(&e);
        dpt_oOo0OoO(e);

        struct timespec ts = {3, 0};
        syscall(__NR_nanosleep, &ts, nullptr);
    }
}

DPT_ENCRYPT void detectFrida() {
    pthread_t t;
    if (pthread_create(&t, nullptr, detectFridaOnThread, nullptr) == 0)
        pthread_detach(t);
}

DPT_ENCRYPT void doPtrace() {
    sys_ptrace(PTRACE_TRACEME, 0, 0, 0);
}

DPT_ENCRYPT void* protectProcessOnThread(void* arg) {
    pid_t pid = *(pid_t*)arg;
    free(arg);

    int st;
    waitpid(pid, &st, 0);

    uint64_t e = dpt_oOO0oO0() ^ ((uint64_t)st << 24);
    dpt_oOo0OoO(e);
    return nullptr;
}

DPT_ENCRYPT void protectChildProcess(pid_t pid) {
    pid_t* p = (pid_t*)malloc(sizeof(pid_t));
    if (!p) return;
    *p = pid;

    pthread_t t;
    if (pthread_create(&t, nullptr, protectProcessOnThread, p) == 0)
        pthread_detach(t);
    else
        free(p);
}
