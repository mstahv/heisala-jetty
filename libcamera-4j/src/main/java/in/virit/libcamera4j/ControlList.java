package in.virit.libcamera4j;

import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A collection of control values.
 *
 * <p>ControlList is used to set per-request camera parameters and to
 * retrieve metadata from completed requests.</p>
 */
public class ControlList implements Iterable<Map.Entry<ControlId, Object>> {

    private final Map<ControlId, Object> controls = new HashMap<>();
    private MemorySegment nativeHandle;

    /**
     * Creates an empty control list.
     */
    public ControlList() {
    }

    ControlList(MemorySegment nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    /**
     * Returns whether this list is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return controls.isEmpty();
    }

    /**
     * Returns the number of controls in this list.
     *
     * @return the size
     */
    public int size() {
        return controls.size();
    }

    /**
     * Clears all controls from this list.
     */
    public void clear() {
        controls.clear();
    }

    /**
     * Returns whether this list contains a control.
     *
     * @param id the control ID
     * @return true if the control is present
     */
    public boolean contains(ControlId id) {
        return controls.containsKey(id);
    }

    /**
     * Gets a control value.
     *
     * @param id the control ID
     * @return the value, or null if not set
     */
    public Object get(ControlId id) {
        return controls.get(id);
    }

    /**
     * Gets a control value as the specified type.
     *
     * @param <T> the value type
     * @param id the control ID
     * @param type the value class
     * @return the value, or null if not set
     */
    @SuppressWarnings("unchecked")
    public <T> T get(ControlId id, Class<T> type) {
        Object value = controls.get(id);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Sets a control value.
     *
     * @param id the control ID
     * @param value the value
     */
    public void set(ControlId id, Object value) {
        controls.put(id, value);
    }

    /**
     * Sets a boolean control.
     *
     * @param id the control ID
     * @param value the value
     */
    public void set(ControlId id, boolean value) {
        controls.put(id, value);
    }

    /**
     * Sets an integer control.
     *
     * @param id the control ID
     * @param value the value
     */
    public void set(ControlId id, int value) {
        controls.put(id, value);
    }

    /**
     * Sets a long control.
     *
     * @param id the control ID
     * @param value the value
     */
    public void set(ControlId id, long value) {
        controls.put(id, value);
    }

    /**
     * Sets a float control.
     *
     * @param id the control ID
     * @param value the value
     */
    public void set(ControlId id, float value) {
        controls.put(id, value);
    }

    /**
     * Removes a control from this list.
     *
     * @param id the control ID
     * @return the removed value, or null if not present
     */
    public Object remove(ControlId id) {
        return controls.remove(id);
    }

    /**
     * Returns the set of control IDs in this list.
     *
     * @return the control IDs
     */
    public Set<ControlId> ids() {
        return Set.copyOf(controls.keySet());
    }

    /**
     * Merges another control list into this one.
     *
     * @param other the list to merge
     */
    public void merge(ControlList other) {
        controls.putAll(other.controls);
    }

    @Override
    public Iterator<Map.Entry<ControlId, Object>> iterator() {
        return controls.entrySet().iterator();
    }

    @Override
    public String toString() {
        return "ControlList" + controls;
    }
}
