cmake_minimum_required(VERSION 3.31.1)

project("dpt")

if(DEFINED DPT_PROFILE_CMAKE_DIR AND NOT DPT_PROFILE_BUILD)
    message(FATAL_ERROR "DPT_PROFILE_CMAKE_DIR requires DPT_PROFILE_BUILD=1")
endif()

if(DPT_PROFILE_BUILD)
    if(NOT DEFINED DPT_PROFILE_CMAKE_DIR OR DPT_PROFILE_CMAKE_DIR STREQUAL "")
        message(FATAL_ERROR "DPT_PROFILE_CMAKE_DIR is required when DPT_PROFILE_BUILD=1")
    endif()
    if(NOT EXISTS "${DPT_PROFILE_CMAKE_DIR}/profile-parameters.cmake"
            OR NOT EXISTS "${DPT_PROFILE_CMAKE_DIR}/profile-sources.cmake")
        message(FATAL_ERROR "generated Profile CMake contract is incomplete: ${DPT_PROFILE_CMAKE_DIR}")
    endif()
    include("${DPT_PROFILE_CMAKE_DIR}/profile-parameters.cmake")
    include("${DPT_PROFILE_CMAKE_DIR}/profile-sources.cmake")
    if(NOT DPT_PROFILE_ABI STREQUAL "arm64-v8a")
        message(FATAL_ERROR "Profile ABI must be arm64-v8a: ${DPT_PROFILE_ABI}")
    endif()
    if(NOT DEFINED CMAKE_ANDROID_ARCH_ABI
            OR NOT CMAKE_ANDROID_ARCH_ABI STREQUAL DPT_PROFILE_ABI)
        message(FATAL_ERROR "Profile CMake ABI must match DPT_PROFILE_ABI: expected=${DPT_PROFILE_ABI} actual=${CMAKE_ANDROID_ARCH_ABI}")
    endif()
    if(NOT DEFINED DPT_PROFILE_SO_FILE OR NOT DEFINED DPT_PROFILE_SO_TARGET
            OR NOT DEFINED DPT_PROFILE_BUILD_KEY OR NOT DEFINED DPT_PROFILE_JNI_BRIDGE
            OR NOT DPT_PROFILE_GENERATED_INCLUDE_DIR OR NOT DPT_PROFILE_GENERATED_SOURCES)
        message(FATAL_ERROR "generated Profile CMake contract is incomplete")
    endif()
    if(NOT DPT_PROFILE_SO_FILE STREQUAL "lib${DPT_PROFILE_SO_TARGET}.so")
        message(FATAL_ERROR "Profile SO file and target do not match")
    endif()
    if(NOT IS_DIRECTORY "${DPT_PROFILE_GENERATED_INCLUDE_DIR}")
        message(FATAL_ERROR "Profile generated include directory is missing")
    endif()
    foreach(DPT_PROFILE_GENERATED_SOURCE IN LISTS DPT_PROFILE_GENERATED_SOURCES)
        if(NOT EXISTS "${DPT_PROFILE_GENERATED_SOURCE}")
            message(FATAL_ERROR "Profile generated source is missing: ${DPT_PROFILE_GENERATED_SOURCE}")
        endif()
    endforeach()
    set(SO_NAME "${DPT_PROFILE_SO_TARGET}")
    set(DPT_BUILD_KEY "${DPT_PROFILE_BUILD_KEY}")
    set(JNI_BRIDGE_NAME "${DPT_PROFILE_JNI_BRIDGE}")
else()
    set(DPT_PROFILE_BUILD 0)
endif()

set(DPT_NATIVE_SOURCE
        dpt.cpp
        dpt_hook.cpp
        dpt_jni.cpp
        dpt_util.cpp
        dpt_risk.cpp
        dpt_crypto.cpp
        rc4/rc4.c
        common/dpt_string.c
        dex/dex_file.cpp
        dex/MultiDexCode.cpp
        dex/CodeItem.cpp
        reflect/dalvik_system_BaseDexClassLoader.cpp
        reflect/dalvik_system_DexPathList.cpp
        reflect/java_io_File.cpp
        reflect/java_util_ArrayList.cpp
        reflect/java_lang_Class.cpp
        reflect/android_app_ActivityThread.cpp
        reflect/android_content_pm_ApplicationInfo.cpp
        reflect/android_app_Application.cpp
        reflect/android_app_LoadedApk.cpp
        reflect/android_app_ContextImpl.cpp
        )

