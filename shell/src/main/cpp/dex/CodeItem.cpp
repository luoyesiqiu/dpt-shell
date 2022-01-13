//
// Created by luoyesiqiu
//
#include "CodeItem.h"

uint32_t CodeItem::getMethodIdx() const {
    return mMethodIdx;
}

void CodeItem::setMethodIdx(uint32_t methodIdx) {
    CodeItem::mMethodIdx = methodIdx;
}

uint32_t CodeItem::getOffsetDex() const {
    return mOffsetDex;
}

void CodeItem::setOffsetDex(uint32_t offsetDex) {
    CodeItem::mOffsetDex = offsetDex;
}

uint32_t CodeItem::getInsnsSize() const {
    return mInsnsSize;
}

void CodeItem::setInsnsSize(uint32_t size) {
    CodeItem::mInsnsSize = size;
}

uint8_t *CodeItem::getInsns() const {
    return mInsns;
}

void CodeItem::setInsns(uint8_t *insns) {
    CodeItem::mInsns = insns;
}

CodeItem::CodeItem(uint32_t methodIdx, uint32_t offsetDex, uint32_t size,
                   uint8_t *insns): mMethodIdx(methodIdx), mOffsetDex(offsetDex), mInsnsSize(size), mInsns(insns) {

}

CodeItem::~CodeItem() {

}
