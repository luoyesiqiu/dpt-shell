//
// Created by luoyesiqiu
//

#ifndef DPT_CODEITEM_H
#define DPT_CODEITEM_H

#include <stdint.h>

namespace dpt {
    namespace data {
        class CodeItem {
        private:
            uint32_t mMethodIdx;
            uint32_t mOffsetDex;
            uint32_t mInsnsSize;
            uint8_t *mInsns;
        public:
            uint32_t getMethodIdx() const;

            void setMethodIdx(uint32_t methodIdx);

            uint32_t getOffsetDex() const;

            void setOffsetDex(uint32_t offsetDex);

            uint32_t getInsnsSize() const;

            void setInsnsSize(uint32_t size);

            uint8_t *getInsns() const;

            void setInsns(uint8_t *insns);

            CodeItem();

            CodeItem(uint32_t methodIdx, uint32_t offsetDex, uint32_t size, uint8_t *insns);

            ~CodeItem();
        };
    }
}

#endif //DPT_CODEITEM_H
