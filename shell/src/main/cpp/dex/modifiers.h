/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ART_RUNTIME_MODIFIERS_H_
#define ART_RUNTIME_MODIFIERS_H_

#include <stdint.h>


static constexpr uint32_t kAccPublic =       0x0001;  // class, field, method, ic
static constexpr uint32_t kAccPrivate =      0x0002;  // field, method, ic
static constexpr uint32_t kAccProtected =    0x0004;  // field, method, ic
static constexpr uint32_t kAccStatic =       0x0008;  // field, method, ic
static constexpr uint32_t kAccFinal =        0x0010;  // class, field, method, ic
static constexpr uint32_t kAccSynchronized = 0x0020;  // method (only allowed on natives)
static constexpr uint32_t kAccSuper =        0x0020;  // class (not used in dex)
static constexpr uint32_t kAccVolatile =     0x0040;  // field
static constexpr uint32_t kAccBridge =       0x0040;  // method (1.5)
static constexpr uint32_t kAccTransient =    0x0080;  // field
static constexpr uint32_t kAccVarargs =      0x0080;  // method (1.5)
static constexpr uint32_t kAccNative =       0x0100;  // method
static constexpr uint32_t kAccInterface =    0x0200;  // class, ic
static constexpr uint32_t kAccAbstract =     0x0400;  // class, method, ic
static constexpr uint32_t kAccStrict =       0x0800;  // method
static constexpr uint32_t kAccSynthetic =    0x1000;  // class, field, method, ic
static constexpr uint32_t kAccAnnotation =   0x2000;  // class, ic (1.5)
static constexpr uint32_t kAccEnum =         0x4000;  // class, field, ic (1.5)

static constexpr uint32_t kAccJavaFlagsMask = 0xffff;  // bits set from Java sources (low 16)

static constexpr uint32_t kAccConstructor =           0x00010000;  // method (dex only) <(cl)init>
static constexpr uint32_t kAccDeclaredSynchronized =  0x00020000;  // method (dex only)
static constexpr uint32_t kAccClassIsProxy =          0x00040000;  // class  (dex only)
// Set to indicate that the ArtMethod is obsolete and has a different DexCache + DexFile from its
// declaring class. This flag may only be applied to methods.
static constexpr uint32_t kAccObsoleteMethod =        0x00040000;  // method (runtime)
// Used by a method to denote that its execution does not need to go through slow path interpreter.
static constexpr uint32_t kAccSkipAccessChecks =      0x00080000;  // method (dex only)
// Used by a class to denote that the verifier has attempted to check it at least once.
static constexpr uint32_t kAccVerificationAttempted = 0x00080000;  // class (runtime)
static constexpr uint32_t kAccFastNative =            0x00080000;  // method (dex only)
// This is set by the class linker during LinkInterfaceMethods. It is used by a method to represent
// that it was copied from its declaring class into another class. All methods marked kAccMiranda
// and kAccDefaultConflict will have this bit set. Any kAccDefault method contained in the methods_
// array of a concrete class will also have this bit set.
static constexpr uint32_t kAccCopied =                0x00100000;  // method (runtime)
static constexpr uint32_t kAccMiranda =               0x00200000;  // method (dex only)
static constexpr uint32_t kAccDefault =               0x00400000;  // method (runtime)
// This is set by the class linker during LinkInterfaceMethods. Prior to that point we do not know
// if any particular method needs to be a default conflict. Used to figure out at runtime if
// invoking this method will throw an exception.
static constexpr uint32_t kAccDefaultConflict =       0x00800000;  // method (runtime)

// Set by the verifier for a method we do not want the compiler to compile.
static constexpr uint32_t kAccCompileDontBother =     0x01000000;  // method (runtime)

// Set by the verifier for a method that could not be verified to follow structured locking.
static constexpr uint32_t kAccMustCountLocks =        0x02000000;  // method (runtime)

// Set by the class linker for a method that has only one implementation for a
// virtual call.
static constexpr uint32_t kAccSingleImplementation =  0x08000000;  // method (runtime)

static constexpr uint32_t kAccIntrinsic  =            0x80000000;  // method (runtime)

// Special runtime-only flags.
// Interface and all its super-interfaces with default methods have been recursively initialized.
static constexpr uint32_t kAccRecursivelyInitialized    = 0x20000000;
// Interface declares some default method.
static constexpr uint32_t kAccHasDefaultMethod          = 0x40000000;
// class/ancestor overrides finalize()
static constexpr uint32_t kAccClassIsFinalizable        = 0x80000000;

static constexpr uint32_t kAccFlagsNotUsedByIntrinsic   = 0x007FFFFF;
static constexpr uint32_t kAccMaxIntrinsic              = 0xFF;

// Valid (meaningful) bits for a field.
static constexpr uint32_t kAccValidFieldFlags = kAccPublic | kAccPrivate | kAccProtected |
    kAccStatic | kAccFinal | kAccVolatile | kAccTransient | kAccSynthetic | kAccEnum;

// Valid (meaningful) bits for a method.
static constexpr uint32_t kAccValidMethodFlags = kAccPublic | kAccPrivate | kAccProtected |
    kAccStatic | kAccFinal | kAccSynchronized | kAccBridge | kAccVarargs | kAccNative |
    kAccAbstract | kAccStrict | kAccSynthetic | kAccMiranda | kAccConstructor |
    kAccDeclaredSynchronized;

// Valid (meaningful) bits for a class (not interface).
// Note 1. These are positive bits. Other bits may have to be zero.
// Note 2. Inner classes can expose more access flags to Java programs. That is handled by libcore.
static constexpr uint32_t kAccValidClassFlags = kAccPublic | kAccFinal | kAccSuper |
    kAccAbstract | kAccSynthetic | kAccEnum;

// Valid (meaningful) bits for an interface.
// Note 1. Annotations are interfaces.
// Note 2. These are positive bits. Other bits may have to be zero.
// Note 3. Inner classes can expose more access flags to Java programs. That is handled by libcore.
static constexpr uint32_t kAccValidInterfaceFlags = kAccPublic | kAccInterface |
    kAccAbstract | kAccSynthetic | kAccAnnotation;

#endif  // ART_RUNTIME_MODIFIERS_H_