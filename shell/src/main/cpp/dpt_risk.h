//
// Created by luoyesiqiu
//

#ifndef DPT_DPT_RISK_H
#define DPT_DPT_RISK_H

#include <dlfcn.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <ctype.h>
#include <pthread.h>
#include <sys/ptrace.h>
#include <sys/wait.h>

#include <jni.h>

#include "dpt_util.h"
#include "dpt_log.h"
#include "dpt_jni.h"
#include "linux_syscall_support.h"
#include "common/obfuscate.h"

void dpt_crash();
void detectFrida();
void doPtrace();
void protectChildProcess(pid_t pid);
void junkCodeDexProtect(JNIEnv *env);

#endif //DPT_DPT_RISK_H
