package in.virit.libcamera4j;

/**
 * Represents a 2D point with x and y coordinates.
 *
 * @param x the x coordinate
 * @param y the y coordinate
 */
public record Point(int x, int y) {

    /**
     * The origin point (0, 0).
     */
    public static final Point ORIGIN = new Point(0, 0);

    /**
     * Translates this point by the given offset.
     *
     * @param dx the x offset
     * @param dy the y offset
     * @return a new translated point
     */
    public Point translate(int dx, int dy) {
        return new Point(x + dx, y + dy);
    }

    /**
     * Returns the distance to another point.
     *
     * @param other the other point
     * @return the distance
     */
    public double distanceTo(Point other) {
        int dx = other.x - x;
        int dy = other.y - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
