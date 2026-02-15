package in.virit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;

/**
 * DTO for timelapse generation settings with bean validation.
 */
@ValidDateRange
public class TimelapseSettings {

    @NotNull(message = "Start time is required")
    private LocalDateTime from;

    @NotNull(message = "End time is required")
    private LocalDateTime to;

    @NotNull(message = "Frames per second is required")
    @Min(value = 1, message = "FPS must be at least 1")
    @Max(value = 60, message = "FPS must be at most 60")
    private Integer fps;

    @NotNull(message = "Sampling interval is required")
    private SamplingInterval samplingInterval;

    @NotNull(message = "Resolution is required")
    private VideoResolution resolution;

    @NotNull(message = "Quality is required")
    private VideoQuality quality;

    // Default constructor for bean validation
    public TimelapseSettings() {
    }

    // Constructor with all fields
    public TimelapseSettings(LocalDateTime from, LocalDateTime to, Integer fps,
                           SamplingInterval samplingInterval, VideoResolution resolution, VideoQuality quality) {
        this.from = from;
        this.to = to;
        this.fps = fps;
        this.samplingInterval = samplingInterval;
        this.resolution = resolution;
        this.quality = quality;
    }

    // Getters and setters
    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public void setTo(LocalDateTime to) {
        this.to = to;
    }

    public Integer getFps() {
        return fps;
    }

    public void setFps(Integer fps) {
        this.fps = fps;
    }

    public SamplingInterval getSamplingInterval() {
        return samplingInterval;
    }

    public void setSamplingInterval(SamplingInterval samplingInterval) {
        this.samplingInterval = samplingInterval;
    }

    public VideoResolution getResolution() {
        return resolution;
    }

    public void setResolution(VideoResolution resolution) {
        this.resolution = resolution;
    }

    public VideoQuality getQuality() {
        return quality;
    }

    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }

    // Nested classes for the selection options (moved from TimelapseView)
    public record SamplingInterval(String label, int seconds) {
        @Override public String toString() { return label; }
    }

    public record VideoResolution(String label, String scale) {
        @Override public String toString() { return label; }
    }

    public record VideoQuality(String label, String bitrate, int bitrateKbps) {
        @Override public String toString() { return label; }
    }

    // Static arrays for the selection options
    public static final SamplingInterval[] SAMPLING_INTERVALS = {
        new SamplingInterval("All images", 0),
        new SamplingInterval("1 per minute", 60),
        new SamplingInterval("1 per 5 min", 300),
        new SamplingInterval("1 per 15 min", 900),
        new SamplingInterval("1 per hour", 3600)
    };

    public static final VideoResolution[] RESOLUTIONS = {
        new VideoResolution("Original", null),
        new VideoResolution("1080p", "1920:1080"),
        new VideoResolution("720p", "1280:720"),
        new VideoResolution("480p", "854:480")
    };

    public static final VideoQuality[] QUALITIES = {
        new VideoQuality("Low (2 Mbps)", "2M", 2000),
        new VideoQuality("Medium (5 Mbps)", "5M", 5000),
        new VideoQuality("High (8 Mbps)", "8M", 8000),
        new VideoQuality("Very High (12 Mbps)", "12M", 12000)
    };
}