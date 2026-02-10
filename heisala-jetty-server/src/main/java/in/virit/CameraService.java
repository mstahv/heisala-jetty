package in.virit;

import in.virit.libcamera4j.CameraCapture;
import in.virit.libcamera4j.CameraSettings;
import in.virit.libcamera4j.CaptureResult;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Service for capturing photos from the camera.
 */
@ApplicationScoped
public class CameraService {

    private static final Logger LOG = Logger.getLogger(CameraService.class);
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final String LATEST_PHOTO_FILENAME = "latest.jpg";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    @ConfigProperty(name = "camera.rotation", defaultValue = "0")
    int defaultRotation;

    @Inject
    @ConfigProperty(name = "camera.focus.mode", defaultValue = "continuous")
    String defaultFocusMode;

    @Inject
    @ConfigProperty(name = "camera.focus.position", defaultValue = "0.0")
    float defaultLensPosition;

    @Inject
    @ConfigProperty(name = "camera.exposure.auto", defaultValue = "true")
    boolean defaultAutoExposure;

    @Inject
    @ConfigProperty(name = "camera.exposure.time", defaultValue = "10000")
    int defaultExposureTimeMicros;

    @Inject
    @ConfigProperty(name = "camera.exposure.gain", defaultValue = "1.0")
    float defaultAnalogueGain;

    @Inject
    TimelapseService timelapseService;

    private CameraSettings currentSettings;
    private LocalDateTime lastCaptureTime;
    private boolean cameraAvailable;

    @PostConstruct
    void init() {
        currentSettings = buildSettingsFromConfig();
        cameraAvailable = checkCameraAvailable();
        if (!cameraAvailable) {
            LOG.warn("Camera not available - running in UI-only mode. Camera features will be disabled.");
        }
    }

    private boolean checkCameraAvailable() {
        try {
            Class.forName("in.virit.libcamera4j.CameraCapture");
            // Try to trigger native library loading
            CameraCapture.class.getName();
            return true;
        } catch (Throwable e) {
            LOG.debug("Camera library not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns whether the camera hardware is available.
     *
     * @return true if camera is available
     */
    public boolean isCameraAvailable() {
        return cameraAvailable;
    }

    private CameraSettings buildSettingsFromConfig() {
        CameraSettings.Builder builder = CameraSettings.builder()
            .rotation(defaultRotation)
            .autoExposure(defaultAutoExposure)
            .exposureTimeMicros(defaultExposureTimeMicros)
            .analogueGain(defaultAnalogueGain);

        switch (defaultFocusMode.toLowerCase()) {
            case "manual":
                builder.manualFocus(defaultLensPosition);
                break;
            case "auto":
                builder.autofocus(false);
                break;
            case "continuous":
            default:
                builder.autofocus(true);
                break;
        }

        return builder.build();
    }

    /**
     * Returns the current camera settings.
     *
     * @return current camera settings
     */
    public CameraSettings getCurrentSettings() {
        return currentSettings;
    }

    /**
     * Updates the current camera settings.
     *
     * @param settings the new settings to use
     */
    public void setCurrentSettings(CameraSettings settings) {
        this.currentSettings = settings;
        LOG.info("Camera settings updated: " + settings);
    }

    /**
     * Returns the default camera settings from configuration.
     *
     * @return default camera settings
     */
    public CameraSettings getDefaultSettings() {
        return buildSettingsFromConfig();
    }

    /**
     * Returns the time of the last capture, or null if no capture has been made.
     *
     * @return last capture time
     */
    public LocalDateTime getLastCaptureTime() {
        return lastCaptureTime;
    }

    /**
     * Returns the formatted time of the last capture, or "Never" if no capture has been made.
     *
     * @return formatted last capture time
     */
    public String getLastCaptureTimeFormatted() {
        return lastCaptureTime != null ? lastCaptureTime.format(TIMESTAMP_FORMAT) : "Never";
    }

    /**
     * Asynchronously captures a JPEG image with metadata at standard resolution.
     *
     * @param settings camera settings to apply
     * @return a CompletableFuture that completes with the capture result
     */
    public CompletableFuture<CaptureResult> captureWithMetadataAsync(CameraSettings settings) {
        if (!cameraAvailable) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Camera not available"));
        }
        return CameraCapture.captureWithMetadataAsync(WIDTH, HEIGHT, settings);
    }

    /**
     * Asynchronously captures a full resolution JPEG image with metadata.
     *
     * @param settings camera settings to apply
     * @return a CompletableFuture that completes with the capture result
     */
    public CompletableFuture<CaptureResult> captureFullResolutionWithMetadataAsync(CameraSettings settings) {
        if (!cameraAvailable) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Camera not available"));
        }
        return CameraCapture.captureFullResolutionWithMetadataAsync(settings);
    }

    /**
     * Asynchronously captures a raw image and returns it as DNG bytes.
     *
     * @param settings camera settings to apply
     * @return a CompletableFuture that completes with the DNG file content
     */
    public CompletableFuture<byte[]> captureDngAsync(CameraSettings settings) {
        if (!cameraAvailable) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Camera not available"));
        }
        return CameraCapture.captureDngBytesAsync(settings);
    }

    /**
     * Captures a quick photo and saves it to disk as the latest photo.
     *
     * @param settings camera settings to apply
     * @return a CompletableFuture that completes with the capture result
     */
    public CompletableFuture<CaptureResult> captureAndSaveAsync(CameraSettings settings) {
        return captureWithMetadataAsync(settings)
            .thenApply(result -> {
                saveLatestPhoto(result.jpeg());
                return result;
            });
    }

    /**
     * Saves the given JPEG bytes as the latest photo on disk and for timelapse.
     *
     * @param jpeg the JPEG image data
     */
    public void saveLatestPhoto(byte[] jpeg) {
        try {
            Path photoPath = Path.of(LATEST_PHOTO_FILENAME);
            Files.write(photoPath, jpeg);
            lastCaptureTime = LocalDateTime.now();
            LOG.info("Latest photo saved to " + photoPath.toAbsolutePath());

            // Also save for timelapse
            timelapseService.saveTimelapseImage(jpeg, lastCaptureTime);
        } catch (IOException e) {
            LOG.error("Failed to save latest photo", e);
        }
    }

    /**
     * Returns the latest photo from disk, or null if not available.
     *
     * @return the latest photo bytes, or null
     */
    public byte[] getLatestPhoto() {
        try {
            Path photoPath = Path.of(LATEST_PHOTO_FILENAME);
            if (Files.exists(photoPath)) {
                return Files.readAllBytes(photoPath);
            }
        } catch (IOException e) {
            LOG.error("Failed to read latest photo", e);
        }
        return null;
    }

    /**
     * Checks if a latest photo is available on disk.
     *
     * @return true if latest photo exists
     */
    public boolean hasLatestPhoto() {
        return Files.exists(Path.of(LATEST_PHOTO_FILENAME));
    }

    /**
     * Returns the path to the latest photo file.
     *
     * @return path to the latest photo
     */
    public Path getLatestPhotoPath() {
        return Path.of(LATEST_PHOTO_FILENAME);
    }

    /**
     * Scheduled task that captures a photo every 15 seconds.
     */
    @Scheduled(every = "15s")
    void periodicCapture() {
        if (!cameraAvailable) {
            LOG.debug("Skipping periodic capture - camera not available");
            return;
        }
        LOG.info("Starting periodic capture...");
        captureAndSaveAsync(currentSettings)
            .thenAccept(result -> LOG.info("Periodic capture completed successfully"))
            .exceptionally(ex -> {
                LOG.error("Periodic capture failed", ex);
                return null;
            });
    }
}
