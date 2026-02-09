# Dockerfile for cross-compiling libcamera4j native library for Raspberry Pi
#
# Build with:
#   docker build --platform linux/arm64 -t libcamera4j-builder .
#
# Run to extract the library:
#   docker run --rm -v $(pwd)/build-output:/output libcamera4j-builder
#

FROM --platform=linux/arm64 debian:trixie

# Install base tools
RUN apt-get update && apt-get install -y \
    curl \
    build-essential \
    cmake \
    pkg-config \
    default-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

# Using trusted=yes to bypass SHA1 signature rejection in trixie
# Install libcamera 0.7 from Raspberry Pi repo
RUN echo "deb [trusted=yes] http://archive.raspberrypi.com/debian/ trixie main" > /etc/apt/sources.list.d/raspi.list \
    && apt-get update \
    && apt-get install -y libcamera-dev \
    && echo "Installed libcamera version:" \
    && dpkg -l | grep libcamera \
    && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/default-java

# Copy native source files
WORKDIR /build
COPY src/main/native/CMakeLists.txt ./
COPY src/main/native/libcamera4j.cpp ./

# Build the native library
RUN mkdir -p /build/output && \
    cmake -DCMAKE_BUILD_TYPE=Release . && \
    make -j$(nproc) && \
    cp libcamera4j.so /build/output/

# Default command: copy library to mounted volume
CMD ["sh", "-c", "cp /build/output/libcamera4j.so /output/ && echo 'Library copied to /output/libcamera4j.so' && ls -la /output/"]
