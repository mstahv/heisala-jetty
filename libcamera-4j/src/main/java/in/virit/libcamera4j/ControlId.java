package in.virit.libcamera4j;

/**
 * Identifies a camera control.
 *
 * <p>Control IDs are used to get and set camera parameters like brightness,
 * contrast, exposure, etc.</p>
 */
public class ControlId {

    // Common control IDs matching libcamera controls
    public static final ControlId AE_ENABLE = new ControlId(1, "AeEnable", ControlType.BOOL);
    public static final ControlId AE_METERING_MODE = new ControlId(2, "AeMeteringMode", ControlType.INT32);
    public static final ControlId AE_CONSTRAINT_MODE = new ControlId(3, "AeConstraintMode", ControlType.INT32);
    public static final ControlId AE_EXPOSURE_MODE = new ControlId(4, "AeExposureMode", ControlType.INT32);
    public static final ControlId EXPOSURE_VALUE = new ControlId(5, "ExposureValue", ControlType.FLOAT);
    public static final ControlId EXPOSURE_TIME = new ControlId(6, "ExposureTime", ControlType.INT32);
    public static final ControlId ANALOGUE_GAIN = new ControlId(7, "AnalogueGain", ControlType.FLOAT);
    public static final ControlId BRIGHTNESS = new ControlId(8, "Brightness", ControlType.FLOAT);
    public static final ControlId CONTRAST = new ControlId(9, "Contrast", ControlType.FLOAT);
    public static final ControlId SATURATION = new ControlId(10, "Saturation", ControlType.FLOAT);
    public static final ControlId SHARPNESS = new ControlId(11, "Sharpness", ControlType.FLOAT);
    public static final ControlId AWB_ENABLE = new ControlId(12, "AwbEnable", ControlType.BOOL);
    public static final ControlId AWB_MODE = new ControlId(13, "AwbMode", ControlType.INT32);
    public static final ControlId COLOUR_GAINS = new ControlId(14, "ColourGains", ControlType.FLOAT);
    public static final ControlId COLOUR_TEMPERATURE = new ControlId(15, "ColourTemperature", ControlType.INT32);
    public static final ControlId NOISE_REDUCTION_MODE = new ControlId(16, "NoiseReductionMode", ControlType.INT32);
    public static final ControlId AF_MODE = new ControlId(17, "AfMode", ControlType.INT32);
    public static final ControlId AF_RANGE = new ControlId(18, "AfRange", ControlType.INT32);
    public static final ControlId AF_SPEED = new ControlId(19, "AfSpeed", ControlType.INT32);
    public static final ControlId AF_TRIGGER = new ControlId(20, "AfTrigger", ControlType.INT32);
    public static final ControlId LENS_POSITION = new ControlId(21, "LensPosition", ControlType.FLOAT);
    public static final ControlId FRAME_DURATION_LIMITS = new ControlId(22, "FrameDurationLimits", ControlType.INT64);
    public static final ControlId SCALER_CROP = new ControlId(23, "ScalerCrop", ControlType.RECTANGLE);

    /**
     * Control value types.
     */
    public enum ControlType {
        NONE,
        BOOL,
        BYTE,
        INT32,
        INT64,
        FLOAT,
        STRING,
        RECTANGLE,
        SIZE
    }

    private final int id;
    private final String name;
    private final ControlType type;

    /**
     * Creates a control ID.
     *
     * @param id the numeric ID
     * @param name the control name
     * @param type the value type
     */
    public ControlId(int id, String name, ControlType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    /**
     * Returns the numeric ID.
     *
     * @return the ID
     */
    public int id() {
        return id;
    }

    /**
     * Returns the control name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the value type.
     *
     * @return the type
     */
    public ControlType type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ControlId other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return name + "(" + id + ")";
    }
}
