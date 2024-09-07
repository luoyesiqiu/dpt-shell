//
// Created by luoyesiqiu on 2024/9/7.
//

#include "dpt_string.h"

int dpt_memcmp(const void *cs, const void *ct, size_t count)
{
    const unsigned char *su1, *su2;
    int res = 0;

    for (su1 = (unsigned char *)cs, su2 = (unsigned char *)ct; 0 < count; ++su1, ++su2, count--)
        if ((res = *su1 - *su2) != 0)
            break;
    return res;
}

size_t dpt_strlen(const char *s)
{
    const char *sc;

    for (sc = s; *sc != '\0'; ++sc)
        /* nothing */;
    return sc - s;
}

char *dpt_strstr(const char *s1, const char *s2)
{
    size_t l1, l2;

    l2 = dpt_strlen(s2);
    if (!l2)
        return (char *)s1;
    l1 = dpt_strlen(s1);
    while (l1 >= l2) {
        l1--;
        if (!dpt_memcmp(s1, s2, l2))
            return (char *)s1;
        s1++;
    }
    return NULL;
}