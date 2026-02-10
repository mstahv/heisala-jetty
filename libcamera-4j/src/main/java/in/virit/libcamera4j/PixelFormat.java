package in.virit.libcamera4j;

/**
 * Represents a pixel format for image data.
 *
 * <p>Pixel formats define how image data is stored in memory, including
 * color model, bit depth, and memory layout.</p>
 */
public class PixelFormat {

    // Common pixel format FourCC codes
    private static final int FOURCC_RGB888 = fourcc('R', 'G', 'B', '3');
    private static final int FOURCC_BGR888 = fourcc('B', 'G', 'R', '3');
    private static final int FOURCC_XRGB8888 = fourcc('X', 'R', '2', '4');
    private static final int FOURCC_XBGR8888 = fourcc('X', 'B', '2', '4');
    private static final int FOURCC_ARGB8888 = fourcc('A', 'R', '2', '4');
    private static final int FOURCC_ABGR8888 = fourcc('A', 'B', '2', '4');
    private static final int FOURCC_YUYV = fourcc('Y', 'U', 'Y', 'V');
    private static final int FOURCC_YVYU = fourcc('Y', 'V', 'Y', 'U');
    private static final int FOURCC_UYVY = fourcc('U', 'Y', 'V', 'Y');
    private static final int FOURCC_NV12 = fourcc('N', 'V', '1', '2');
    private static final int FOURCC_NV21 = fourcc('N', 'V', '2', '1');
    private static final int FOURCC_YUV420 = fourcc('Y', 'U', '1', '2');
    private static final int FOURCC_MJPEG = fourcc('M', 'J', 'P', 'G');

    // Raw Bayer formats - 10-bit packed
    private static final int FOURCC_SRGGB10 = fourcc('R', 'G', '1', '0');
    private static final int FOURCC_SGRBG10 = fourcc('B', 'A', '1', '0');
    private static final int FOURCC_SBGGR10 = fourcc('B', 'G', '1', '0');
    private static final int FOURCC_SGBRG10 = fourcc('G', 'B', '1', '0');

    // Raw Bayer formats - 12-bit packed
    private static final int FOURCC_SRGGB12 = fourcc('R', 'G', '1', '2');
    private static final int FOURCC_SGRBG12 = fourcc('B', 'A', '1', '2');
    private static final int FOURCC_SBGGR12 = fourcc('B', 'G', '1', '2');
    private static final int FOURCC_SGBRG12 = fourcc('G', 'B', '1', '2');

    // Pre-defined common formats
    public static final PixelFormat RGB888 = new PixelFormat(FOURCC_RGB888, 0);
    public static final PixelFormat BGR888 = new PixelFormat(FOURCC_BGR888, 0);
    public static final PixelFormat XRGB8888 = new PixelFormat(FOURCC_XRGB8888, 0);
    public static final PixelFormat XBGR8888 = new PixelFormat(FOURCC_XBGR8888, 0);
    public static final PixelFormat ARGB8888 = new PixelFormat(FOURCC_ARGB8888, 0);
    public static final PixelFormat ABGR8888 = new PixelFormat(FOURCC_ABGR8888, 0);
    public static final PixelFormat YUYV = new PixelFormat(FOURCC_YUYV, 0);
    public static final PixelFormat YVYU = new PixelFormat(FOURCC_YVYU, 0);
    public static final PixelFormat UYVY = new PixelFormat(FOURCC_UYVY, 0);
    public static final PixelFormat NV12 = new PixelFormat(FOURCC_NV12, 0);
    public static final PixelFormat NV21 = new PixelFormat(FOURCC_NV21, 0);
    public static final PixelFormat YUV420 = new PixelFormat(FOURCC_YUV420, 0);
    public static final PixelFormat MJPEG = new PixelFormat(FOURCC_MJPEG, 0);

    // Raw Bayer formats - 10-bit packed
    public static final PixelFormat SRGGB10 = new PixelFormat(FOURCC_SRGGB10, 0);
    public static final PixelFormat SGRBG10 = new PixelFormat(FOURCC_SGRBG10, 0);
    public static final PixelFormat SBGGR10 = new PixelFormat(FOURCC_SBGGR10, 0);
    public static final PixelFormat SGBRG10 = new PixelFormat(FOURCC_SGBRG10, 0);

