package in.virit.libcamera4j;

/**
 * Represents a color space for image data.
 *
 * <p>Color spaces define how color values are interpreted, including
 * primaries, transfer function, and YCbCr encoding.</p>
 */
public class ColorSpace {

    /**
     * Color primaries (defines the RGB color space).
     */
    public enum Primaries {
        RAW,
        SMPTE170M,
        REC709,
        REC2020
    }

    /**
     * Transfer function (gamma curve).
     */
    public enum TransferFunction {
        LINEAR,
        SRGB,
        REC709
    }

    /**
     * YCbCr encoding matrix.
     */
    public enum YcbcrEncoding {
        NONE,
        REC601,
        REC709,
        REC2020
    }

    /**
     * Quantization range.
     */
    public enum Range {
        FULL,
        LIMITED
    }

    // Predefined color spaces
    public static final ColorSpace RAW = new ColorSpace(
        Primaries.RAW, TransferFunction.LINEAR, YcbcrEncoding.NONE, Range.FULL);

    public static final ColorSpace SRGB = new ColorSpace(
        Primaries.REC709, TransferFunction.SRGB, YcbcrEncoding.NONE, Range.FULL);

    public static final ColorSpace REC709 = new ColorSpace(
        Primaries.REC709, TransferFunction.REC709, YcbcrEncoding.REC709, Range.LIMITED);

    public static final ColorSpace REC2020 = new ColorSpace(
        Primaries.REC2020, TransferFunction.REC709, YcbcrEncoding.REC2020, Range.LIMITED);

    public static final ColorSpace JPEG = new ColorSpace(
        Primaries.REC709, TransferFunction.SRGB, YcbcrEncoding.REC601, Range.FULL);

    private final Primaries primaries;
    private final TransferFunction transferFunction;
    private final YcbcrEncoding ycbcrEncoding;
    private final Range range;

    /**
     * Creates a color space with the specified parameters.
     *
     * @param primaries the color primaries
     * @param transferFunction the transfer function
     * @param ycbcrEncoding the YCbCr encoding
     * @param range the quantization range
     */
    public ColorSpace(Primaries primaries, TransferFunction transferFunction,
                      YcbcrEncoding ycbcrEncoding, Range range) {
        this.primaries = primaries;
        this.transferFunction = transferFunction;
        this.ycbcrEncoding = ycbcrEncoding;
        this.range = range;
    }

    /**
     * Returns the color primaries.
     *
     * @return the primaries
     */
    public Primaries primaries() {
        return primaries;
    }

    /**
     * Returns the transfer function.
     *
     * @return the transfer function
     */
    public TransferFunction transferFunction() {
        return transferFunction;
    }

    /**
     * Returns the YCbCr encoding.
     *
     * @return the encoding
     */
    public YcbcrEncoding ycbcrEncoding() {
        return ycbcrEncoding;
    }

    /**
     * Returns the quantization range.
     *
     * @return the range
     */
    public Range range() {
        return range;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ColorSpace other)) return false;
        return primaries == other.primaries &&
               transferFunction == other.transferFunction &&
               ycbcrEncoding == other.ycbcrEncoding &&
               range == other.range;
    }

    @Override
    public int hashCode() {
        int result = primaries.hashCode();
        result = 31 * result + transferFunction.hashCode();
        result = 31 * result + ycbcrEncoding.hashCode();
        result = 31 * result + range.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ColorSpace[primaries=" + primaries +
               ", transfer=" + transferFunction +
               ", ycbcr=" + ycbcrEncoding +
               ", range=" + range + "]";
    }
}
