
cmake_minimum_required(VERSION 3.22.1)

project(ats_native_runtime)

add_library(
    ats-native
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    ats-native
    ${log-lib}
)
