//
// Created by luoyesiqiu on 2025/11/26.
//

#include "dpt_crypto.h"

std::vector<uint8_t> aes_cbc_decrypt(const uint8_t *key,
                                     const uint8_t *iv,
                                     const uint8_t *in,
                                     size_t inlen) {

    std::vector<uint8_t> out_vec(inlen);

    mbedtls_aes_context ctx;
    mbedtls_aes_init(&ctx);

    int setkey_ret = mbedtls_aes_setkey_dec(&ctx, key, 128);

    if(setkey_ret == 0) {
        DLOGD("set key success");
    }
    else {
        DLOGE("set key fail");
    }

    uint8_t new_iv[16] = {0};
    memcpy(new_iv, iv, 16);

    int ret = mbedtls_aes_crypt_cbc(&ctx, MBEDTLS_AES_DECRYPT, inlen, new_iv, in, out_vec.data());

    if(ret == 0) {
        DLOGD("decrypt ret: %d", ret);
    }
    else {
        DLOGE("decrypt fail");
    }

    if (!out_vec.empty()) {
        uint8_t pad = out_vec.back();
        DLOGD("padding: %d", pad);
        if (pad > 0 && pad <= 16 && pad <= out_vec.size()) {
            out_vec.resize(out_vec.size() - pad);
        } else {
            DLOGE("invalid padding");
            return {};
        }
    }

    mbedtls_aes_free(&ctx);

    return out_vec;
}