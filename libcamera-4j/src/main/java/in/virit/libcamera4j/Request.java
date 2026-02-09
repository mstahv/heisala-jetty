package in.virit.libcamera4j;

/**
 * Represents a capture request to be processed by the camera.
 *
 * <p>A Request contains one or more buffers (one per stream) and optional control
 * values. When the request completes, the buffers contain captured image data.</p>
 */
public class Request {

    static {
        NativeLoader.load();
    }

    /**
     * Request completion status.
     */
    public enum Status {
        PENDING(0),
        COMPLETE(1),
        CANCELLED(2);

        private final int value;

        Status(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Status fromValue(int value) {
            for (Status s : values()) {
                if (s.value == value) {
                    return s;
                }
            }
            return PENDING;
        }
    }

    private final Camera camera;
    private final long cookie;
    private long nativeHandle;
    private final CameraConfiguration configuration;
    private FrameBufferAllocator allocator;
    private int[] bufferIndices;

    Request(Camera camera, long cookie, long nativeHandle, CameraConfiguration configuration) {
        this.camera = camera;
        this.cookie = cookie;
        this.nativeHandle = nativeHandle;
        this.configuration = configuration;
        this.bufferIndices = new int[configuration.size()];
        for (int i = 0; i < bufferIndices.length; i++) {
            bufferIndices[i] = -1;
        }
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
     * Returns the cookie value associated with this request.
     *
     * @return the cookie
     */
    public long cookie() {
        return cookie;
    }

    /**
     * Adds a buffer for a stream to this request.
     *
     * @param streamIndex the stream index
     * @param bufferIndex the buffer index from the allocator
     * @param allocator the allocator that owns the buffer
     */
    public void addBuffer(int streamIndex, int bufferIndex, FrameBufferAllocator allocator) {
        if (bufferIndices[streamIndex] >= 0) {
            throw new IllegalStateException("Buffer already added for stream " + streamIndex);
        }

        int result = nativeAddBuffer(nativeHandle, configuration.nativeHandle(),
                                      streamIndex, allocator.nativeHandle(), bufferIndex);
        if (result != 0) {
            throw LibCameraException.forOperation("Request.addBuffer", result);
        }

        this.allocator = allocator;
        bufferIndices[streamIndex] = bufferIndex;
    }

    /**
     * Returns the buffer index for a stream.
     *
     * @param streamIndex the stream index
     * @return the buffer index, or -1 if no buffer assigned
     */
    public int getBufferIndex(int streamIndex) {
        if (streamIndex < 0 || streamIndex >= bufferIndices.length) {
            return -1;
        }
        return bufferIndices[streamIndex];
    }

    /**
     * Returns the allocator used for this request's buffers.
     *
     * @return the allocator
     */
    public FrameBufferAllocator getAllocator() {
        return allocator;
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public CameraConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Returns the completion status of this request.
     *
     * @return the status
     */
    public Status status() {
        int value = nativeStatus(nativeHandle);
        return Status.fromValue(value);
    }

    /**
     * Reuses this request, keeping buffers attached.
     */
    public void reuse() {
        nativeReuse(nativeHandle);
    }

    /**
     * Gets the frame timestamp for a buffer.
     *
     * @param streamIndex the stream index
     * @return the timestamp in nanoseconds
     */
    public long getTimestamp(int streamIndex) {
        int bufferIndex = bufferIndices[streamIndex];
        if (bufferIndex < 0 || allocator == null) {
            return 0;
        }
        return nativeGetTimestamp(nativeHandle, configuration.nativeHandle(),
                                   streamIndex, allocator.nativeHandle(), bufferIndex);
    }

    /**
     * Gets the frame sequence number for a buffer.
     *
     * @param streamIndex the stream index
     * @return the sequence number
     */
    public long getSequence(int streamIndex) {
        int bufferIndex = bufferIndices[streamIndex];
        if (bufferIndex < 0 || allocator == null) {
            return 0;
        }
        return nativeGetSequence(nativeHandle, configuration.nativeHandle(),
                                  streamIndex, allocator.nativeHandle(), bufferIndex);
    }

    @Override
    public String toString() {
        return "Request[cookie=" + cookie + ", status=" + status() + "]";
    }

    // Native methods
    private native int nativeAddBuffer(long handle, long configHandle, int streamIndex,
                                        long allocatorHandle, int bufferIndex);
    private native int nativeReuse(long handle);
    private native int nativeStatus(long handle);
    private native long nativeGetTimestamp(long handle, long configHandle, int streamIndex,
                                            long allocatorHandle, int bufferIndex);
    private native long nativeGetSequence(long handle, long configHandle, int streamIndex,
                                           long allocatorHandle, int bufferIndex);
}
