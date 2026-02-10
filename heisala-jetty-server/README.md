# heisala-jetty-server

Heisala Jetty monitoring system - a Raspberry Pi-based camera monitoring application with timelapse generation.

Built with Vaadin 25 and Quarkus for a Raspberry Pi deployment.

## Features

### Camera Monitoring
- **Live camera view** - displays the latest captured photo from the jetty
- **Automatic photo capture** - captures photos every 15 seconds using libcamera4j
- **Refresh button** - manually refresh the displayed photo
- **Camera settings** - configurable rotation, focus mode, and exposure settings

### Timelapse Generation
Generate timelapse videos from captured images with comprehensive configuration options:

- **Date/time range selection** - select start and end dates for the timelapse
- **FPS control** - choose video playback speed (10, 15, 24, 30 fps)
- **Image sampling** - use all images or sample at intervals (1 per minute, 5 min, 15 min, 1 hour)
- **Video resolution** - Original, 1080p, 720p, or 480p
- **Video quality** - High (8 Mbps), Medium (4 Mbps), or Low (2 Mbps) bitrate
- **Hardware acceleration** - uses h264_v4l2m2m encoder on Raspberry Pi for efficient encoding
- **Real-time progress** - live FFmpeg output, progress bar with ETA
- **Cancel support** - abort long-running video generation
- **Video management** - list, download, and delete previously generated videos

### System Monitoring
Real-time system resource monitoring widget showing:
- CPU usage percentage with progress bar
- Memory usage (used/total MB)
- Video generation progress with file size and ETA

### Image Retention
- **Automatic cleanup** - removes images older than 7 days every Sunday at 3:30 AM
- **Help view** - shows disk usage estimates for different sampling rates

## Requirements

### Camera
The application uses libcamera4j for camera access. Camera features are automatically disabled if the camera hardware is not available.

### FFmpeg
For timelapse video generation, FFmpeg must be installed on the system:

```shell script
sudo apt install ffmpeg
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/heisala-jetty-server-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
