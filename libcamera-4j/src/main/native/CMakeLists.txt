cmake_minimum_required(VERSION 3.16)
project(libcamera4j VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# No JNI needed: the Java side binds this shim via the Foreign Function & Memory
# API (Project Panama), so we only expose a flat C ABI (see libcamera4j.h).

# Find libcamera using pkg-config
find_package(PkgConfig REQUIRED)
pkg_check_modules(LIBCAMERA REQUIRED libcamera)

message(STATUS "libcamera include dirs: ${LIBCAMERA_INCLUDE_DIRS}")
message(STATUS "libcamera libraries: ${LIBCAMERA_LIBRARIES}")

# Create the shared library
add_library(camera4j SHARED
    libcamera4j.cpp
)

target_include_directories(camera4j PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LIBCAMERA_INCLUDE_DIRS}
)

target_link_libraries(camera4j PRIVATE
    ${LIBCAMERA_LIBRARIES}
)

# Set output name without 'lib' prefix on some platforms
set_target_properties(camera4j PROPERTIES
    OUTPUT_NAME "camera4j"
    PREFIX "lib"
)

# Install target
install(TARGETS camera4j
    LIBRARY DESTINATION lib
    RUNTIME DESTINATION bin
)
