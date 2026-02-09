package in.virit.libcamera4j;

import java.util.List;

/**
 * Provides information about a camera control's valid values.
 *
 * <p>ControlInfo describes the constraints on a control's value, including
 * minimum, maximum, and default values, or a list of valid discrete values.</p>
 */
public class ControlInfo {

    private final ControlId id;
    private final Object min;
    private final Object max;
    private final Object defaultValue;
    private final List<Object> values;

    /**
     * Creates control info with a value range.
     *
     * @param id the control ID
     * @param min the minimum value
     * @param max the maximum value
     * @param defaultValue the default value
     */
    public ControlInfo(ControlId id, Object min, Object max, Object defaultValue) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.values = null;
    }

    /**
     * Creates control info with discrete values.
     *
     * @param id the control ID
     * @param values the list of valid values
     * @param defaultValue the default value
     */
    public ControlInfo(ControlId id, List<Object> values, Object defaultValue) {
        this.id = id;
        this.min = null;
        this.max = null;
        this.defaultValue = defaultValue;
        this.values = List.copyOf(values);
    }

    /**
     * Returns the control ID.
     *
     * @return the ID
     */
    public ControlId id() {
        return id;
    }

    /**
     * Returns the minimum value, if this control uses a range.
     *
     * @return the minimum value, or null for discrete values
     */
    public Object min() {
        return min;
    }

    /**
     * Returns the maximum value, if this control uses a range.
     *
     * @return the maximum value, or null for discrete values
     */
    public Object max() {
        return max;
    }

    /**
     * Returns the default value.
     *
     * @return the default value
     */
    public Object defaultValue() {
        return defaultValue;
    }

    /**
     * Returns the list of valid discrete values.
     *
     * @return the valid values, or null if this control uses a range
     */
    public List<Object> values() {
        return values;
    }

    /**
     * Returns whether this control uses discrete values.
     *
     * @return true if discrete, false if range
     */
    public boolean isDiscrete() {
        return values != null;
    }

    @Override
    public String toString() {
        if (isDiscrete()) {
            return "ControlInfo[" + id.name() + ", values=" + values +
                   ", default=" + defaultValue + "]";
        } else {
            return "ControlInfo[" + id.name() + ", min=" + min + ", max=" + max +
                   ", default=" + defaultValue + "]";
        }
    }
}
