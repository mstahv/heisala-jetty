package in.virit.libcamera4j;

/**
 * Allocates frame buffers for camera streams.
 *
 * <p>The FrameBufferAllocator manages memory allocation for frame buffers.
 * Buffers are allocated per-stream and can be freed when no longer needed.</p>
 */
public class FrameBufferAllocator implements AutoCloseable {

    static {
        NativeLoader.load();
    }

    private final Camera camera;
    private final CameraConfiguration configuration;
    private long nativeHandle;
    private boolean closed = false;
    private int[] allocatedCounts;

    /**
     * Creates a buffer allocator for the given camera.
     *
     * @param camera the camera to allocate buffers for
     */
    public FrameBufferAllocator(Camera camera) {
        this.camera = camera;
        this.configuration = camera.getConfiguration();
        if (configuration == null) {
            throw new IllegalStateException("Camera must be configured before creating allocator");
        }

        this.nativeHandle = nativeCreate(camera.nativeHandle());
        if (this.nativeHandle == 0) {
            throw new LibCameraException("Failed to create FrameBufferAllocator");
        }

        this.allocatedCounts = new int[configuration.size()];
    }

    /**
     * Returns the native handle.
     *
     * @return the native handle
     */
    long nativeHandle() {
        return nativeHandle;
    }

    /**
     * Allocates buffers for a stream.
     *
     * @param streamIndex the stream index to allocate buffers for
     * @return the number of buffers allocated
     * @throws LibCameraException if allocation fails
     */
    public int allocate(int streamIndex) {
        if (closed) {
            throw new IllegalStateException("Allocator is closed");
        }
        if (streamIndex < 0 || streamIndex >= configuration.size()) {
            throw new IndexOutOfBoundsException("Invalid stream index: " + streamIndex);
        }
        if (allocatedCounts[streamIndex] > 0) {
            throw new IllegalStateException("Buffers already allocated for stream " + streamIndex);
        }

        int count = nativeAllocate(nativeHandle, configuration.nativeHandle(), streamIndex);
        if (count < 0) {
            throw LibCameraException.forOperation("FrameBufferAllocator.allocate", count);
        }

        allocatedCounts[streamIndex] = count;
        return count;
    }

    /**
     * Returns the number of buffers allocated for a stream.
     *
     * @param streamIndex the stream index
     * @return the number of buffers, or 0 if none allocated
     */
    public int bufferCount(int streamIndex) {
        if (streamIndex < 0 || streamIndex >= allocatedCounts.length) {
            return 0;
        }
        return allocatedCounts[streamIndex];
    }

    /**
     * Returns the configuration handle.
     *
     * @return the configuration
     */
    public CameraConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
        closed = true;
    }

    // Native methods
    private native long nativeCreate(long cameraHandle);
    private native void nativeDestroy(long handle);
    private native int nativeAllocate(long handle, long configHandle, int streamIndex);
}
