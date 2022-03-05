cmake_minimum_required(VERSION 3.10.2)
project(shadowhook)

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(ARCH "arm64")
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(ARCH "arm")
endif()

file(GLOB SRC *.c)
add_library(zip STATIC ${SRC})
target_link_libraries(zip z)
