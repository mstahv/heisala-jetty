package in.virit.libcamera4j;

/**
 * Defines the intended usage of a camera stream.
 *
 * <p>Stream roles are used when generating a camera configuration to indicate
 * the purpose of each stream. The camera will optimize settings based on the role.</p>
 */
public enum StreamRole {

    /**
     * Stream for live viewfinder display.
     * Typically lower resolution for smooth preview.
     */
    VIEWFINDER(0),

    /**
     * Stream for still image capture.
     * Optimized for maximum quality at full resolution.
     */
    STILL_CAPTURE(1),

    /**
     * Stream for video recording.
     * Optimized for consistent frame rate and encoding efficiency.
     */
    VIDEO_RECORDING(2),

    /**
     * Raw stream for advanced processing.
     * Provides unprocessed sensor data.
     */
    RAW(3);

    private final int value;

    StreamRole(int value) {
        this.value = value;
    }

    /**
     * Returns the native value for this role.
     *
     * @return the native enum value
     */
    public int value() {
        return value;
    }

    /**
     * Returns the StreamRole for the given native value.
     *
     * @param value the native value
     * @return the corresponding StreamRole
     * @throws IllegalArgumentException if the value is invalid
     */
    public static StreamRole fromValue(int value) {
        for (StreamRole role : values()) {
            if (role.value == value) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown StreamRole value: " + value);
    }
}
