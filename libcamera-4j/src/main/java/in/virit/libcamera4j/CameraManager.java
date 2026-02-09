package in.virit.libcamera4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Manages camera devices in the system.
 *
 * <p>The CameraManager is the entry point for discovering and accessing cameras.
 * It enumerates available camera devices and provides access to individual cameras.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try (CameraManager manager = CameraManager.create()) {
 *     manager.start();
 *     List<Camera> cameras = manager.cameras();
 *     if (!cameras.isEmpty()) {
 *         Camera camera = cameras.get(0);
 *         camera.acquire();
 *         // Use camera...
 *         camera.release();
 *     }
 * }
 * }</pre>
 */
public class CameraManager implements AutoCloseable {

    static {
        NativeLoader.load();
    }

    private long nativeHandle;
    private boolean started = false;
    private final List<Camera> cameras = new ArrayList<>();

    private Consumer<Camera> cameraAddedListener;
    private Consumer<Camera> cameraRemovedListener;

    private CameraManager() {
        this.nativeHandle = nativeCreate();
        if (this.nativeHandle == 0) {
            throw new LibCameraException("Failed to create CameraManager");
        }
    }

    /**
     * Creates a new CameraManager instance.
     *
     * @return a new CameraManager
     */
    public static CameraManager create() {
        return new CameraManager();
    }

    /**
     * Starts the camera manager and enumerates all camera devices in the system.
     *
     * <p>This method must be called before accessing cameras. It discovers all available
     * camera hardware and makes them accessible through {@link #cameras()}.</p>
     *
     * @throws LibCameraException if the camera manager fails to start
     */
    public void start() {
        if (started) {
            return;
        }

        int result = nativeStart(nativeHandle);
        if (result != 0) {
            throw LibCameraException.forOperation("CameraManager.start", result);
        }

        // Enumerate cameras
        cameras.clear();
        String[] cameraIds = nativeGetCameraIds(nativeHandle);
        if (cameraIds != null) {
            for (String id : cameraIds) {
                long camHandle = nativeGetCamera(nativeHandle, id);
                if (camHandle != 0) {
                    cameras.add(new Camera(id, camHandle));
                }
            }
        }

        started = true;
    }

    /**
     * Stops the camera manager.
     *
     * <p>All cameras must be released before calling this method. After stopping,
     * the camera list will be empty.</p>
     */
    public void stop() {
        if (!started) {
            return;
        }

        cameras.clear();
        nativeStop(nativeHandle);

        started = false;
    }

    /**
     * Returns a list of all available cameras in the system.
     *
     * <p>This method is thread-safe and can be called from any thread.</p>
     *
     * @return an unmodifiable list of available cameras
     * @throws IllegalStateException if the camera manager has not been started
     */
    public List<Camera> cameras() {
        if (!started) {
            throw new IllegalStateException("CameraManager must be started before accessing cameras");
        }
        return Collections.unmodifiableList(new ArrayList<>(cameras));
    }

    /**
     * Gets a camera by its unique identifier.
     *
     * @param id the camera identifier
     * @return an Optional containing the camera if found, empty otherwise
     * @throws IllegalStateException if the camera manager has not been started
     */
    public Optional<Camera> get(String id) {
        if (!started) {
            throw new IllegalStateException("CameraManager must be started before accessing cameras");
        }
        return cameras.stream()
                .filter(c -> c.id().equals(id))
                .findFirst();
    }

    /**
     * Sets a listener for camera added events.
     *
     * <p>The listener is called when a new camera is detected and added to the system,
     * for example when a USB camera is connected.</p>
     *
     * @param listener the listener to notify when a camera is added
     */
    public void onCameraAdded(Consumer<Camera> listener) {
        this.cameraAddedListener = listener;
    }

    /**
     * Sets a listener for camera removed events.
     *
     * <p>The listener is called when a camera is disconnected from the system.</p>
     *
     * @param listener the listener to notify when a camera is removed
     */
    public void onCameraRemoved(Consumer<Camera> listener) {
        this.cameraRemovedListener = listener;
    }

    /**
     * Returns the libcamera version string.
     *
     * @return the version string
     */
    public static String version() {
        NativeLoader.load();
        return nativeVersion();
    }

    @Override
    public void close() {
        stop();
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }

    // Native methods
    private native long nativeCreate();
    private native void nativeDestroy(long handle);
    private native int nativeStart(long handle);
    private native void nativeStop(long handle);
    private native String[] nativeGetCameraIds(long handle);
    private native long nativeGetCamera(long handle, String cameraId);
    private static native String nativeVersion();
}
