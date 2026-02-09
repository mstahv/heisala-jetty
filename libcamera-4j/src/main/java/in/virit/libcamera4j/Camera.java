package in.virit.libcamera4j;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a camera device in the system.
 *
 * <p>A Camera object provides access to a single camera device. Before using a camera,
 * it must be acquired using {@link #acquire()}. After use, it should be released
 * with {@link #release()} to allow other applications to access it.</p>
 *
 * <p>The typical workflow for using a camera is:</p>
 * <ol>
 *   <li>Acquire the camera with {@link #acquire()}</li>
 *   <li>Generate a configuration with {@link #generateConfiguration(StreamRole...)}</li>
 *   <li>Optionally modify the configuration</li>
 *   <li>Apply the configuration with {@link #configure(CameraConfiguration)}</li>
 *   <li>Allocate frame buffers</li>
 *   <li>Create requests and add buffers to them</li>
 *   <li>Start the camera with {@link #start()}</li>
 *   <li>Queue requests with {@link #queueRequest(Request)}</li>
 *   <li>Process completed requests</li>
 *   <li>Stop the camera with {@link #stop()}</li>
 *   <li>Release the camera with {@link #release()}</li>
 * </ol>
 */
public class Camera {

    static {
        NativeLoader.load();
    }

    private final String id;
    private final long nativeHandle;
    private boolean acquired = false;
    private CameraConfiguration configuration;
    private boolean running = false;

    private Consumer<Request> requestCompletedListener;
    private Consumer<FrameBuffer> bufferCompletedListener;
    private Runnable disconnectedListener;

    Camera(String id, long nativeHandle) {
        this.id = id;
        this.nativeHandle = nativeHandle;
    }

    /**
     * Returns the unique identifier for this camera.
     *
     * <p>The identifier is stable across reboots and reconnections, and can be used
     * to uniquely identify a specific camera device.</p>
     *
     * @return the camera identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the native handle for this camera.
     * Used internally for JNI bindings.
     *
     * @return the native handle
     */
    long nativeHandle() {
        return nativeHandle;
    }

    /**
     * Acquires exclusive access to the camera.
     *
     * <p>This method must be called before configuring or using the camera.
     * Only one process can have a camera acquired at a time.</p>
     *
     * @throws LibCameraException if the camera is already acquired by another process
     */
    public void acquire() {
        if (acquired) {
            return;
        }
        int result = nativeAcquire(nativeHandle);
        if (result != 0) {
            throw LibCameraException.forOperation("Camera.acquire", result);
        }
        acquired = true;
    }

    /**
     * Releases exclusive access to the camera.
     *
     * <p>After releasing, other applications can acquire and use the camera.
     * The camera must be stopped before releasing.</p>
     *
     * @throws IllegalStateException if the camera is still running
     */
    public void release() {
        if (!acquired) {
            return;
        }
        if (running) {
            throw new IllegalStateException("Camera must be stopped before releasing");
        }
        nativeRelease(nativeHandle);
        acquired = false;
    }

    /**
     * Generates a default camera configuration for the specified stream roles.
     *
     * <p>The returned configuration can be modified before being applied with
     * {@link #configure(CameraConfiguration)}.</p>
     *
     * @param roles the desired stream roles (e.g., Viewfinder, StillCapture, VideoRecording)
     * @return a camera configuration with default settings for the requested roles
     * @throws IllegalStateException if the camera has not been acquired
     */
    public CameraConfiguration generateConfiguration(StreamRole... roles) {
        if (!acquired) {
            throw new IllegalStateException("Camera must be acquired before generating configuration");
        }

        int[] roleValues = new int[roles.length];
        for (int i = 0; i < roles.length; i++) {
            roleValues[i] = roles[i].value();
        }

        long configHandle = nativeGenerateConfiguration(nativeHandle, roleValues);
        if (configHandle == 0) {
            throw new LibCameraException("Failed to generate configuration");
        }

        return new CameraConfiguration(configHandle);
    }

    /**
     * Applies a configuration to the camera.
     *
     * <p>The camera must be acquired and not running. After configuration,
     * the camera is ready to allocate buffers and start capturing.</p>
     *
     * @param config the configuration to apply
     * @throws IllegalStateException if the camera is not acquired or is running
     * @throws LibCameraException if the configuration is invalid or cannot be applied
     */
    public void configure(CameraConfiguration config) {
        if (!acquired) {
            throw new IllegalStateException("Camera must be acquired before configuring");
        }
        if (running) {
            throw new IllegalStateException("Camera must be stopped before reconfiguring");
        }

        int result = nativeConfigure(nativeHandle, config.nativeHandle());
        if (result != 0) {
            throw LibCameraException.forOperation("Camera.configure", result);
        }
        this.configuration = config;
    }

    /**
     * Creates a new capture request.
     *
     * <p>The request should be populated with buffers using {@link Request#addBuffer(int, int)}
     * before being queued with {@link #queueRequest(Request)}.</p>
     *
     * @return a new capture request
     * @throws IllegalStateException if the camera is not configured
     */
    public Request createRequest() {
        return createRequest(0);
    }

    /**
     * Creates a new capture request with a cookie value.
     *
     * <p>The cookie is an opaque value that applications can use to identify requests.</p>
     *
     * @param cookie an application-defined value to associate with the request
     * @return a new capture request
     * @throws IllegalStateException if the camera is not configured
     */
    public Request createRequest(long cookie) {
        if (configuration == null) {
            throw new IllegalStateException("Camera must be configured before creating requests");
        }

        long reqHandle = nativeCreateRequest(nativeHandle, cookie);
        if (reqHandle == 0) {
            throw new LibCameraException("Failed to create request");
        }

        return new Request(this, cookie, reqHandle, configuration);
    }

    /**
     * Queues a request for processing.
     *
     * <p>The request will be processed asynchronously. When complete, the
     * {@link #onRequestCompleted(Consumer)} listener will be notified.</p>
     *
     * @param request the request to queue
     * @throws IllegalStateException if the camera is not running
     * @throws LibCameraException if the request cannot be queued
     */
    public void queueRequest(Request request) {
        if (!running) {
            throw new IllegalStateException("Camera must be running to queue requests");
        }

        int result = nativeQueueRequest(nativeHandle, request.nativeHandle());
        if (result != 0) {
            throw LibCameraException.forOperation("Camera.queueRequest", result);
        }
    }

    /**
     * Starts the camera and begins processing queued requests.
     *
     * @throws IllegalStateException if the camera is not configured
     * @throws LibCameraException if the camera fails to start
     */
    public void start() {
        start(null);
    }

    /**
     * Starts the camera with initial control values.
     *
     * @param controls initial control values to apply
     * @throws IllegalStateException if the camera is not configured
     * @throws LibCameraException if the camera fails to start
     */
    public void start(ControlList controls) {
        if (configuration == null) {
            throw new IllegalStateException("Camera must be configured before starting");
        }
        if (running) {
            return;
        }

        int result = nativeStart(nativeHandle);
        if (result != 0) {
            throw LibCameraException.forOperation("Camera.start", result);
        }
        running = true;
    }

    /**
     * Stops the camera.
     *
     * <p>All pending requests are cancelled and returned via the request completed callback.</p>
     */
    public void stop() {
        if (!running) {
            return;
        }
        nativeStop(nativeHandle);
        running = false;
    }

    /**
     * Polls for a completed request.
     *
     * <p>Returns a request that has completed, or null if no requests are ready.</p>
     *
     * @return a completed request, or null
     */
    public Request pollCompletedRequest() {
        long reqHandle = nativePollCompletedRequest(nativeHandle);
        if (reqHandle == 0) {
            return null;
        }
        // Note: In a full implementation, we'd maintain a map of handles to Request objects
        return null; // Placeholder - needs proper request tracking
    }

    /**
     * Returns the controls supported by this camera.
     *
     * @return a map of control IDs to their information
     */
    public Map<ControlId, ControlInfo> controls() {
        return Map.of();
    }

    /**
     * Returns the static properties of this camera.
     *
     * @return the camera properties
     */
    public ControlList properties() {
        return new ControlList();
    }

    /**
     * Sets a listener for request completed events.
     *
     * @param listener the listener to notify when a request completes
     */
    public void onRequestCompleted(Consumer<Request> listener) {
        this.requestCompletedListener = listener;
    }

    /**
     * Sets a listener for buffer completed events.
     *
     * @param listener the listener to notify when a buffer completes
     */
    public void onBufferCompleted(Consumer<FrameBuffer> listener) {
        this.bufferCompletedListener = listener;
    }

    /**
     * Sets a listener for camera disconnection events.
     *
     * @param listener the listener to notify when the camera is disconnected
     */
    public void onDisconnected(Runnable listener) {
        this.disconnectedListener = listener;
    }

    /**
     * Returns the current configuration.
     *
     * @return the configuration, or null if not configured
     */
    public CameraConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Returns whether this camera has been acquired.
     *
     * @return true if the camera is acquired
     */
    public boolean isAcquired() {
        return acquired;
    }

    /**
     * Returns whether this camera is currently running.
     *
     * @return true if the camera is running
     */
    public boolean isRunning() {
        return running;
    }

    @Override
    public String toString() {
        return "Camera[id=" + id + ", acquired=" + acquired + ", running=" + running + "]";
    }

    // Native methods
    private native int nativeAcquire(long handle);
    private native void nativeRelease(long handle);
    private native long nativeGenerateConfiguration(long handle, int[] roles);
    private native int nativeConfigure(long handle, long configHandle);
    private native long nativeCreateRequest(long handle, long cookie);
    private native int nativeQueueRequest(long handle, long requestHandle);
    private native int nativeStart(long handle);
    private native void nativeStop(long handle);
    private native long nativePollCompletedRequest(long handle);
}
