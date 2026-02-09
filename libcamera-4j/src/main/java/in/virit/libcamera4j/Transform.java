package in.virit.libcamera4j;

/**
 * Represents an image transformation (rotation and/or flip).
 *
 * <p>Transforms are applied to the captured image data before it is written
 * to the frame buffer. They can be used to correct for camera orientation
 * or flip the image.</p>
 */
public enum Transform {

    /**
     * No transformation (identity).
     */
    IDENTITY(0),

    /**
     * Horizontal flip (mirror).
     */
    HFLIP(1),

    /**
     * Vertical flip.
     */
    VFLIP(2),

    /**
     * Horizontal and vertical flip (180° rotation).
     */
    HVFLIP(3),

    /**
     * 90° clockwise rotation.
     */
    ROT90(4),

    /**
     * 90° clockwise rotation + horizontal flip.
     */
    ROT90_HFLIP(5),

    /**
     * 90° clockwise rotation + vertical flip (270° clockwise).
     */
    ROT90_VFLIP(6),

    /**
     * 90° clockwise rotation + horizontal and vertical flip.
     */
    ROT90_HVFLIP(7),

    /**
     * 180° rotation.
     */
    ROT180(3),  // Same as HVFLIP

    /**
     * 270° clockwise rotation (90° counter-clockwise).
     */
    ROT270(6);  // Same as ROT90_VFLIP

    private final int value;

    Transform(int value) {
        this.value = value;
    }

    /**
     * Returns the native value for this transform.
     *
     * @return the native value
     */
    public int value() {
        return value;
    }

    /**
     * Returns whether this transform includes horizontal flip.
     *
     * @return true if horizontally flipped
     */
    public boolean hflip() {
        return (value & 1) != 0;
    }

    /**
     * Returns whether this transform includes vertical flip.
     *
     * @return true if vertically flipped
     */
    public boolean vflip() {
        return (value & 2) != 0;
    }

    /**
     * Returns whether this transform includes 90° rotation.
     *
     * @return true if rotated 90°
     */
    public boolean transpose() {
        return (value & 4) != 0;
    }

    /**
     * Composes this transform with another.
     *
     * @param other the transform to apply after this one
     * @return the composed transform
     */
    public Transform compose(Transform other) {
        int result = this.value ^ other.value;
        return fromValue(result);
    }

    /**
     * Returns the inverse of this transform.
     *
     * @return the inverse transform
     */
    public Transform inverse() {
        // For these transforms, the inverse is the same as the transform
        // (they are all self-inverse)
        return this;
    }

    /**
     * Returns the Transform for the given native value.
     *
     * @param value the native value
     * @return the corresponding Transform
     */
    public static Transform fromValue(int value) {
        for (Transform t : values()) {
            if (t.value == value) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown Transform value: " + value);
    }

    /**
     * Creates a transform from rotation angle (in degrees, clockwise).
     *
     * @param degrees the rotation angle (must be 0, 90, 180, or 270)
     * @return the transform
     */
    public static Transform fromRotation(int degrees) {
        return switch (degrees % 360) {
            case 0 -> IDENTITY;
            case 90 -> ROT90;
            case 180 -> ROT180;
            case 270 -> ROT270;
            default -> throw new IllegalArgumentException(
                "Rotation must be 0, 90, 180, or 270 degrees");
        };
    }
}
