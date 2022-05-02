cmake_minimum_required(VERSION 3.18.1)
project(bytehook)

enable_language(ASM)

file(GLOB SRC *.c)
add_library(bytehook STATIC ${SRC})
#target_compile_features(bytehook PUBLIC c_std_11)
target_include_directories(bytehook PUBLIC . include third_party/bsd third_party/lss)
target_link_libraries(bytehook log)

if(USEASAN)
    target_compile_options(bytehook PUBLIC -fsanitize=address -fno-omit-frame-pointer)
    target_link_options(bytehook PUBLIC -fsanitize=address)
else()
    target_compile_options(bytehook PUBLIC -O1 -flto -faddrsig -ffunction-sections -fdata-sections)
    target_link_options(bytehook PUBLIC -O1 -flto)
endif()