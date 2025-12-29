cmake_minimum_required(VERSION 3.16)
project(macac_native VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find JNI
find_package(JNI REQUIRED)

# Compiler flags
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    add_compile_options(-Wall -Wextra -O3 -march=native)
    
    # Enable AVX2 if supported
    include(CheckCXXCompilerFlag)
    check_cxx_compiler_flag("-mavx2" COMPILER_SUPPORTS_AVX2)
    if(COMPILER_SUPPORTS_AVX2)
        add_compile_options(-mavx2)
    endif()
endif()

# Source files
set(SOURCES
    src/timing.cpp
    src/ringbuffer.cpp
    src/stats.cpp
    src/network.cpp
    src/combat.cpp
    src/jni_bridge.cpp
)

# Include directories
include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${JNI_INCLUDE_DIRS}
)

# Build shared library
add_library(macac_native SHARED ${SOURCES})

# Link libraries
target_link_libraries(macac_native
    pthread
)

# Set output name based on platform
if(WIN32)
    set_target_properties(macac_native PROPERTIES
        PREFIX ""
        OUTPUT_NAME "macac_native"
        SUFFIX ".dll"
    )
elseif(APPLE)
    set_target_properties(macac_native PROPERTIES
        PREFIX "lib"
        OUTPUT_NAME "macac_native"
        SUFFIX ".dylib"
    )
else()
    set_target_properties(macac_native PROPERTIES
        PREFIX "lib"
        OUTPUT_NAME "macac_native"
        SUFFIX ".so"
    )
endif()

# Install
install(TARGETS macac_native
    LIBRARY DESTINATION lib
    RUNTIME DESTINATION bin
)

install(FILES include/macac_native.h
    DESTINATION include
)

# Print build info
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ compiler: ${CMAKE_CXX_COMPILER}")
message(STATUS "JNI include: ${JNI_INCLUDE_DIRS}")
