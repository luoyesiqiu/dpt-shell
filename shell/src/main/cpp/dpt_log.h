//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_LOG_H
#define DPT_DPT_LOG_H
#define TAG  "dpt_native"
#include "android/log.h"

#define DEBUG

#ifdef DEBUG
#define DLOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG,__VA_ARGS__)
#define DLOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define DLOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)
#define DLOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG,__VA_ARGS__)
#else
#define DLOGI(...)
#define DLOGD(...)
#define DLOGE(...)
#define DLOGW(...)
#endif

#ifdef NOICE_LOG
#define NLOG(...) { \
    __android_log_print(ANDROID_LOG_INFO,TAG,__VA_ARGS__); \
}
#else
#define NLOG(...)
#endif

#endif //DPT_DPT_LOG_H
