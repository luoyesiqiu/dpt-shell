cmake_minimum_required(VERSION 3.10.2)

project("dpt")

set(DPT_NATIVE_SOURCE
        dpt.cpp
        dpt_hook.cpp
        dex/MultiDexCode.cpp
        dex/CodeItem.cpp
        dex/ClassDataItemReader.cpp
        JniWrapper.cpp
        dpt_util.cpp
        )

include_directories(./ )


# 添加库
add_library(dpt
             SHARED
        ${DPT_NATIVE_SOURCE})


include_directories(
        dobby
)

# 使用预编译库
add_library(local_dobby STATIC IMPORTED)

set_target_properties(local_dobby PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/${ANDROID_ABI}/libdobby.a)

# 寻找已存在的库
find_library(log-lib log)
find_library(z-lib z)
find_library(android-lib android)

target_link_libraries( dpt
            ${log-lib}
        local_dobby
        ${android-lib}
        )


ENABLE_LANGUAGE(ASM)