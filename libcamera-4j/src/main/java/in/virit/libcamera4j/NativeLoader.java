package in.virit.libcamera4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Handles loading of the native libcamera4j library.
 */
public final class NativeLoader {

    private static final String LIBRARY_NAME = "camera4j";
    private static volatile boolean loaded = false;
    private static volatile Throwable loadError = null;

    private NativeLoader() {
        // Prevent instantiation
    }

    /**
     * Loads the native library.
     *
     * <p>This method can be called multiple times; the library is only loaded once.</p>
     *
     * @throws UnsatisfiedLinkError if the native library cannot be loaded
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        if (loadError != null) {
            throw new UnsatisfiedLinkError("Native library failed to load previously: " + loadError.getMessage());
        }

        try {
            // First, try loading from java.library.path
            try {
                System.loadLibrary(LIBRARY_NAME);
                loaded = true;
                return;
            } catch (UnsatisfiedLinkError e) {
                // Fall through to try other methods
            }

            // Try loading from classpath (for bundled native libs)
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch").toLowerCase();

            String libName;
            if (osName.contains("linux")) {
                libName = "lib" + LIBRARY_NAME + ".so";
            } else if (osName.contains("mac")) {
                libName = "lib" + LIBRARY_NAME + ".dylib";
            } else if (osName.contains("windows")) {
                libName = LIBRARY_NAME + ".dll";
            } else {
                throw new UnsatisfiedLinkError("Unsupported OS: " + osName);
            }

            // Map architecture names
            String archDir;
            if (osArch.equals("amd64") || osArch.equals("x86_64")) {
                archDir = "x86_64";
            } else if (osArch.equals("aarch64") || osArch.equals("arm64")) {
                archDir = "aarch64";
            } else if (osArch.startsWith("arm")) {
                archDir = "arm";
            } else {
                archDir = osArch;
            }

            String resourcePath = "/native/" + archDir + "/" + libName;

            try (InputStream is = NativeLoader.class.getResourceAsStream(resourcePath)) {
                if (is != null) {
                    // Extract to temp file and load
                    Path tempDir = Files.createTempDirectory("libcamera4j");
                    Path tempLib = tempDir.resolve(libName);
                    Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
                    tempLib.toFile().deleteOnExit();
                    tempDir.toFile().deleteOnExit();

                    System.load(tempLib.toAbsolutePath().toString());
                    loaded = true;
                    return;
                }
            }

            // If we get here, we couldn't find the library
            throw new UnsatisfiedLinkError(
                "Native library '" + LIBRARY_NAME + "' not found. " +
                "Make sure libcamera4j is built and either:\n" +
                "  1. Set java.library.path to include the directory containing lib" + LIBRARY_NAME + ".so\n" +
                "  2. Copy lib" + LIBRARY_NAME + ".so to /usr/lib or /usr/local/lib\n" +
                "  3. Set LD_LIBRARY_PATH to include the library directory\n" +
                "\nTo build: cd libcamera-4j/src/main/native && mkdir build && cd build && cmake .. && make"
            );

        } catch (IOException e) {
            loadError = e;
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            loadError = e;
            throw e;
        }
    }

    /**
     * Returns whether the native library has been loaded.
     *
     * @return true if loaded successfully
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns the error that occurred during loading, if any.
     *
     * @return the load error, or null if no error occurred
     */
    public static Throwable getLoadError() {
        return loadError;
    }
}