    // Raw Bayer formats - 12-bit packed
    public static final PixelFormat SRGGB12 = new PixelFormat(FOURCC_SRGGB12, 0);
    public static final PixelFormat SGRBG12 = new PixelFormat(FOURCC_SGRBG12, 0);
    public static final PixelFormat SBGGR12 = new PixelFormat(FOURCC_SBGGR12, 0);
    public static final PixelFormat SGBRG12 = new PixelFormat(FOURCC_SGBRG12, 0);

    private final int fourcc;
    private final long modifier;

    /**
     * Creates a pixel format from a FourCC code.
     *
     * @param fourcc the FourCC code
     * @param modifier format modifier (typically 0)
     */
    public PixelFormat(int fourcc, long modifier) {
        this.fourcc = fourcc;
        this.modifier = modifier;
    }

    /**
     * Creates a pixel format from a FourCC string.
     *
     * @param fourccString a 4-character format code (e.g., "YUYV")
     * @return the pixel format
     */
    public static PixelFormat fromString(String fourccString) {
        if (fourccString.length() != 4) {
            throw new IllegalArgumentException("FourCC string must be exactly 4 characters");
        }
        int code = fourcc(
            fourccString.charAt(0),
            fourccString.charAt(1),
            fourccString.charAt(2),
            fourccString.charAt(3)
        );
        return new PixelFormat(code, 0);
    }

    /**
     * Returns the FourCC code for this format.
     *
     * @return the FourCC code
     */
    public int fourcc() {
        return fourcc;
    }

    /**
     * Returns the format modifier.
     *
     * @return the modifier
     */
    public long modifier() {
        return modifier;
    }

    /**
     * Returns the FourCC code as a string.
     *
     * @return the FourCC string
     */
    public String fourccString() {
        return String.valueOf(new char[] {
            (char) (fourcc & 0xFF),
            (char) ((fourcc >> 8) & 0xFF),
            (char) ((fourcc >> 16) & 0xFF),
            (char) ((fourcc >> 24) & 0xFF)
        });
    }

    /**
     * Returns whether this format is valid.
     *
     * @return true if the format is valid
     */
    public boolean isValid() {
        return fourcc != 0;
    }

    /**
     * Returns whether this format is a raw Bayer format.
     *
     * @return true if this is a raw Bayer format
     */
    public boolean isRawBayer() {
        return fourcc == FOURCC_SRGGB10 || fourcc == FOURCC_SGRBG10 ||
               fourcc == FOURCC_SBGGR10 || fourcc == FOURCC_SGBRG10 ||
               fourcc == FOURCC_SRGGB12 || fourcc == FOURCC_SGRBG12 ||
               fourcc == FOURCC_SBGGR12 || fourcc == FOURCC_SGBRG12;
    }

    /**
     * Returns the bit depth for raw Bayer formats.
     *
     * @return the bit depth (10, 12) or 0 if not a raw format
     */
    public int getRawBitDepth() {
        if (fourcc == FOURCC_SRGGB10 || fourcc == FOURCC_SGRBG10 ||
            fourcc == FOURCC_SBGGR10 || fourcc == FOURCC_SGBRG10) {
            return 10;
        }
        if (fourcc == FOURCC_SRGGB12 || fourcc == FOURCC_SGRBG12 ||
            fourcc == FOURCC_SBGGR12 || fourcc == FOURCC_SGBRG12) {
            return 12;
        }
        return 0;
    }

    private static int fourcc(char a, char b, char c, char d) {
        return (a & 0xFF) | ((b & 0xFF) << 8) | ((c & 0xFF) << 16) | ((d & 0xFF) << 24);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PixelFormat other)) return false;
        return fourcc == other.fourcc && modifier == other.modifier;
    }

    @Override
    public int hashCode() {
        return 31 * fourcc + Long.hashCode(modifier);
    }

    @Override
    public String toString() {
        return fourccString();
    }
}
