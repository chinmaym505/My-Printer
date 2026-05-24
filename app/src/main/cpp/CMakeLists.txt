cmake_minimum_required(VERSION 3.22.1)
project("myprinter_slicer")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    slicer_jni
    SHARED
    slicer_jni.cpp
    stl_parser.cpp
    slicer.cpp
    gcode_writer.cpp
)

target_link_libraries(
    slicer_jni
    android
    log
)
