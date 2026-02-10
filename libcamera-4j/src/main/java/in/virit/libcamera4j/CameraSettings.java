package in.virit.libcamera4j;

/**
 * Camera capture settings for rotation, focus, and exposure control.
 *
 * <p>These settings are applied during image capture:</p>
 * <ul>
 *   <li><b>Transform</b> - Image rotation/flip applied in Java after capture</li>
 *   <li><b>Focus</b> - AF mode and lens position</li>
 *   <li><b>Exposure</b> - Auto or manual exposure with custom time and gain</li>
 * </ul>
 *
 * @param transform the image transform (rotation/flip)
 * @param afMode the autofocus mode
 * @param lensPosition lens position in dioptres for manual focus (0=infinity)
 * @param autoExposure true for auto exposure, false for manual
 * @param exposureTimeMicros exposure time in microseconds (for manual exposure)
 * @param analogueGain sensor gain, similar to ISO (for manual exposure)
 */
public record CameraSettings(
    Transform transform,
    AfMode afMode,
    float lensPosition,
    boolean autoExposure,
    int exposureTimeMicros,
    float analogueGain
) {
    /**
     * Creates default camera settings with no rotation, continuous autofocus, and auto exposure.
     *
     * @return default settings
     */
    public static CameraSettings defaults() {
        return new CameraSettings(Transform.IDENTITY, AfMode.CONTINUOUS, 0.0f, true, 0, 1.0f);
    }

    /**
     * Creates a builder for camera settings.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of these settings with a different transform.
     *
     * @param transform the new transform
     * @return new settings with the transform changed
     */
    public CameraSettings withTransform(Transform transform) {
        return new CameraSettings(transform, afMode, lensPosition, autoExposure, exposureTimeMicros, analogueGain);
    }

    /**
     * Returns a copy of these settings with a different rotation (in degrees).
     *
     * @param degrees rotation in degrees (0, 90, 180, or 270)
     * @return new settings with the rotation applied
     */
    public CameraSettings withRotation(int degrees) {
        return withTransform(Transform.fromRotation(degrees));
    }

    /**
     * Returns a copy of these settings with manual focus at the specified position.
     *
     * @param position lens position in dioptres (0=infinity)
     * @return new settings with manual focus enabled
     */
    public CameraSettings withManualFocus(float position) {
        return new CameraSettings(transform, AfMode.MANUAL, position, autoExposure, exposureTimeMicros, analogueGain);
    }

    /**
     * Returns a copy of these settings with autofocus enabled.
     *
     * @param continuous true for continuous AF, false for single-shot AF
     * @return new settings with autofocus enabled
     */
    public CameraSettings withAutofocus(boolean continuous) {
        AfMode mode = continuous ? AfMode.CONTINUOUS : AfMode.AUTO;
        return new CameraSettings(transform, mode, 0.0f, autoExposure, exposureTimeMicros, analogueGain);
    }

    /**
     * Returns a copy of these settings with manual exposure.
     *
     * @param exposureTimeMicros exposure time in microseconds
     * @param analogueGain sensor gain (1.0 = ~ISO 100)
     * @return new settings with manual exposure
     */
    public CameraSettings withManualExposure(int exposureTimeMicros, float analogueGain) {
        return new CameraSettings(transform, afMode, lensPosition, false, exposureTimeMicros, analogueGain);
    }

    /**
     * Returns a copy of these settings with auto exposure enabled.
     *
     * @return new settings with auto exposure
     */
    public CameraSettings withAutoExposure() {
        return new CameraSettings(transform, afMode, lensPosition, true, 0, 1.0f);
    }

    /**
     * Builder for CameraSettings.
     */
    public static class Builder {
        private Transform transform = Transform.IDENTITY;
        private AfMode afMode = AfMode.CONTINUOUS;
        private float lensPosition = 0.0f;
        private boolean autoExposure = true;
        private int exposureTimeMicros = 0;
        private float analogueGain = 1.0f;

        public Builder transform(Transform transform) {
            this.transform = transform;
            return this;
        }

        public Builder rotation(int degrees) {
            this.transform = Transform.fromRotation(degrees);
            return this;
        }

        public Builder afMode(AfMode afMode) {
            this.afMode = afMode;
            return this;
        }

        public Builder lensPosition(float lensPosition) {
            this.lensPosition = lensPosition;
            return this;
        }

        public Builder manualFocus(float position) {
            this.afMode = AfMode.MANUAL;
            this.lensPosition = position;
            return this;
        }

        public Builder autofocus(boolean continuous) {
            this.afMode = continuous ? AfMode.CONTINUOUS : AfMode.AUTO;
            this.lensPosition = 0.0f;
            return this;
        }

        public Builder autoExposure(boolean autoExposure) {
            this.autoExposure = autoExposure;
            return this;
        }

        public Builder exposureTimeMicros(int exposureTimeMicros) {
            this.exposureTimeMicros = exposureTimeMicros;
            return this;
        }

        public Builder analogueGain(float analogueGain) {
            this.analogueGain = analogueGain;
            return this;
        }

        public Builder manualExposure(int exposureTimeMicros, float analogueGain) {
            this.autoExposure = false;
            this.exposureTimeMicros = exposureTimeMicros;
            this.analogueGain = analogueGain;
            return this;
        }

        public CameraSettings build() {
            return new CameraSettings(transform, afMode, lensPosition, autoExposure, exposureTimeMicros, analogueGain);
        }
    }
}
