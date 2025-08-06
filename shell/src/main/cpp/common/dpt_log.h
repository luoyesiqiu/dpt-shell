//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_LOG_H
#define DPT_DPT_LOG_H

#include <android/log.h>
#include <sys/prctl.h>
#include <cstring>

#define TAG  "dpt_native"

inline const char* getThreadName() {
    static char threadName[256];
    memset(threadName, 0, 256);
    prctl(PR_GET_NAME, (unsigned long)threadName);
    return threadName;
}

#define DLOG(_level,...) do { \
        char logBuffer[1024];                                           \
        snprintf(logBuffer, sizeof(logBuffer), __VA_ARGS__);            \
        __android_log_print(_level,TAG,"[%s] %s() %s", getThreadName(), __FUNCTION__, logBuffer);  \
} while(false)

#ifdef DEBUG

#define DLOGI(...) DLOG(ANDROID_LOG_INFO,__VA_ARGS__)
#define DLOGD(...) DLOG(ANDROID_LOG_DEBUG, __VA_ARGS__)
#define DLOGE(...) DLOG(ANDROID_LOG_ERROR,__VA_ARGS__)
#define DLOGW(...) DLOG(ANDROID_LOG_WARN,__VA_ARGS__)
#define DLOGF(...) DLOG(ANDROID_LOG_FATAL,__VA_ARGS__)

#ifdef NOISY_LOG
#define NLOG(...) DLOG(ANDROID_LOG_INFO,__VA_ARGS__)
#else
#define NLOG(...)
#endif

#else
#define DLOGI(...)
#define DLOGD(...)
#define DLOGE(...)
#define DLOGW(...)
#define DLOGF(...)

#define NLOG(...)
#endif


#endif //DPT_DPT_LOG_H
