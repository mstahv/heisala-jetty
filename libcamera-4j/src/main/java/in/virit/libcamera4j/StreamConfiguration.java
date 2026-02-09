package in.virit.libcamera4j;

/**
 * Configuration settings for a single camera stream.
 *
 * <p>A StreamConfiguration defines the format, resolution, and buffer settings
 * for a camera stream. It is part of a {@link CameraConfiguration}.</p>
 */
public class StreamConfiguration {

    private final CameraConfiguration parent;
    private final int index;

    StreamConfiguration(CameraConfiguration parent, int index) {
        this.parent = parent;
        this.index = index;
    }

    /**
     * Returns the stream index within the configuration.
     *
     * @return the index
     */
    public int index() {
        return index;
    }

    /**
     * Returns the frame size (width and height).
     *
     * @return the frame size
     */
    public Size size() {
        int width = parent.nativeGetWidth(parent.nativeHandle(), index);
        int height = parent.nativeGetHeight(parent.nativeHandle(), index);
        return new Size(width, height);
    }

    /**
     * Sets the frame size.
     *
     * @param size the frame size
     */
    public void setSize(Size size) {
        parent.nativeSetSize(parent.nativeHandle(), index, size.width(), size.height());
    }

    /**
     * Returns the pixel format.
     *
     * @return the pixel format
     */
    public PixelFormat pixelFormat() {
        int fourcc = parent.nativeGetPixelFormat(parent.nativeHandle(), index);
        return new PixelFormat(fourcc, 0);
    }

    /**
     * Sets the pixel format.
     *
     * @param pixelFormat the pixel format
     */
    public void setPixelFormat(PixelFormat pixelFormat) {
        parent.nativeSetPixelFormat(parent.nativeHandle(), index, pixelFormat.fourcc());
    }

    /**
     * Returns the line stride in bytes.
     *
     * @return the stride in bytes
     */
    public int stride() {
        return parent.nativeGetStride(parent.nativeHandle(), index);
    }

    /**
     * Sets the number of buffers to allocate.
     *
     * @param count the buffer count (typically 2-8)
     */
    public void setBufferCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Buffer count must be at least 1");
        }
        parent.nativeSetBufferCount(parent.nativeHandle(), index, count);
    }

    /**
     * Returns the parent configuration.
     *
     * @return the parent configuration
     */
    public CameraConfiguration configuration() {
        return parent;
    }

    @Override
    public String toString() {
        return "StreamConfiguration[index=" + index + ", size=" + size() +
               ", format=" + pixelFormat() + "]";
    }
}
