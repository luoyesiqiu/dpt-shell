//
// Created by luoyesiqiu
//

#ifndef DPT_CLASSDATAITEMITERATORP_H
#define DPT_CLASSDATAITEMITERATORP_H
#include <stdint.h>

#include "modifiers.h"

class ClassDataItemIteratorP {
public:
    uint32_t NumStaticFields() const {
        return header_.static_fields_size_;
    }
    uint32_t NumInstanceFields() const {
        return header_.instance_fields_size_;
    }
    uint32_t NumDirectMethods() const {
        return header_.direct_methods_size_;
    }
    uint32_t NumVirtualMethods() const {
        return header_.virtual_methods_size_;
    }
    bool IsAtMethod() const {
        return pos_ >= EndOfInstanceFieldsPos();
    }
    bool HasNextStaticField() const {
        return pos_ < EndOfStaticFieldsPos();
    }
    bool HasNextInstanceField() const {
        return pos_ >= EndOfStaticFieldsPos() && pos_ < EndOfInstanceFieldsPos();
    }
    bool HasNextDirectMethod() const {
        return pos_ >= EndOfInstanceFieldsPos() && pos_ < EndOfDirectMethodsPos();
    }
    bool HasNextVirtualMethod() const {
        return pos_ >= EndOfDirectMethodsPos() && pos_ < EndOfVirtualMethodsPos();
    }
    bool HasNextMethod() const {
        const bool result = pos_ >= EndOfInstanceFieldsPos() && pos_ < EndOfVirtualMethodsPos();
        //DCHECK_EQ(result, HasNextDirectMethod() || HasNextVirtualMethod());
        return result;
    }

    bool HasNext() const {
        return pos_ < EndOfVirtualMethodsPos();
    }

    uint32_t GetMemberIndex() const {
        if (pos_ < EndOfInstanceFieldsPos()) {
            return last_idx_ + field_.field_idx_delta_;
        } else {
            return last_idx_ + method_.method_idx_delta_;
        }
    }
    uint32_t GetRawMemberAccessFlags() const {
        if (pos_ < EndOfInstanceFieldsPos()) {
            return field_.access_flags_;
        } else {
            return method_.access_flags_;
        }
    }

    bool MemberIsNative() const {
        return GetRawMemberAccessFlags() & kAccNative;
    }
    bool MemberIsFinal() const {
        return GetRawMemberAccessFlags() & kAccFinal;
    }
    uint32_t GetMethodCodeItemOffset() const {
        return method_.code_off_;
    }
    const uint8_t* DataPointer() const {
        return ptr_pos_;
    }
    const uint8_t* EndDataPointer() const {
        //CHECK(!HasNext());
        return ptr_pos_;
    }

private:
    // A dex file's class_data_item is leb128 encoded, this structure holds a decoded form of the
    // header for a class_data_item
    struct ClassDataHeader {
        uint32_t static_fields_size_;  // the number of static fields
        uint32_t instance_fields_size_;  // the number of instance fields
        uint32_t direct_methods_size_;  // the number of direct methods
        uint32_t virtual_methods_size_;  // the number of virtual methods
    } header_;

    // Read and decode header from a class_data_item stream into header
    //void ReadClassDataHeader();

    uint32_t EndOfStaticFieldsPos() const {
        return header_.static_fields_size_;
    }
    uint32_t EndOfInstanceFieldsPos() const {
        return EndOfStaticFieldsPos() + header_.instance_fields_size_;
    }
    uint32_t EndOfDirectMethodsPos() const {
        return EndOfInstanceFieldsPos() + header_.direct_methods_size_;
    }
    uint32_t EndOfVirtualMethodsPos() const {
        return EndOfDirectMethodsPos() + header_.virtual_methods_size_;
    }

    // A decoded version of the field of a class_data_item
    struct ClassDataField {
        uint32_t field_idx_delta_;  // delta of index into the field_ids array for FieldId
        uint32_t access_flags_;  // access flags for the field
        ClassDataField() :  field_idx_delta_(0), access_flags_(0) {}

    private:
        //DISALLOW_COPY_AND_ASSIGN(ClassDataField);
    };
    ClassDataField field_;

    // Read and decode a field from a class_data_item stream into field
    //void ReadClassDataField();

    // A decoded version of the method of a class_data_item
    struct ClassDataMethod {
        uint32_t method_idx_delta_;  // delta of index into the method_ids array for MethodId
        uint32_t access_flags_;
        uint32_t code_off_;
        ClassDataMethod() : method_idx_delta_(0), access_flags_(0), code_off_(0) {}

    private:
        //DISALLOW_COPY_AND_ASSIGN(ClassDataMethod);
    };
    ClassDataMethod method_;

    // Read and decode a method from a class_data_item stream into method
    //void ReadClassDataMethod();

    const void* dex_file_;
    size_t pos_;  // integral number of items passed
    const uint8_t* ptr_pos_;  // pointer into stream of class_data_item
    uint32_t last_idx_;  // last read field or method index to apply delta to
    //DISALLOW_IMPLICIT_CONSTRUCTORS(ClassDataItemIterator);
};


#endif //DPT_CLASSDATAITEMITERATORP_H
