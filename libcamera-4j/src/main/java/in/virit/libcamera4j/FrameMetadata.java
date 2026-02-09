package in.virit.libcamera4j;

import java.util.List;

/**
 * Metadata associated with a captured frame.
 *
 * <p>FrameMetadata provides timing information and per-plane byte counts
 * for captured frames.</p>
 */
public class FrameMetadata {

    /**
     * Frame capture status.
     */
    public enum Status {
        /**
         * Frame was captured successfully.
         */
        SUCCESS,

        /**
         * Frame capture failed.
         */
        ERROR,

        /**
         * Frame was cancelled.
         */
        CANCELLED
    }

    /**
     * Metadata for a single plane.
     */
    public record PlaneMetadata(
        /**
         * Number of bytes used in this plane.
         */
        int bytesUsed
    ) {}

    private final Status status;
    private final long sequence;
    private final long timestamp;
    private final List<PlaneMetadata> planes;

    /**
     * Creates frame metadata.
     *
     * @param status the capture status
     * @param sequence the frame sequence number
     * @param timestamp the capture timestamp in nanoseconds
     * @param planes per-plane metadata
     */
    public FrameMetadata(Status status, long sequence, long timestamp, List<PlaneMetadata> planes) {
        this.status = status;
        this.sequence = sequence;
        this.timestamp = timestamp;
        this.planes = List.copyOf(planes);
    }

    /**
     * Returns the capture status.
     *
     * @return the status
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the frame sequence number.
     *
     * <p>The sequence number increments for each captured frame.</p>
     *
     * @return the sequence number
     */
    public long sequence() {
        return sequence;
    }

    /**
     * Returns the capture timestamp.
     *
     * <p>The timestamp is in nanoseconds from an unspecified epoch,
     * suitable for measuring frame intervals.</p>
     *
     * @return the timestamp in nanoseconds
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Returns the per-plane metadata.
     *
     * @return the plane metadata list
     */
    public List<PlaneMetadata> planes() {
        return planes;
    }

    @Override
    public String toString() {
        return "FrameMetadata[status=" + status + ", sequence=" + sequence +
               ", timestamp=" + timestamp + "]";
    }
}
