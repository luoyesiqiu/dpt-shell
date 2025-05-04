//
// Created by luoyesiqiu
//
#include "CodeItem.h"

uint32_t dpt::data::CodeItem::getMethodIdx() const {
    return mMethodIdx;
}

void dpt::data::CodeItem::setMethodIdx(uint32_t methodIdx) {
    CodeItem::mMethodIdx = methodIdx;
}

uint32_t dpt::data::CodeItem::getInsnsSize() const {
    return mInsnsSize;
}

void dpt::data::CodeItem::setInsnsSize(uint32_t size) {
    CodeItem::mInsnsSize = size;
}

uint8_t *dpt::data::CodeItem::getInsns() const {
    return mInsns;
}

void dpt::data::CodeItem::setInsns(uint8_t *insns) {
    CodeItem::mInsns = insns;
}

dpt::data::CodeItem::CodeItem(uint32_t methodIdx, uint32_t size,
                   uint8_t *insns): mMethodIdx(methodIdx), mInsnsSize(size), mInsns(insns) {

}

dpt::data::CodeItem::~CodeItem() {

}
