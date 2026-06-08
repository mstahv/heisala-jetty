package in.virit.libcamera4j;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves and loads the native {@code libcamera4j} shared library for the
 * Foreign Function &amp; Memory API.
 *
 * <p>Unlike the former JNI loader (which called {@link System#load}), this
 * produces a {@link SymbolLookup} that the {@link Native} binding layer uses to
 * resolve the {@code lc4j_*} C entry points. The library is loaded into the
 * {@linkplain Arena#global() global arena} and stays loaded for the JVM lifetime.</p>
 */
public final class NativeLoader {

    private static final String LIBRARY_NAME = "camera4j";
    private static volatile SymbolLookup lookup = null;
    private static volatile boolean loaded = false;
    private static volatile Throwable loadError = null;

    private NativeLoader() {
        // Prevent instantiation
    }

    /**
     * Ensures the native library is loaded. Retained for backward compatibility;
     * delegates to {@link #lookup()}.
     *
     * @throws UnsatisfiedLinkError if the native library cannot be loaded
     */
    public static synchronized void load() {
        lookup();
    }

    /**
     * Resolves a {@link SymbolLookup} for the native library, loading it on first
     * call. Tries the system library path first (for libraries installed via
     * {@code ldconfig}/{@code LD_LIBRARY_PATH}/{@code java.library.path}), then
     * falls back to extracting the architecture-specific library bundled on the
     * classpath.
     *
     * @return the symbol lookup for {@code libcamera4j}
     * @throws UnsatisfiedLinkError if the native library cannot be loaded
     */
    static synchronized SymbolLookup lookup() {
        if (lookup != null) {
            return lookup;
        }
        if (loadError != null) {
            throw new UnsatisfiedLinkError("Native library failed to load previously: " + loadError.getMessage());
        }

        Arena arena = Arena.global();

        // 1. Try the OS loader search path (handles system-installed libcamera4j).
        try {
            lookup = SymbolLookup.libraryLookup(System.mapLibraryName(LIBRARY_NAME), arena);
            loaded = true;
            return lookup;
        } catch (IllegalArgumentException e) {
            // Not on the system path — fall through to the bundled copy.
        }

        try {
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
                    Path tempDir = Files.createTempDirectory("libcamera4j");
                    Path tempLib = tempDir.resolve(libName);
                    Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);
                    tempLib.toFile().deleteOnExit();
                    tempDir.toFile().deleteOnExit();

                    lookup = SymbolLookup.libraryLookup(tempLib, arena);
                    loaded = true;
                    return lookup;
                }
            }

            throw new UnsatisfiedLinkError(
                "Native library '" + LIBRARY_NAME + "' not found. Make sure libcamera4j is built and either:\n" +
                "  1. Install lib" + LIBRARY_NAME + ".so on the loader path (LD_LIBRARY_PATH / ldconfig), or\n" +
                "  2. Bundle it on the classpath under " + resourcePath + "\n" +
                "\nTo build: jbang BuildNative.java");

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
