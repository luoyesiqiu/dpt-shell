//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_LOG_H
#define DPT_DPT_LOG_H
#define TAG  "dpt_native"
#include "android/log.h"

#ifdef DEBUG
#define DLOG(_level,...) do { \
        char logBuffer[1024];                                              \
        snprintf(logBuffer, sizeof(logBuffer), __VA_ARGS__);            \
        __android_log_print(_level,TAG,"[%s] %s",getThreadName(),logBuffer);                            \
}while(false)

#define DLOGI(...) DLOG(ANDROID_LOG_INFO,__VA_ARGS__)
#define DLOGD(...) DLOG(ANDROID_LOG_DEBUG, __VA_ARGS__)
#define DLOGE(...) DLOG(ANDROID_LOG_ERROR,__VA_ARGS__)
#define DLOGW(...) DLOG(ANDROID_LOG_WARN,__VA_ARGS__)

#ifdef NOICE_LOG
#define NLOG(...) DLOG(ANDROID_LOG_INFO,__VA_ARGS__)
#else
#define NLOG(...)
#endif

#else
#define DLOGI(...)
#define DLOGD(...)
#define DLOGE(...)
#define DLOGW(...)
#define NLOG(...)
#endif


#endif //DPT_DPT_LOG_H
