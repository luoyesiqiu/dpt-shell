cmake_minimum_required(VERSION 3.18.1)

project("dpt")

set(DPT_NATIVE_SOURCE
        dpt.cpp
        dpt_hook.cpp
        dpt_jni.cpp
        dpt_util.cpp
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


if(${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    add_definitions(-DDEBUG)
    message(WARNING "!!!WARNNING:opened log!!!")
else()
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")
    message("closed log & stripped so symbols")
endif()


option(MZ_LZMA "Enables LZMA & XZ compression" OFF)
option(MZ_ZSTD "Enables ZSTD compression" OFF)
option(MZ_OPENSSL "Enables OpenSSL for encryption" OFF)

include_directories(./ )
include_directories(./common)
include_directories(./bhook/include)
include_directories(./minizip-ng)

add_library(dpt
             SHARED
        ${DPT_NATIVE_SOURCE})


include_directories(
        dobby
)

add_subdirectory(bhook)
add_subdirectory(minizip-ng)

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