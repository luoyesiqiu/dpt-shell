//
// Created by luoyesiqiu
//
#include "ClassDataItemReader.h"

extern int g_sdkLevel;
uint32_t ClassDataItemReader::GetMethodCodeItemOffset(){
    int api = g_sdkLevel;
    uint32_t ret = 0;
    if(api == 29 || api == 30){
        ClassDataItemIteratorQ::Method *q = (ClassDataItemIteratorQ::Method *) it;
        ret = q->GetMethodCodeItemOffset();
    }
    else if(api == 28){
        ClassDataItemIteratorP *p = (ClassDataItemIteratorP *) it;
        ret =  p->GetMethodCodeItemOffset();
    }
    else if(api == 26 || api == 27){
        // android O
        ClassDataItemIteratorO *o = (ClassDataItemIteratorO *) it;
        ret = o->GetMethodCodeItemOffset();
    }
    else if(api == 24 || api == 25){
        // android N
        ClassDataItemIteratorN *n = (ClassDataItemIteratorN *) it;
        ret = n->GetMethodCodeItemOffset();
    }

    return ret;
}

bool ClassDataItemReader::MemberIsNative(){
    int api = g_sdkLevel;
    bool ret = false;
    if(api == 29 || api == 30){
        ClassDataItemIteratorQ::Method *q = (ClassDataItemIteratorQ::Method *) it;
        ret = q->MemberIsNative();
    }
    else if(api == 28){
        ClassDataItemIteratorP *p = (ClassDataItemIteratorP *) it;
        ret =  p->MemberIsNative();
    }
    else if(api == 26 || api == 27){
        // android O
        ClassDataItemIteratorO *o = (ClassDataItemIteratorO *) it;
        ret = o->MemberIsNative();
    }
    else if(api == 24 || api == 25){
        // android N
        ClassDataItemIteratorN *n = (ClassDataItemIteratorN *) it;
        ret = n->MemberIsNative();
    }

    return ret;
}

bool ClassDataItemReader::MemberIsFinal(){
    int api = g_sdkLevel;
    bool ret = false;
    if(api == 29 || api == 30){
        ClassDataItemIteratorQ::Method *q = (ClassDataItemIteratorQ::Method *) it;
        ret = q->MemberIsFinal();
    }
    else if(api == 28){
        ClassDataItemIteratorP *p = (ClassDataItemIteratorP *) it;
        ret =  p->MemberIsFinal();
    }
    else if(api == 26 || api == 27){
        // android O
        ClassDataItemIteratorO *o = (ClassDataItemIteratorO *) it;
        ret = o->MemberIsFinal();
    }
    else if(api == 24 || api == 25){
        // android N
        ClassDataItemIteratorN *n = (ClassDataItemIteratorN *) it;
        ret = n->MemberIsFinal();
    }

    return ret;
}

uint32_t ClassDataItemReader::GetMemberIndex(){
    int api = g_sdkLevel;
    uint32_t ret = false;
    if(api == 29 || api == 30){
        ClassDataItemIteratorQ::Method *q = (ClassDataItemIteratorQ::Method *) it;
        ret = q->GetMemberIndex();
    }
    else if(api == 28){
        ClassDataItemIteratorP *p = (ClassDataItemIteratorP *) it;
        ret =  p->GetMemberIndex();
    }
    else if(api == 26 || api == 27){
        // android O
        ClassDataItemIteratorO *o = (ClassDataItemIteratorO *) it;
        ret = o->GetMemberIndex();
    }
    else if(api == 24 || api == 25){
        // android N
        ClassDataItemIteratorN *n = (ClassDataItemIteratorN *) it;
        ret = n->GetMemberIndex();
    }

    return ret;
}
