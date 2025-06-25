cmake_minimum_required(VERSION 3.31.1)

project("dpt")

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

set(MZ_LZMA OFF CACHE BOOL "" FORCE)
set(MZ_ZSTD OFF CACHE BOOL "" FORCE)
set(MZ_OPENSSL OFF CACHE BOOL "" FORCE)

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

include_directories(./ )
include_directories(./common)

add_library(dpt SHARED ${DPT_NATIVE_SOURCE})

find_library(log-lib log)
find_library(android-lib android)

target_compile_options(dpt PRIVATE
        -Wall
        -Wextra
        -Werror
)
target_link_options(dpt PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(dpt
            ${log-lib}
            MINIZIP::minizip
            dobby
            bytehook
            ${android-lib}
        )

ENABLE_LANGUAGE(ASM)