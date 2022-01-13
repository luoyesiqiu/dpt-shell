//
// Created by luoyesiqiu
//

#ifndef DPT_CLASSDATAITEMITERATORQ_H
#define DPT_CLASSDATAITEMITERATORQ_H
#include <stdint.h>

#include "modifiers.h"
class ClassDataItemIteratorQ {

public:
    class BaseItem {
    public:
        explicit BaseItem(const void* dex_file,
                          const uint8_t* ptr_pos,
                          const uint8_t* hiddenapi_ptr_pos)
                : dex_file_(dex_file), ptr_pos_(ptr_pos), hiddenapi_ptr_pos_(hiddenapi_ptr_pos) {}

        uint32_t GetIndex() const {
            return index_;
        }

        uint32_t GetAccessFlags() const {
            return access_flags_;
        }

        uint32_t GetHiddenapiFlags() const {
            return hiddenapi_flags_;
        }

        bool IsFinal() const {
            return (GetAccessFlags() & kAccFinal) != 0;
        }

        const void* GetDexFile() const {
            return dex_file_;
        }

        const uint8_t* GetDataPointer() const {
            return ptr_pos_;
        }

        bool MemberIsNative() const {
            return GetAccessFlags() & kAccNative;
        }

        bool MemberIsFinal() const {
            return GetAccessFlags() & kAccFinal;
        }

    protected:
        // Internal data pointer for reading.
        const void* dex_file_;
        const uint8_t* ptr_pos_ = nullptr;
        const uint8_t* hiddenapi_ptr_pos_ = nullptr;
        uint32_t index_ = 0u;
        uint32_t access_flags_ = 0u;
        uint32_t hiddenapi_flags_ = 0u;
    };

    class Method : BaseItem{
    public:
        bool MemberIsNative() const {
            return GetAccessFlags() & kAccNative;
        }

        bool MemberIsFinal() const {
            return GetAccessFlags() & kAccFinal;
        }

        uint32_t GetMemberIndex() const {
            return GetIndex();
        }

        uint32_t GetMethodCodeItemOffset() const {
            return code_off_;
        }

        protected:
            bool is_static_or_direct_ = true;
            uint32_t code_off_ = 0u;

            friend class ClassAccessor;
            friend class DexFileVerifier;
    };

    const void* dex_file_;
    const uint32_t class_def_index_;
    const uint8_t* ptr_pos_ = nullptr;  // Pointer into stream of class_data_item.
    const uint8_t* hiddenapi_ptr_pos_ = nullptr;  // Pointer into stream of hiddenapi_metadata.
    const uint32_t num_static_fields_ = 0u;
    const uint32_t num_instance_fields_ = 0u;
    const uint32_t num_direct_methods_ = 0u;
    const uint32_t num_virtual_methods_ = 0u;

    friend class DexFileVerifier;
};
#endif //DPT_CLASSDATAITEMITERATORQ_H
