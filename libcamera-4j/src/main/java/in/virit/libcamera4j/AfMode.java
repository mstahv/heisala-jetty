package in.virit.libcamera4j;

/**
 * Autofocus mode for camera capture.
 */
public enum AfMode {

    /**
     * Manual focus mode.
     * The lens position is controlled by {@link Request#setLensPosition(float)}.
     */
    MANUAL(0),

    /**
     * Single-shot autofocus.
     * The camera focuses once when capture starts.
     */
    AUTO(1),

    /**
     * Continuous autofocus.
     * The camera continuously adjusts focus during capture.
     */
    CONTINUOUS(2);

    private final int value;

    AfMode(int value) {
        this.value = value;
    }

    /**
     * Returns the native value for this mode.
     *
     * @return the native value
     */
    public int value() {
        return value;
    }

    /**
     * Returns the AfMode for the given native value.
     *
     * @param value the native value
     * @return the corresponding AfMode
     */
    public static AfMode fromValue(int value) {
        for (AfMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown AfMode value: " + value);
    }
}
