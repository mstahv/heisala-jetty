package in.virit.libcamera4j;

/**
 * Represents a capture request to be processed by the camera.
 *
 * <p>A Request contains one or more buffers (one per stream) and optional control
 * values. When the request completes, the buffers contain captured image data.</p>
 */
public class Request {

    static {
        NativeLoader.load();
    }

    /**
     * Request completion status.
     */
    public enum Status {
        PENDING(0),
        COMPLETE(1),
        CANCELLED(2);

        private final int value;

        Status(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static Status fromValue(int value) {
            for (Status s : values()) {
                if (s.value == value) {
                    return s;
                }
            }
            return PENDING;
        }
    }

    private final Camera camera;
    private final long cookie;
    private long nativeHandle;
    private final CameraConfiguration configuration;
    private FrameBufferAllocator allocator;
    private int[] bufferIndices;

    Request(Camera camera, long cookie, long nativeHandle, CameraConfiguration configuration) {
        this.camera = camera;
        this.cookie = cookie;
        this.nativeHandle = nativeHandle;
        this.configuration = configuration;
        this.bufferIndices = new int[configuration.size()];
        for (int i = 0; i < bufferIndices.length; i++) {
            bufferIndices[i] = -1;
        }
    }

    /**
     * Returns the native handle.
     *
     * @return the native handle
     */
    long nativeHandle() {
        return nativeHandle;
    }

    /**
     * Returns the cookie value associated with this request.
     *
     * @return the cookie
     */
    public long cookie() {
        return cookie;
    }

    /**
     * Adds a buffer for a stream to this request.
     *
     * @param streamIndex the stream index
     * @param bufferIndex the buffer index from the allocator
     * @param allocator the allocator that owns the buffer
     */
    public void addBuffer(int streamIndex, int bufferIndex, FrameBufferAllocator allocator) {
        if (bufferIndices[streamIndex] >= 0) {
            throw new IllegalStateException("Buffer already added for stream " + streamIndex);
        }

        int result = nativeAddBuffer(nativeHandle, configuration.nativeHandle(),
                                      streamIndex, allocator.nativeHandle(), bufferIndex);
        if (result != 0) {
            throw LibCameraException.forOperation("Request.addBuffer", result);
        }

        this.allocator = allocator;
        bufferIndices[streamIndex] = bufferIndex;
    }

    /**
     * Returns the buffer index for a stream.
     *
     * @param streamIndex the stream index
     * @return the buffer index, or -1 if no buffer assigned
     */
    public int getBufferIndex(int streamIndex) {
        if (streamIndex < 0 || streamIndex >= bufferIndices.length) {
            return -1;
        }
        return bufferIndices[streamIndex];
    }

