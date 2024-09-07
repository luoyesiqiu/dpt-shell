//
// Created by luoyesiqiu on 2024/9/7.
//

#ifndef DPT_DPT_STRING_H
#define DPT_DPT_STRING_H

#include <string.h>
#ifdef __cplusplus
extern "C" {
#endif
int dpt_memcmp(const void *cs, const void *ct, size_t count);
size_t dpt_strlen(const char *s);
char *dpt_strstr(const char *s1, const char *s2);
#ifdef __cplusplus
};
#endif
#endif //DPT_DPT_STRING_H
