cmake_minimum_required(VERSION 3.22.1)
project(occompanion C)

set(GEN ${CMAKE_CURRENT_SOURCE_DIR}/generated)
if(NOT EXISTS ${GEN}/companion.c OR NOT EXISTS ${GEN}/hrt.h)
    message(FATAL_ERROR "Missing generated Helium C. Run tools/build-apk.sh from the repo root.")
endif()

add_library(companion SHARED ${GEN}/companion.c companion_jni.c)
target_include_directories(companion PRIVATE ${GEN})
target_compile_definitions(companion PRIVATE main=companion_unused_main)
target_compile_options(companion PRIVATE -w -O2 -fwrapv)
target_link_libraries(companion android log)
