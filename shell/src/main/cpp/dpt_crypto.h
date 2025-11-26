//
// Created by luoyesiqiu on 2025/11/26.
//

#ifndef DPT_DPT_CRYPTO_H
#define DPT_DPT_CRYPTO_H

#include <vector>
#include <stdint.h>
#include <mbedtls/aes.h>
#include "common/dpt_log.h"

std::vector<uint8_t> aes_cbc_decrypt(const uint8_t *key,
                                     const uint8_t *iv,
                                     const uint8_t *in,
                                     size_t inlen);
#endif //DPT_DPT_CRYPTO_H
