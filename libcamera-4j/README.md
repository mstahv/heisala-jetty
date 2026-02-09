# libcamera-4j

Java bindings for the [libcamera](https://libcamera.org/) camera stack, designed for Raspberry Pi and other Linux systems.

## Requirements

**On Mac (for building):**
- Docker Desktop
- Maven 3.x
- Java 17+
- [JBang](https://www.jbang.dev/) (install with `curl -Ls https://sh.jbang.dev | bash -s - app setup`)

**On Raspberry Pi (for running):**
- Java 17+
- libcamera runtime (pre-installed on Raspberry Pi OS)

## Quick Start (Build on Mac, Run on Pi)

```bash
# On Mac: Build everything (uses Docker for native library)
jbang BuildForPi.java

# Copy single uber JAR to Pi
scp libcamera-4j-demo/target/libcamera-4j-demo-1.0-SNAPSHOT.jar pi@raspberrypi:~/

# On Pi: Run
java -jar libcamera-4j-demo-1.0-SNAPSHOT.jar
```

The uber JAR contains **everything**: demo app, library, and native code!

## Build Scripts

The build scripts are written in Java and run with JBang:

| Script | Description |
|--------|-------------|
| `jbang BuildForPi.java` | Build everything (native + Java) for Raspberry Pi |
| `jbang libcamera-4j/BuildNative.java` | Build only the native library using Docker |

## What the Demo Does

1. Detects available cameras
2. Captures a 1920x1080 image (with warm-up frames for proper exposure)
3. Saves it as `~/libcamera4j_YYYYMMDD_HHMMSS.jpg`

## Building on Raspberry Pi (Alternative)

If you prefer to build directly on Pi:

```bash
# Install dependencies
sudo apt install libcamera-dev cmake g++ pkg-config openjdk-17-jdk maven

# Build native library
cd libcamera-4j/src/main/native
mkdir build && cd build
cmake ..
make

# Build Java
cd ../../../..
mvn clean package
```

## Usage Example

```java
try (CameraManager manager = CameraManager.create()) {
    manager.start();

    List<Camera> cameras = manager.cameras();
    if (cameras.isEmpty()) {
        System.out.println("No cameras found");
        return;
    }

    Camera camera = cameras.get(0);
    camera.acquire();

    // Configure for still capture
    CameraConfiguration config = camera.generateConfiguration(StreamRole.STILL_CAPTURE);
    StreamConfiguration streamConfig = config.get(0);
    streamConfig.setSize(new Size(1920, 1080));
    config.validate();
    camera.configure(config);

    // Allocate buffers
    try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
        allocator.allocate(0);

        // Create and queue request
        Request request = camera.createRequest();
        request.addBuffer(0, 0, allocator);

        camera.start();
        camera.queueRequest(request);

        // Wait for completion
        while (request.status() == Request.Status.PENDING) {
            Thread.sleep(100);
        }

        camera.stop();

        // Access captured data
        FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
        byte[] imageData = buffer.getData();
        // Process imageData...
    }

    camera.release();
}
```

## Supported Pixel Formats

The demo includes converters for common formats:
- **YU12/I420** - YUV 4:2:0 planar (default on Raspberry Pi)
- **YUYV** - YUV 4:2:2 packed
- **NV12** - YUV 4:2:0 semi-planar
- **RGB24/BGR24** - 24-bit RGB/BGR

## Troubleshooting

### "No cameras found"
- Check camera is connected: `libcamera-hello --list-cameras`
- Check permissions: `sudo usermod -aG video $USER` (then log out/in)
- On Pi, ensure camera is enabled in `raspi-config`

### "Native library not found"
- Rebuild with: `jbang BuildForPi.java`
- The native library is embedded in the JAR and extracted automatically at runtime

### Build errors
- Install dependencies: `sudo apt install libcamera-dev cmake g++ pkg-config`
- Check libcamera version: `pkg-config --modversion libcamera` (requires 0.7+)

## Architecture

```
libcamera-4j/
├── BuildNative.java        # JBang script for native cross-compilation
├── Dockerfile              # ARM64 build environment
├── src/main/java/          # Java API
│   └── in/virit/libcamera4j/
│       ├── CameraManager.java
│       ├── Camera.java
│       ├── CameraConfiguration.java
│       ├── Request.java
│       ├── FrameBuffer.java
│       └── ...
└── src/main/native/        # JNI C++ bindings
    ├── libcamera4j.cpp
    └── CMakeLists.txt

BuildForPi.java             # Main build script (in project root)
```

## License

[Add your license here]
