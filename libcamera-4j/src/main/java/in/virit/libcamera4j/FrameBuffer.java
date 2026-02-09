package in.virit.libcamera4j;

/**
 * Represents a buffer for storing captured image data.
 *
 * <p>Frame buffers are allocated by {@link FrameBufferAllocator} and contain
 * captured image data after a request completes.</p>
 */
public class FrameBuffer {

    static {
        NativeLoader.load();
    }

    private final FrameBufferAllocator allocator;
    private final CameraConfiguration configuration;
    private final int streamIndex;
    private final int bufferIndex;
    private long mapHandle;
    private byte[] cachedData;

    /**
     * Creates a frame buffer reference.
     *
     * @param allocator the allocator that owns this buffer
     * @param configuration the camera configuration
     * @param streamIndex the stream index
     * @param bufferIndex the buffer index within the allocator
     */
    public FrameBuffer(FrameBufferAllocator allocator, CameraConfiguration configuration,
                       int streamIndex, int bufferIndex) {
        this.allocator = allocator;
        this.configuration = configuration;
        this.streamIndex = streamIndex;
        this.bufferIndex = bufferIndex;
    }

    /**
     * Maps the buffer and returns its data as a byte array.
     *
     * <p>This copies the buffer data into a Java byte array. For large buffers,
     * consider using {@link #mapAndProcess(BufferProcessor)} instead.</p>
     *
     * @return the buffer data
     */
    public byte[] getData() {
        if (cachedData != null) {
            return cachedData;
        }

        mapHandle = nativeMapBuffer(allocator.nativeHandle(), configuration.nativeHandle(),
                                     streamIndex, bufferIndex);
        if (mapHandle == 0) {
            throw new LibCameraException("Failed to map buffer");
        }

        try {
            int size = nativeGetBufferSize(mapHandle);
            cachedData = new byte[size];
            nativeCopyBuffer(mapHandle, cachedData, 0, size);
            return cachedData;
        } finally {
            nativeUnmapBuffer(mapHandle);
            mapHandle = 0;
        }
    }

    /**
     * Returns the size of the buffer in bytes.
     *
     * @return the buffer size
     */
    public int size() {
        StreamConfiguration streamConfig = configuration.get(streamIndex);
        Size frameSize = streamConfig.size();
        int stride = streamConfig.stride();

        // Estimate based on stride and height
        return stride * frameSize.height();
    }

    /**
     * Returns the stream index this buffer belongs to.
     *
     * @return the stream index
     */
    public int streamIndex() {
        return streamIndex;
    }

    /**
     * Returns the buffer index within the allocator.
     *
     * @return the buffer index
     */
    public int bufferIndex() {
        return bufferIndex;
    }

    /**
     * Clears the cached data to free memory.
     */
    public void clearCache() {
        cachedData = null;
    }

    /**
     * Functional interface for processing buffer data without copying.
     */
    @FunctionalInterface
    public interface BufferProcessor {
        void process(long mapHandle, int size);
    }

    /**
     * Maps the buffer and calls the processor, then unmaps.
     *
     * @param processor the processor to call with the mapped buffer
     */
    public void mapAndProcess(BufferProcessor processor) {
        long handle = nativeMapBuffer(allocator.nativeHandle(), configuration.nativeHandle(),
                                       streamIndex, bufferIndex);
        if (handle == 0) {
            throw new LibCameraException("Failed to map buffer");
        }

        try {
            int size = nativeGetBufferSize(handle);
            processor.process(handle, size);
        } finally {
            nativeUnmapBuffer(handle);
        }
    }

    // Native methods
    private static native long nativeMapBuffer(long allocatorHandle, long configHandle,
                                                int streamIndex, int bufferIndex);
    private static native void nativeUnmapBuffer(long mapHandle);
    private static native int nativeGetBufferSize(long mapHandle);
    private static native void nativeCopyBuffer(long mapHandle, byte[] dest, int offset, int length);
}
