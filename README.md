# Heisala Jetty Server Project

This project is not Jetty web server :-) This is for a Raspberry Pi server
to be placed on my jetty (dock/pier). Plan is to at least make it take beautiful sunset
pictures and timelapses, and measure water level as an input for an another
"very important" project called Catch-A-Fish.

Let's see what else comes up when the customer (that's me) falls into feature creep...

## Tech Stack

- **Java 25** - Because Raspberry Pi benefits from the latest JVM improvements
- **Quarkus** - Supersonic Subatomic Java framework
- **Vaadin 25** - Full-stack web framework for the UI
- **libcamera** - Linux camera stack for Raspberry Pi cameras
- **JBang** - For build scripts (because we're Java devs, not bash devs)

## Modules

| Module | Description |
|--------|-------------|
| [libcamera-4j](libcamera-4j/) | Java bindings for libcamera (JNI) |
| [libcamera-4j-demo](libcamera-4j-demo/) | Demo app for testing camera capture |
| [heisala-jetty-server](heisala-jetty-server/) | Main Quarkus/Vaadin web application |

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.x
- Docker Desktop (for cross-compiling native libraries)
- [JBang](https://www.jbang.dev/)

### Build Everything

```bash
jbang BuildForPi.java
```

This builds the native camera library using Docker (ARM64) and packages everything into deployable JARs.

### Deploy to Raspberry Pi

```bash
# Copy the demo JAR
scp libcamera-4j-demo/target/libcamera-4j-demo-1.0-SNAPSHOT.jar pi@raspberrypi:~/

# Run on Pi
ssh pi@raspberrypi 'java -jar libcamera-4j-demo-1.0-SNAPSHOT.jar'
```

## Hardware

- Raspberry Pi Zero 2 W
- Raspberry Pi Camera Module 3
- (Future) Water level sensor

## Project Status

- [x] libcamera Java bindings
- [x] Cross-compilation from Mac to Pi
- [x] Single uber JAR deployment
- [x] Still image capture with auto-exposure
- [ ] Timelapse functionality
- [ ] Water level measurement
- [ ] Sunset detection
- [ ] Web UI for monitoring and control
