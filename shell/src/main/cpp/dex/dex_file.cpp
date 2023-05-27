//
// Created by luoyesiqiu
//
#include "dex_file.h"

size_t dpt::DexFileUtils::readUleb128(uint8_t const * const data, uint64_t * const val) {
    uint64_t result = 0;
    size_t read = 0;
    for(int i = 0;i < 5;i++){
        uint8_t b = *(data + i);
        uint8_t value =  b & 0x7f;
        result |= (value << (i * 7));
        read++;
        if((b & 0x80) != 0x80){
            break;
        }
    }
    *val = result;
    return read;
}

size_t dpt::DexFileUtils::readFields(uint8_t *data,dpt::dex::ClassDataField *field,uint64_t count){
    size_t read = 0;
    uint32_t fieldIndexDelta = 0;
    for (uint64_t i = 0; i < count; ++i) {
        uint64_t fieldIndex = 0;
        read += readUleb128(data + read,&fieldIndex);
        fieldIndexDelta += fieldIndex;

        uint64_t accessFlags = 0;
        read += readUleb128(data + read,&accessFlags);
        field[i].field_idx_delta_ = fieldIndexDelta;
        field[i].access_flags_ = accessFlags;
    }

    return read;
}

size_t dpt::DexFileUtils::readMethods(uint8_t *data,dpt::dex::ClassDataMethod *method,uint64_t count){
    size_t read = 0;
    uint32_t methodIndexDelta = 0;
    for (uint64_t i = 0; i < count; ++i) {
        uint64_t methodIndex = 0;
        read += readUleb128(data + read,&methodIndex);
        methodIndexDelta += methodIndex;

        uint64_t accessFlags = 0;
        read += readUleb128(data + read,&accessFlags);

        uint64_t codeOff = 0;
        read += readUleb128(data + read,&codeOff);

        method[i].method_idx_delta_ = methodIndexDelta;
        method[i].access_flags_ = accessFlags;
        method[i].code_off_ = codeOff;
    }

    return read;
}
