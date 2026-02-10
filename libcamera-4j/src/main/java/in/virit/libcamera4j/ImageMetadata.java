package in.virit.libcamera4j;

import java.time.Duration;

/**
 * Contains EXIF-like metadata for a captured image.
 *
 * <p>This record holds various camera settings and sensor data that was used
 * or measured during image capture, similar to EXIF data in standard image files.</p>
 */
public record ImageMetadata(
    long timestamp,
    long sequence,
    Duration exposureTime,
    double analogueGain,
    double digitalGain,
    double redGain,
    double blueGain,
    int colourTemperature,
    double lux,
    int width,
    int height,
    String pixelFormat
) {
    /**
     * Returns the total gain (analogue × digital).
     *
     * @return total gain
     */
    public double totalGain() {
        return analogueGain * digitalGain;
    }

    /**
     * Returns exposure time in a human-readable format.
     *
     * @return formatted exposure time (e.g., "1/60s" or "2.5s")
     */
    public String exposureTimeFormatted() {
        if (exposureTime == null || exposureTime.isZero()) {
            return "N/A";
        }
        long micros = exposureTime.toNanos() / 1000;
        if (micros >= 1_000_000) {
            return String.format("%.1fs", micros / 1_000_000.0);
        } else if (micros >= 1000) {
            double seconds = micros / 1_000_000.0;
            int denominator = (int) Math.round(1.0 / seconds);
            return "1/" + denominator + "s";
        } else {
            return micros + "µs";
        }
    }

    /**
     * Returns the ISO equivalent based on gains.
     * This is an approximation since exact ISO mapping depends on sensor.
     *
     * @return approximate ISO value
     */
    public int approximateIso() {
        // Base ISO 100 at gain 1.0 is a common approximation
        return (int) Math.round(100 * totalGain());
    }

    /**
     * Creates metadata with default/unknown values.
     *
     * @return metadata with default values
     */
    public static ImageMetadata unknown() {
        return new ImageMetadata(0, 0, Duration.ZERO, 1.0, 1.0, 1.0, 1.0, 0, 0.0, 0, 0, "unknown");
    }

    /**
     * Builder for creating ImageMetadata instances.
     */
    public static class Builder {
        private long timestamp;
        private long sequence;
        private Duration exposureTime = Duration.ZERO;
        private double analogueGain = 1.0;
        private double digitalGain = 1.0;
        private double redGain = 1.0;
        private double blueGain = 1.0;
        private int colourTemperature;
        private double lux;
        private int width;
        private int height;
        private String pixelFormat = "unknown";

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder sequence(long sequence) {
            this.sequence = sequence;
            return this;
        }

        public Builder exposureTime(Duration exposureTime) {
            this.exposureTime = exposureTime;
            return this;
        }

        public Builder exposureTimeMicros(long micros) {
            this.exposureTime = Duration.ofNanos(micros * 1000);
            return this;
        }

        public Builder analogueGain(double analogueGain) {
            this.analogueGain = analogueGain;
            return this;
        }

        public Builder digitalGain(double digitalGain) {
            this.digitalGain = digitalGain;
            return this;
        }

        public Builder redGain(double redGain) {
            this.redGain = redGain;
            return this;
        }

        public Builder blueGain(double blueGain) {
            this.blueGain = blueGain;
            return this;
        }

        public Builder colourTemperature(int colourTemperature) {
            this.colourTemperature = colourTemperature;
            return this;
        }

        public Builder lux(double lux) {
            this.lux = lux;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder pixelFormat(String pixelFormat) {
            this.pixelFormat = pixelFormat;
            return this;
        }

        public ImageMetadata build() {
            return new ImageMetadata(
                timestamp, sequence, exposureTime, analogueGain, digitalGain,
                redGain, blueGain, colourTemperature, lux, width, height, pixelFormat
            );
        }
    }
}
