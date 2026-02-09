package in.virit.libcamera4j;

/**
 * Represents a 2D size with width and height.
 *
 * @param width the width in pixels
 * @param height the height in pixels
 */
public record Size(int width, int height) {

    /**
     * Creates a size with the given dimensions.
     *
     * @param width the width
     * @param height the height
     * @throws IllegalArgumentException if width or height is negative
     */
    public Size {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size dimensions cannot be negative");
        }
    }

    /**
     * Returns the area (width * height).
     *
     * @return the area in pixels
     */
    public long area() {
        return (long) width * height;
    }

    /**
     * Returns whether this size is empty (zero area).
     *
     * @return true if width or height is zero
     */
    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /**
     * Scales this size by the given factor.
     *
     * @param factor the scale factor
     * @return a new scaled size
     */
    public Size scale(double factor) {
        return new Size((int) (width * factor), (int) (height * factor));
    }

    /**
     * Returns a size that fits within the given bounds while maintaining aspect ratio.
     *
     * @param bounds the bounding size
     * @return a size that fits within bounds
     */
    public Size fitIn(Size bounds) {
        if (isEmpty() || bounds.isEmpty()) {
            return new Size(0, 0);
        }

        double scaleX = (double) bounds.width / width;
        double scaleY = (double) bounds.height / height;
        double scale = Math.min(scaleX, scaleY);

        return new Size((int) (width * scale), (int) (height * scale));
    }

    /**
     * Aligns the size to the given horizontal and vertical alignment.
     *
     * @param hAlign horizontal alignment (must be power of 2)
     * @param vAlign vertical alignment (must be power of 2)
     * @return an aligned size
     */
    public Size alignTo(int hAlign, int vAlign) {
        int alignedWidth = (width + hAlign - 1) & ~(hAlign - 1);
        int alignedHeight = (height + vAlign - 1) & ~(vAlign - 1);
        return new Size(alignedWidth, alignedHeight);
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
