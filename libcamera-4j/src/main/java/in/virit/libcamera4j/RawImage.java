package in.virit.libcamera4j;

/**
 * Represents a raw captured image with metadata.
 *
 * <p>Raw images contain unprocessed pixel data directly from the camera sensor,
 * useful for advanced image editing where maximum quality and flexibility is needed.</p>
 */
public record RawImage(
    byte[] data,
    int width,
    int height,
    int stride,
    PixelFormat format
) {
    /**
     * Returns the suggested file extension for this raw image format.
     *
     * @return file extension (e.g., "raw", "dng")
     */
    public String suggestedExtension() {
        return "raw";
    }

    /**
     * Returns the MIME type for this raw image.
     *
     * @return MIME type
     */
    public String mimeType() {
        return "application/octet-stream";
    }

    /**
     * Returns metadata as a simple text header that can be prepended or saved alongside the raw data.
     *
     * @return metadata string
     */
    public String metadataHeader() {
        return String.format(
            "LIBCAMERA4J RAW\nWidth: %d\nHeight: %d\nStride: %d\nFormat: %s\nDataSize: %d\n",
            width, height, stride, format.fourccString(), data.length
        );
    }
}
