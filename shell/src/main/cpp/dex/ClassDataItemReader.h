//
// Created by luoyesiqiu
//

#ifndef DPT_CLASSDATAITEMREADER_H
#define DPT_CLASSDATAITEMREADER_H

#include <android/api-level.h>
#include "dex/ClassDataItemIteratorN.h"
#include "dex/ClassDataItemIteratorO.h"
#include "dex/ClassDataItemIteratorP.h"
#include "dex/ClassDataItemIteratorQ.h"
#include "dpt_log.h"
class ClassDataItemReader {
private:
    const void* it = nullptr;
public:
    ClassDataItemReader(const void* iterator) : it(iterator){

    }
    uint32_t GetMethodCodeItemOffset();
    bool MemberIsNative();
    bool MemberIsFinal();
    uint32_t GetMemberIndex();


};


#endif //DPT_CLASSDATAITEMREADER_H
