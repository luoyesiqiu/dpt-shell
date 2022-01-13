//
// Created by luoyesiqiu
//

#include "MultiDexCode.h"

MultiDexCode* MultiDexCode::m_inst = nullptr;
MultiDexCode* MultiDexCode::getInst(){
    if(nullptr == m_inst){
        m_inst = new MultiDexCode();
    }

    return m_inst;
}

void MultiDexCode::init(uint8_t* buffer, int size){
    this->m_buffer = buffer;
    this->m_size = size;
}

uint16_t MultiDexCode::readVersion(){
    return readUInt16(0);
}

uint16_t MultiDexCode::readDexCount(){
    return readUInt16(2);
}

uint32_t* MultiDexCode::readDexCodeIndex(int* count){
    uint16_t dexCount = readDexCount();
    *count = dexCount;
    return (uint32_t*)(m_buffer + 4);
}


CodeItem* MultiDexCode::nextCodeItem(uint32_t* offset) {
//    DLOGD("nextCodeItem start = %d",*offset);

    uint32_t methodIdx = readUInt32(*offset);
//    DLOGD("offset = %d,methodIdx = %d",*offset,methodIdx);

    uint32_t offsetOfDex = readUInt32(*offset + 4);
//    DLOGD("offset = %d,offsetOfDex = %d",*offset,offsetOfDex);

    uint32_t insnsSize = readUInt32(*offset + 8);
//    DLOGD("offset = %d,insnsSize = %d",*offset,insnsSize);


    uint8_t* insns = (uint8_t*)(m_buffer + *offset + 12);
//    DLOGD("offset = %d,insns = %p",*offset,insns);

    *offset = (*offset + 12 + insnsSize);
//    DLOGD("*offset = %d",*offset);

    CodeItem* codeItem = new CodeItem(methodIdx,offsetOfDex,insnsSize,insns);

    return codeItem;
}

uint8_t MultiDexCode::readUInt8(uint32_t offset){
    uint8_t t = *((uint8_t*)(m_buffer + offset));

    return t;
}

uint16_t MultiDexCode::readUInt16(uint32_t offset){
    uint16_t t = *((uint16_t*)(m_buffer + offset));

    return t;
}

uint32_t MultiDexCode::readUInt32(uint32_t offset){
    uint32_t t = *((uint32_t*)(m_buffer + offset));

    return t;
}