///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Cross-compile libcamera4j native library for Raspberry Pi using Docker.
 *
 * Requirements:
 *   - Docker Desktop with "Use Rosetta for x86_64/amd64 emulation" enabled (for Apple Silicon)
 *   - Or Docker with QEMU for ARM64 emulation (for Intel Macs)
 *
 * Usage:
 *   jbang BuildNative.java
 *
 * Output:
 *   build-output/libcamera4j.so (ARM64 Linux binary for Raspberry Pi)
 */
public class BuildNative {

    private static final String DOCKER_IMAGE = "libcamera4j-builder";

    public static void main(String[] args) throws Exception {
        Path scriptDir = Path.of(System.getProperty("user.dir"));

        // If running from parent directory, cd into libcamera-4j
        if (Files.exists(scriptDir.resolve("libcamera-4j/Dockerfile"))) {
            scriptDir = scriptDir.resolve("libcamera-4j");
        }

        System.out.println("========================================");
        System.out.println("libcamera4j Cross-Compiler for Raspberry Pi");
        System.out.println("========================================");
        System.out.println();

        // Check for Docker
        if (!commandExists("docker")) {
            System.err.println("Error: Docker is not installed");
            System.err.println("Install Docker Desktop from: https://www.docker.com/products/docker-desktop");
            System.exit(1);
        }

        // Check if Docker is running
        if (!isDockerRunning()) {
            System.err.println("Error: Docker is not running");
            System.err.println("Please start Docker Desktop");
            System.exit(1);
        }

        // Create output directory
        Path buildOutput = scriptDir.resolve("build-output");
        Files.createDirectories(buildOutput);

        System.out.println("Building Docker image for ARM64 (this may take a few minutes on first run)...");
        System.out.println();

        // Build the Docker image for ARM64
        int exitCode = runCommand(scriptDir,
            "docker", "build", "--platform", "linux/arm64", "-t", DOCKER_IMAGE, ".");
        if (exitCode != 0) {
            System.err.println("Docker build failed!");
            System.exit(exitCode);
        }

        System.out.println();
        System.out.println("Extracting native library...");

        // Run the container to copy out the library
        exitCode = runCommand(scriptDir,
            "docker", "run", "--rm", "--platform", "linux/arm64",
            "-v", buildOutput.toAbsolutePath() + ":/output",
            DOCKER_IMAGE);
        if (exitCode != 0) {
            System.err.println("Docker run failed!");
            System.exit(exitCode);
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("Build complete!");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Native library: " + buildOutput.resolve("libcamera4j.so"));

        // Copy to resources for JAR embedding
        Path resourcesDir = scriptDir.resolve("src/main/resources/native/aarch64");
        Files.createDirectories(resourcesDir);
        Files.copy(
            buildOutput.resolve("libcamera4j.so"),
            resourcesDir.resolve("libcamera4j.so"),
            StandardCopyOption.REPLACE_EXISTING
        );

        System.out.println();
        System.out.println("Copied to: " + resourcesDir.resolve("libcamera4j.so"));
        System.out.println("(Will be embedded in JAR on next 'mvn package')");
        System.out.println();
        System.out.println("To verify it's an ARM64 binary:");
        System.out.println("  file build-output/libcamera4j.so");
        System.out.println();
        System.out.println("To deploy to Raspberry Pi:");
        System.out.println("  scp build-output/libcamera4j.so pi@raspberrypi:/usr/local/lib/");
        System.out.println("  ssh pi@raspberrypi 'sudo ldconfig'");
        System.out.println();
    }

    private static boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("which", command)
                .redirectErrorStream(true)
                .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isDockerRunning() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int runCommand(Path workDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(workDir.toFile())
            .inheritIO();
        return pb.start().waitFor();
    }
}
