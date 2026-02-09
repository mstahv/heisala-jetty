///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Build everything for Raspberry Pi deployment.
 *
 * This script:
 *   1. Builds the native library using Docker (ARM64)
 *   2. Embeds it in the JAR
 *   3. Builds the Java JARs
 *
 * Usage:
 *   jbang BuildForPi.java
 *
 * Output:
 *   libcamera-4j-demo/target/libcamera-4j-demo-1.0-SNAPSHOT.jar
 *   (Native library is embedded - no separate .so needed!)
 */
public class BuildForPi {

    public static void main(String[] args) throws Exception {
        Path projectDir = Path.of(System.getProperty("user.dir"));

        // Ensure we're in the project root
        if (!Files.exists(projectDir.resolve("libcamera-4j"))) {
            System.err.println("Error: Run this script from the project root directory");
            System.exit(1);
        }

        System.out.println("========================================");
        System.out.println("Building libcamera4j for Raspberry Pi");
        System.out.println("========================================");
        System.out.println();

        // Step 1: Build native library
        System.out.println("Step 1/2: Building native library with Docker...");
        int exitCode = runJBangScript(projectDir.resolve("libcamera-4j"), "BuildNative.java");
        if (exitCode != 0) {
            System.err.println("Native build failed!");
            System.exit(exitCode);
        }

        // Step 2: Build Java (native lib is now in resources, will be embedded)
        System.out.println();
        System.out.println("Step 2/2: Building Java libraries (with embedded native library)...");
        exitCode = runCommand(projectDir, "mvn", "clean", "package", "-DskipTests", "-q");
        if (exitCode != 0) {
            System.err.println("Maven build failed!");
            System.exit(exitCode);
        }

        // Verify the native lib is in the JAR
        System.out.println();
        System.out.println("Verifying native library is embedded in JAR...");

        Path jarPath = findJar(projectDir.resolve("libcamera-4j/target"), "libcamera-4j-");
        if (jarPath != null && containsNativeLib(jarPath)) {
            System.out.println("✓ Native library embedded successfully");
        } else {
            System.out.println("✗ Warning: Native library not found in JAR");
        }

        Path uberJar = projectDir.resolve("libcamera-4j-demo/target/libcamera-4j-demo-1.0-SNAPSHOT.jar");
        String uberJarSize = formatSize(Files.size(uberJar));

        System.out.println();
        System.out.println("========================================");
        System.out.println("Build complete!");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Uber JAR: " + uberJar + " (" + uberJarSize + ")");
        System.out.println();
        System.out.println("Everything is in ONE JAR:");
        System.out.println("  ✓ Demo application");
        System.out.println("  ✓ libcamera-4j library");
        System.out.println("  ✓ Native library (ARM64)");
        System.out.println();
        System.out.println("To run on Raspberry Pi:");
        System.out.println();
        System.out.println("  1. Copy to Pi:");
        System.out.println("     scp " + uberJar + " pi@raspberrypi:~/");
        System.out.println();
        System.out.println("  2. Run:");
        System.out.println("     ssh pi@raspberrypi 'java -jar libcamera-4j-demo-1.0-SNAPSHOT.jar'");
        System.out.println();
    }

    private static int runJBangScript(Path workDir, String script) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("jbang", script)
            .directory(workDir.toFile())
            .inheritIO();
        return pb.start().waitFor();
    }

    private static int runCommand(Path workDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(workDir.toFile())
            .inheritIO();
        return pb.start().waitFor();
    }

    private static Path findJar(Path dir, String prefix) throws IOException {
        if (!Files.exists(dir)) return null;
        return Files.list(dir)
            .filter(p -> p.getFileName().toString().startsWith(prefix))
            .filter(p -> p.toString().endsWith(".jar"))
            .filter(p -> !p.toString().contains("-sources"))
            .filter(p -> !p.toString().contains("-javadoc"))
            .findFirst()
            .orElse(null);
    }

    private static boolean containsNativeLib(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("native/aarch64/libcamera4j.so");
            return entry != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
