package in.virit.libcamera4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * A zero-copy, mapped view of a captured {@link FrameBuffer}.
 *
 * <p>Each plane of the buffer is exposed as a {@link MemorySegment} backed
 * directly by the {@code mmap}'d native memory — no bytes are copied into the
 * JVM heap. This is the Foreign Function &amp; Memory (Project Panama) counterpart
 * to {@link FrameBuffer#getData()}, which copies the whole frame into a
 * {@code byte[]} on every capture.</p>
 *
 * <p>The mapped memory is owned natively. The segments are valid only until this
 * object is {@linkplain #close() closed}; closing both invalidates the segments
 * (so stray access fails fast instead of reading freed memory) and unmaps the
 * underlying buffer. Always use try-with-resources:</p>
 *
 * <pre>{@code
 * try (MappedFrame frame = buffer.map()) {
 *     MemorySegment y = frame.plane(0);
 *     // ... read pixels directly, e.g. y.get(ValueLayout.JAVA_BYTE, i) ...
 * }
 * }</pre>
 *
 * <p>Requires {@code --enable-native-access}.</p>
 */
public final class MappedFrame implements AutoCloseable {

    private final long mapHandle;
    private final Arena arena;
    private final MemorySegment[] planes;
    private final long totalSize;
    private boolean closed;

    MappedFrame(FrameBufferAllocator allocator, CameraConfiguration configuration,
                int streamIndex, int bufferIndex) {
        this.mapHandle = Native.fbMap(allocator.nativeHandle(),
                configuration.nativeHandle(), streamIndex, bufferIndex);
        if (mapHandle == 0) {
            throw new LibCameraException("Failed to map buffer");
        }

        // A shared arena: the segments may be read from any thread (libcamera
        // completes requests on its own thread). The arena scope governs segment
        // validity; we unmap natively in close() once the scope is shut.
        this.arena = Arena.ofShared();
        try {
            int count = Native.fbPlaneCount(mapHandle);
            this.planes = new MemorySegment[count];
            long total = 0;
            for (int i = 0; i < count; i++) {
                long address = Native.fbPlaneAddress(mapHandle, i);
                long length = Native.fbPlaneLength(mapHandle, i);
                // Wrap the raw native pointer as a bounded, read/writable segment
                // confined to this arena's lifetime. Restricted method: needs
                // --enable-native-access.
                planes[i] = MemorySegment.ofAddress(address).reinterpret(length, arena, null);
                total += length;
            }
            this.totalSize = total;
        } catch (RuntimeException e) {
            // Mapping succeeded but wrapping failed — don't leak the mmap.
            arena.close();
            Native.fbUnmap(mapHandle);
            throw e;
        }
    }

    /**
     * Returns the number of planes in this buffer (1 for packed/single-plane
     * formats such as the Raspberry Pi's default YU12, more for separated planes).
     *
     * @return the plane count
     */
    public int planeCount() {
        return planes.length;
    }

    /**
     * Returns the given plane as a memory segment backed by native memory.
     *
     * @param index the plane index, {@code 0 <= index < }{@link #planeCount()}
     * @return the plane data as a {@link MemorySegment}
     */
    public MemorySegment plane(int index) {
        return planes[index];
    }

    /**
     * Returns the whole buffer as a single contiguous segment.
     *
     * <p>Only valid when the buffer has exactly one plane (the common case for
     * processed YUV output on the Raspberry Pi). For genuinely multi-plane
     * buffers the planes are separate {@code mmap} regions and cannot be presented
     * as one contiguous segment without copying — use {@link #plane(int)} instead.</p>
     *
     * @return the single-plane segment
     * @throws LibCameraException if the buffer has more than one plane
     */
    public MemorySegment contiguous() {
        if (planes.length != 1) {
            throw new LibCameraException("Buffer has " + planes.length
                    + " planes; use plane(int) for multi-plane access");
        }
        return planes[0];
    }

    /**
     * Returns the total mapped size across all planes, in bytes.
     *
     * @return the total size
     */
    public long size() {
        return totalSize;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // Invalidate all segments first so any concurrent access fails fast,
        // then release the native mapping.
        arena.close();
        Native.fbUnmap(mapHandle);
    }
}
