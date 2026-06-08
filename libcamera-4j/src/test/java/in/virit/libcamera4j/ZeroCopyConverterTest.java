package in.virit.libcamera4j;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Camera-free validation of the zero-copy {@link MemorySegment} conversion path.
 *
 * <p>We cannot get a real {@code mmap}'d {@link FrameBuffer} without camera
 * hardware, but the genuinely new and error-prone code is (a) wrapping a raw
 * native address as a {@link MemorySegment} the way {@link MappedFrame} does, and
 * (b) reading pixels from it in {@link PixelFormatConverter}. This test exercises
 * both by allocating native memory via FFM, reducing it to a bare {@code long}
 * address (exactly what JNI hands back), re-wrapping it, and asserting the
 * zero-copy converter is byte-for-byte identical to the heap {@code byte[]} one.</p>
 *
 * <p>Requires {@code --enable-native-access} (set in the surefire argLine).</p>
 */
class ZeroCopyConverterTest {

    private static final int WIDTH = 8;
    private static final int HEIGHT = 8;
    private static final int STRIDE = 8;

    /** Builds a deterministic, fully-populated YU12/I420 frame. */
    private static byte[] syntheticYu12() {
        int yPlaneSize = STRIDE * HEIGHT;          // 64
        int uvStride = STRIDE / 2;                 // 4
        int uvHeight = HEIGHT / 2;                 // 4
        int total = yPlaneSize + 2 * uvStride * uvHeight; // 64 + 32 = 96
        byte[] data = new byte[total];
        for (int i = 0; i < total; i++) {
            // Spread values across the full 0..255 range to exercise the YUV math.
            data[i] = (byte) ((i * 37 + 11) & 0xFF);
        }
        return data;
    }

    @Test
    void segmentConverterMatchesByteArrayConverter() {
        byte[] frame = syntheticYu12();
        PixelFormat format = PixelFormat.YUV420; // fourcc "YU12"

        BufferedImage expected = PixelFormatConverter.convert(frame, WIDTH, HEIGHT, STRIDE, format);

        try (Arena arena = Arena.ofConfined()) {
            // Allocate off-heap and copy the frame in.
            MemorySegment owned = arena.allocate(frame.length);
            MemorySegment.copy(frame, 0, owned, ValueLayout.JAVA_BYTE, 0, frame.length);

            // Reduce to a bare address + length, then re-wrap exactly like
            // MappedFrame does with a JNI-provided pointer. This is the path
            // that needs --enable-native-access.
            long address = owned.address();
            MemorySegment wrapped = MemorySegment.ofAddress(address).reinterpret(frame.length);

            BufferedImage actual = PixelFormatConverter.convert(wrapped, WIDTH, HEIGHT, STRIDE, format);

            assertEquals(expected.getWidth(), actual.getWidth());
            assertEquals(expected.getHeight(), actual.getHeight());
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    assertEquals(expected.getRGB(x, y), actual.getRGB(x, y),
                            "pixel mismatch at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void segmentReportsCorrectSizeAndBytes() {
        byte[] frame = syntheticYu12();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment owned = arena.allocate(frame.length);
            MemorySegment.copy(frame, 0, owned, ValueLayout.JAVA_BYTE, 0, frame.length);
            MemorySegment wrapped = MemorySegment.ofAddress(owned.address()).reinterpret(frame.length);

            assertEquals(frame.length, wrapped.byteSize());
            for (int i = 0; i < frame.length; i++) {
                assertEquals(frame[i], wrapped.get(ValueLayout.JAVA_BYTE, i), "byte mismatch at " + i);
            }
        }
    }
}
