package in.virit.libcamera4j;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

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
     * <p>This copies the buffer data into a Java byte array, concatenating planes
     * sequentially. It is now implemented on top of the zero-copy {@link #map()}
     * path: for streaming/timelapse use, prefer {@link #map()} to avoid the copy.</p>
     *
     * @return the buffer data
     */
    public byte[] getData() {
        if (cachedData != null) {
            return cachedData;
        }

        try (MappedFrame frame = map()) {
            byte[] data = new byte[(int) frame.size()];
            int offset = 0;
            for (int i = 0; i < frame.planeCount(); i++) {
                MemorySegment plane = frame.plane(i);
                int len = (int) plane.byteSize();
                MemorySegment.copy(plane, ValueLayout.JAVA_BYTE, 0, data, offset, len);
                offset += len;
            }
            cachedData = data;
            return cachedData;
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
     * Maps the buffer for zero-copy access via the Foreign Function &amp; Memory API.
     *
     * <p>Unlike {@link #getData()}, this does not copy the pixel data into a Java
     * {@code byte[]}. Instead each plane is exposed as a {@link java.lang.foreign.MemorySegment}
     * backed directly by the {@code mmap}'d native memory. The returned
     * {@link MappedFrame} is {@link AutoCloseable} and must be closed (typically
     * with try-with-resources) to unmap the buffer.</p>
     *
     * <p>Requires the JVM flag {@code --enable-native-access} (already set for the
     * test runner in {@code pom.xml}).</p>
     *
     * <pre>{@code
     * try (MappedFrame frame = buffer.map()) {
     *     BufferedImage img = PixelFormatConverter.convert(
     *         frame.contiguous(), width, height, stride, format);
     * }
     * }</pre>
     *
     * @return a mapped view of the buffer; close it to unmap
     */
    public MappedFrame map() {
        return new MappedFrame(allocator, configuration, streamIndex, bufferIndex);
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
        long handle = Native.fbMap(allocator.nativeHandle(), configuration.nativeHandle(),
                                       streamIndex, bufferIndex);
        if (handle == 0) {
            throw new LibCameraException("Failed to map buffer");
        }

        try {
            int size = Native.fbSize(handle);
            processor.process(handle, size);
        } finally {
            Native.fbUnmap(handle);
        }
    }
}
