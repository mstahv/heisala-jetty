package in.virit.libcamera4j;

import java.awt.image.BufferedImage;

/**
 * Result of a camera capture operation, containing both image data and metadata.
 *
 * @param jpeg the JPEG-encoded image data (null if raw capture)
 * @param image the BufferedImage (null if raw-only capture)
 * @param rawImage the raw image data (null if JPEG-only capture)
 * @param metadata the capture metadata (EXIF-like data)
 */
public record CaptureResult(
    byte[] jpeg,
    BufferedImage image,
    RawImage rawImage,
    ImageMetadata metadata
) {
    /**
     * Creates a result containing only JPEG data.
     *
     * @param jpeg the JPEG data
     * @param metadata the metadata
     * @return the capture result
     */
    public static CaptureResult ofJpeg(byte[] jpeg, ImageMetadata metadata) {
        return new CaptureResult(jpeg, null, null, metadata);
    }

    /**
     * Creates a result containing a BufferedImage.
     *
     * @param image the image
     * @param metadata the metadata
     * @return the capture result
     */
    public static CaptureResult ofImage(BufferedImage image, ImageMetadata metadata) {
        return new CaptureResult(null, image, null, metadata);
    }

    /**
     * Creates a result containing raw image data.
     *
     * @param rawImage the raw image
     * @param metadata the metadata
     * @return the capture result
     */
    public static CaptureResult ofRaw(RawImage rawImage, ImageMetadata metadata) {
        return new CaptureResult(null, null, rawImage, metadata);
    }

    /**
     * Creates a result containing both JPEG and raw data.
     *
     * @param jpeg the JPEG data
     * @param rawImage the raw image data
     * @param metadata the metadata
     * @return the capture result
     */
    public static CaptureResult ofJpegAndRaw(byte[] jpeg, RawImage rawImage, ImageMetadata metadata) {
        return new CaptureResult(jpeg, null, rawImage, metadata);
    }

    /**
     * Returns true if this result contains JPEG data.
     *
     * @return true if JPEG data is present
     */
    public boolean hasJpeg() {
        return jpeg != null && jpeg.length > 0;
    }

    /**
     * Returns true if this result contains a BufferedImage.
     *
     * @return true if image is present
     */
    public boolean hasImage() {
        return image != null;
    }

    /**
     * Returns true if this result contains raw image data.
     *
     * @return true if raw data is present
     */
    public boolean hasRaw() {
        return rawImage != null;
    }
}
