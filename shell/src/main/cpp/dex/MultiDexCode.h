//
// Created by luoyesiqiu
//

#ifndef DPT_MULTIDEXCODE_H
#define DPT_MULTIDEXCODE_H

#include <stdint.h>
#include "CodeItem.h"
#include "common/dpt_log.h"
namespace dpt {
    namespace data {
        class MultiDexCode {
        private:
            size_t m_size;
            uint8_t *m_buffer;
        public:
            static MultiDexCode *getInst();

            void init(uint8_t *buffer, size_t size);

            uint8_t readUInt8(uint32_t offset);

            uint16_t readUInt16(uint32_t offset);

            uint32_t readUInt32(uint32_t offset);

            uint16_t readVersion();

            uint16_t readDexCount();

            uint32_t *readDexCodeIndex(int *count);

            dpt::data::CodeItem *nextCodeItem(uint32_t *offset);
        };
    }
}



#endif //DPT_MULTIDEXCODE_H
