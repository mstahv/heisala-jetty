cmake_minimum_required(VERSION 3.16)
project(libcamera4j VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find JNI - only need headers, not AWT
find_package(Java COMPONENTS Development)
find_path(JNI_INCLUDE_DIR jni.h
    HINTS
        $ENV{JAVA_HOME}/include
        /usr/lib/jvm/default-java/include
        /usr/lib/jvm/java-21-openjdk-arm64/include
        /usr/lib/jvm/java-17-openjdk-arm64/include
        /usr/lib/jvm/java-17-openjdk-amd64/include
)
find_path(JNI_INCLUDE_DIR_PLATFORM jni_md.h
    HINTS
        ${JNI_INCLUDE_DIR}/linux
        $ENV{JAVA_HOME}/include/linux
)

if(NOT JNI_INCLUDE_DIR)
    message(FATAL_ERROR "Could not find jni.h. Set JAVA_HOME or install JDK.")
endif()

set(JNI_INCLUDE_DIRS ${JNI_INCLUDE_DIR} ${JNI_INCLUDE_DIR_PLATFORM})
message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")

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
    ${JNI_INCLUDE_DIRS}
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
