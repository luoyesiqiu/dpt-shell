cmake_minimum_required(VERSION 3.31.1)

project("dpt")

include(FetchContent)

FetchContent_Declare(
        minizip_ng
        GIT_REPOSITORY https://github.com/zlib-ng/minizip-ng.git
        GIT_TAG 4.0.7
)
set(MZ_LZMA OFF CACHE BOOL "" FORCE)
set(MZ_ZSTD OFF CACHE BOOL "" FORCE)
set(MZ_OPENSSL OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(minizip_ng)

set(patch_book git apply ${CMAKE_CURRENT_SOURCE_DIR}/patches/bhook_add_static_lib.patch)
FetchContent_Declare(
        bhook
        GIT_REPOSITORY https://github.com/bytedance/bhook.git
        GIT_TAG v1.0.5
        PATCH_COMMAND ${patch_book}
        SOURCE_SUBDIR bytehook/src/main/cpp
)
FetchContent_MakeAvailable(bhook)


set(DPT_NATIVE_SOURCE
        dpt.cpp
        dpt_hook.cpp
        dpt_jni.cpp
        dpt_util.cpp
        dpt_risk.cpp
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

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Werror")

add_definitions(-DDECRYPT_BITCODE)

if(${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    add_definitions(-DDEBUG)
    message(WARNING "!!!WARNNING:opened log!!!")
else()
    set(OPTIMIZATION_LEVEL "")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden ${OPTIMIZATION_LEVEL}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden ${OPTIMIZATION_LEVEL}")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--exclude-libs,ALL")
    message("closed log & stripped so symbols")
endif()

include_directories(./ )
include_directories(./common)

add_library(dpt
             SHARED
        ${DPT_NATIVE_SOURCE})


include_directories(
        dobby
)

add_library(local_dobby STATIC IMPORTED)
set_target_properties(local_dobby PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/${ANDROID_ABI}/libdobby.a)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries( dpt
            ${log-lib}
        MINIZIP::minizip
        local_dobby
        bytehook
        ${android-lib}
        )


ENABLE_LANGUAGE(ASM)