if(DPT_PROFILE_BUILD)
    list(APPEND DPT_NATIVE_SOURCE ${DPT_PROFILE_GENERATED_SOURCES})
endif()

set(MZ_LZMA OFF CACHE BOOL "" FORCE)
set(MZ_ZSTD OFF CACHE BOOL "" FORCE)
set(MZ_OPENSSL OFF CACHE BOOL "" FORCE)

set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)

add_subdirectory(external/mbedtls)
include_directories(external/mbedtls/include)

file(GLOB BHOOK_SOURCE external/bhook/bytehook/src/main/cpp/*.c)
add_library(bytehook STATIC ${BHOOK_SOURCE})
target_include_directories(bytehook PUBLIC
        external/bhook/bytehook/src/main/cpp
        external/bhook/bytehook/src/main/cpp/include
        external/bhook/bytehook/src/main/cpp/third_party/bsd
        external/bhook/bytehook/src/main/cpp/third_party/lss)
target_link_libraries(bytehook log)

add_subdirectory(external/minizip-ng)

if(NOT TARGET dobby)
    set(DOBBY_DIR external/Dobby)
    macro(SET_OPTION option value)
        set(${option} ${value} CACHE INTERNAL "" FORCE)
    endmacro()
    SET_OPTION(DOBBY_DEBUG OFF)
    SET_OPTION(DOBBY_GENERATE_SHARED OFF)
    add_definitions(-DLOGGING_DISABLE)
    add_subdirectory(${DOBBY_DIR} dobby)
    get_property(DOBBY_INCLUDE_DIRECTORIES
            TARGET dobby
            PROPERTY INCLUDE_DIRECTORIES)
    include_directories(
            .
            ${DOBBY_INCLUDE_DIRECTORIES}
            $<TARGET_PROPERTY:dobby,INCLUDE_DIRECTORIES>
    )
endif()


add_definitions(-DDECRYPT_BITCODE)

if(${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    add_definitions(-DDEBUG)
    message(WARNING "!!!WARNNING:opened log!!!")
else()
    set(OPTIMIZATION_LEVEL "-Oz")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden ${OPTIMIZATION_LEVEL}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden ${OPTIMIZATION_LEVEL}")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--exclude-libs,ALL")
    message("closed log & stripped so symbols")
endif()

message("so name: ${SO_NAME}")

if(NOT DEFINED DPT_BUILD_KEY OR DPT_BUILD_KEY STREQUAL "")
    message(FATAL_ERROR "DPT_BUILD_KEY is required")
endif()
message("dpt build key: ${DPT_BUILD_KEY}")

add_compile_definitions(DPT_PROFILE_BUILD=${DPT_PROFILE_BUILD})
add_compile_definitions(JNI_BRIDGE_NAME="${JNI_BRIDGE_NAME}")
add_compile_definitions(SO_NAME="lib${SO_NAME}.so")
add_compile_definitions(DPT_BUILD_KEY="${DPT_BUILD_KEY}")

include_directories(./ )
include_directories(./common)
if(DPT_PROFILE_BUILD)
    include_directories("${DPT_PROFILE_GENERATED_INCLUDE_DIR}")
endif()

add_library(${SO_NAME} SHARED ${DPT_NATIVE_SOURCE})

find_library(log-lib log)
find_library(android-lib android)

target_compile_options(${SO_NAME} PRIVATE
        -Wall
        -Wextra
        -Werror
)
target_link_options(${SO_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(${SO_NAME}
            ${log-lib}
            MINIZIP::minizip
            dobby
            bytehook
            ${android-lib}
            MbedTLS::mbedcrypto
        )

ENABLE_LANGUAGE(ASM)
