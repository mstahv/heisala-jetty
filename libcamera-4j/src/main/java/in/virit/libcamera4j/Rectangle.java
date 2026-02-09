package in.virit.libcamera4j;

/**
 * Represents a rectangle with position and size.
 *
 * @param x the x coordinate of the top-left corner
 * @param y the y coordinate of the top-left corner
 * @param width the width
 * @param height the height
 */
public record Rectangle(int x, int y, int width, int height) {

    /**
     * Creates a rectangle from a point and size.
     *
     * @param topLeft the top-left corner
     * @param size the size
     * @return a new rectangle
     */
    public static Rectangle of(Point topLeft, Size size) {
        return new Rectangle(topLeft.x(), topLeft.y(), size.width(), size.height());
    }

    /**
     * Creates a rectangle from a size, positioned at the origin.
     *
     * @param size the size
     * @return a new rectangle at (0, 0)
     */
    public static Rectangle of(Size size) {
        return new Rectangle(0, 0, size.width(), size.height());
    }

    /**
     * Returns the top-left corner point.
     *
     * @return the top-left point
     */
    public Point topLeft() {
        return new Point(x, y);
    }

    /**
     * Returns the size of this rectangle.
     *
     * @return the size
     */
    public Size size() {
        return new Size(width, height);
    }

    /**
     * Returns the area of this rectangle.
     *
     * @return the area
     */
    public long area() {
        return (long) width * height;
    }

    /**
     * Returns whether this rectangle is empty (zero area).
     *
     * @return true if width or height is zero
     */
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    /**
     * Returns whether this rectangle contains the given point.
     *
     * @param point the point to test
     * @return true if the point is inside this rectangle
     */
    public boolean contains(Point point) {
        return point.x() >= x && point.x() < x + width &&
               point.y() >= y && point.y() < y + height;
    }

    /**
     * Returns the intersection of this rectangle with another.
     *
     * @param other the other rectangle
     * @return the intersection, or an empty rectangle if they don't overlap
     */
    public Rectangle intersection(Rectangle other) {
        int left = Math.max(x, other.x);
        int top = Math.max(y, other.y);
        int right = Math.min(x + width, other.x + other.width);
        int bottom = Math.min(y + height, other.y + other.height);

        if (right <= left || bottom <= top) {
            return new Rectangle(0, 0, 0, 0);
        }
        return new Rectangle(left, top, right - left, bottom - top);
    }

    /**
     * Translates this rectangle by the given offset.
     *
     * @param dx the x offset
     * @param dy the y offset
     * @return a new translated rectangle
     */
    public Rectangle translate(int dx, int dy) {
        return new Rectangle(x + dx, y + dy, width, height);
    }

    /**
     * Scales this rectangle by the given factor.
     *
     * @param factor the scale factor
     * @return a new scaled rectangle
     */
    public Rectangle scale(double factor) {
        return new Rectangle(
            (int) (x * factor),
            (int) (y * factor),
            (int) (width * factor),
            (int) (height * factor)
        );
    }

    /**
     * Returns a rectangle centered within this rectangle with the given size.
     *
     * @param size the size of the centered rectangle
     * @return a centered rectangle
     */
    public Rectangle center(Size size) {
        int cx = x + (width - size.width()) / 2;
        int cy = y + (height - size.height()) / 2;
        return new Rectangle(cx, cy, size.width(), size.height());
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")/" + width + "x" + height;
    }
}