    /**
     * Returns the allocator used for this request's buffers.
     *
     * @return the allocator
     */
    public FrameBufferAllocator getAllocator() {
        return allocator;
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public CameraConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Returns the completion status of this request.
     *
     * @return the status
     */
    public Status status() {
        int value = nativeStatus(nativeHandle);
        return Status.fromValue(value);
    }

    /**
     * Reuses this request, keeping buffers attached.
     */
    public void reuse() {
        nativeReuse(nativeHandle);
    }

    /**
     * Gets the frame timestamp for a buffer.
     *
     * @param streamIndex the stream index
     * @return the timestamp in nanoseconds
     */
    public long getTimestamp(int streamIndex) {
        int bufferIndex = bufferIndices[streamIndex];
        if (bufferIndex < 0 || allocator == null) {
            return 0;
        }
        return nativeGetTimestamp(nativeHandle, configuration.nativeHandle(),
                                   streamIndex, allocator.nativeHandle(), bufferIndex);
    }

    /**
     * Gets the frame sequence number for a buffer.
     *
     * @param streamIndex the stream index
     * @return the sequence number
     */
    public long getSequence(int streamIndex) {
        int bufferIndex = bufferIndices[streamIndex];
        if (bufferIndex < 0 || allocator == null) {
            return 0;
        }
        return nativeGetSequence(nativeHandle, configuration.nativeHandle(),
                                  streamIndex, allocator.nativeHandle(), bufferIndex);
    }

    /**
     * Gets the exposure time in microseconds.
     *
     * @return exposure time in microseconds
     */
    public long getExposureTimeMicros() {
        return nativeGetExposureTime(nativeHandle);
    }

    /**
     * Gets the analogue gain applied by the sensor.
     *
     * @return analogue gain (1.0 = no gain)
     */
    public double getAnalogueGain() {
        return nativeGetAnalogueGain(nativeHandle);
    }

    /**
     * Gets the digital gain applied in processing.
     *
     * @return digital gain (1.0 = no gain)
     */
    public double getDigitalGain() {
        return nativeGetDigitalGain(nativeHandle);
    }

    /**
     * Gets the colour (white balance) gains.
     *
     * @return array of [red gain, blue gain]
     */
    public double[] getColourGains() {
        return nativeGetColourGains(nativeHandle);
    }

    /**
     * Gets the estimated colour temperature in Kelvin.
     *
     * @return colour temperature in Kelvin, or 0 if not available
     */
    public int getColourTemperature() {
        return nativeGetColourTemperature(nativeHandle);
    }

    /**
     * Gets the estimated scene lux level.
     *
     * @return lux value, or 0 if not available
     */
    public double getLux() {
        return nativeGetLux(nativeHandle);
    }

    /**
     * Gets the sensor black levels for each Bayer channel.
     * Used for DNG metadata.
     *
     * @return array of 4 black level values (one per channel)
     */
    public int[] getSensorBlackLevels() {
        return nativeGetSensorBlackLevels(nativeHandle);
    }

    /**
     * Gets the colour correction matrix (3x3).
     * Used for DNG color calibration.
     *
     * @return array of 9 values representing the 3x3 matrix (row-major)
     */
    public double[] getColourCorrectionMatrix() {
        return nativeGetColourCorrectionMatrix(nativeHandle);
    }

    /**
     * Sets the autofocus mode for this request.
     *
     * @param mode the autofocus mode
     */
    public void setAfMode(AfMode mode) {
        nativeSetAfMode(nativeHandle, mode.value());
    }

    /**
     * Sets the lens position for manual focus.
     *
     * <p>The lens position is specified in dioptres (1/distance in meters).
     * A value of 0.0 represents infinity focus, while larger values focus closer.</p>
     *
     * <p>Common values:</p>
     * <ul>
     *   <li>0.0 - Infinity focus</li>
     *   <li>2.0 - Focus at 0.5 meters</li>
     *   <li>5.0 - Focus at 0.2 meters</li>
     *   <li>10.0 - Focus at 0.1 meters (macro)</li>
     * </ul>
     *
     * @param position the lens position in dioptres
     */
    public void setLensPosition(float position) {
        nativeSetLensPosition(nativeHandle, position);
    }

    /**
     * Enables or disables auto-exposure.
     *
     * <p>When disabled, manual exposure time and analogue gain settings are used.</p>
     *
     * @param enable true to enable auto-exposure, false for manual control
     */
    public void setAeEnable(boolean enable) {
        nativeSetAeEnable(nativeHandle, enable);
    }

    /**
     * Sets the exposure time for manual exposure control.
     *
     * <p>This setting is only effective when auto-exposure is disabled.</p>
     *
     * <p>Example values:</p>
     * <ul>
     *   <li>100 - 1/10000s (very fast, bright daylight)</li>
     *   <li>1000 - 1/1000s (fast action)</li>
     *   <li>10000 - 1/100s (normal daylight)</li>
     *   <li>100000 - 1/10s (low light)</li>
     *   <li>1000000 - 1s (night/long exposure)</li>
     *   <li>30000000 - 30s (astrophotography)</li>
     * </ul>
     *
     * @param microseconds exposure time in microseconds
     */
    public void setExposureTime(int microseconds) {
        nativeSetExposureTime(nativeHandle, microseconds);
    }

    /**
     * Sets the analogue gain for manual exposure control.
     *
     * <p>This is the sensor gain, similar to ISO. Higher values = brighter but more noise.</p>
     *
     * <p>Approximate ISO equivalents (sensor dependent):</p>
     * <ul>
     *   <li>1.0 - ~ISO 100</li>
     *   <li>2.0 - ~ISO 200</li>
     *   <li>4.0 - ~ISO 400</li>
     *   <li>8.0 - ~ISO 800</li>
     *   <li>16.0 - ~ISO 1600</li>
     * </ul>
     *
     * @param gain analogue gain (1.0 = minimum gain)
     */
    public void setAnalogueGain(float gain) {
        nativeSetAnalogueGain(nativeHandle, gain);
    }

    /**
     * Gets all available metadata for a stream as an ImageMetadata record.
     *
     * @param streamIndex the stream index
     * @return the image metadata
     */
    public ImageMetadata getMetadata(int streamIndex) {
        StreamConfiguration streamConfig = configuration.get(streamIndex);
        double[] colourGains = getColourGains();

        return new ImageMetadata.Builder()
            .timestamp(getTimestamp(streamIndex))
            .sequence(getSequence(streamIndex))
            .exposureTimeMicros(getExposureTimeMicros())
            .analogueGain(getAnalogueGain())
            .digitalGain(getDigitalGain())
            .redGain(colourGains[0])
            .blueGain(colourGains[1])
            .colourTemperature(getColourTemperature())
            .lux(getLux())
            .size(streamConfig.size().width(), streamConfig.size().height())
            .pixelFormat(streamConfig.pixelFormat().fourccString())
            .build();
    }

    @Override
    public String toString() {
        return "Request[cookie=" + cookie + ", status=" + status() + "]";
    }

    // Native methods
    private native int nativeAddBuffer(long handle, long configHandle, int streamIndex,
                                        long allocatorHandle, int bufferIndex);
    private native int nativeReuse(long handle);
    private native int nativeStatus(long handle);
    private native long nativeGetTimestamp(long handle, long configHandle, int streamIndex,
                                            long allocatorHandle, int bufferIndex);
    private native long nativeGetSequence(long handle, long configHandle, int streamIndex,
                                           long allocatorHandle, int bufferIndex);
    private native long nativeGetExposureTime(long handle);
    private native double nativeGetAnalogueGain(long handle);
    private native double nativeGetDigitalGain(long handle);
    private native double[] nativeGetColourGains(long handle);
    private native int nativeGetColourTemperature(long handle);
    private native double nativeGetLux(long handle);
    private native int[] nativeGetSensorBlackLevels(long handle);
    private native double[] nativeGetColourCorrectionMatrix(long handle);
    private native void nativeSetAfMode(long handle, int mode);
    private native void nativeSetLensPosition(long handle, float position);
    private native void nativeSetAeEnable(long handle, boolean enable);
    private native void nativeSetExposureTime(long handle, int microseconds);
    private native void nativeSetAnalogueGain(long handle, float gain);
}
