package in.virit;

import in.virit.libcamera4j.CameraCapture;
import in.virit.libcamera4j.CameraSettings;
import in.virit.libcamera4j.CaptureResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.CompletableFuture;

/**
 * Service for capturing photos from the camera.
 */
@ApplicationScoped
public class CameraService {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

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

    /**
     * Returns the default camera settings from configuration.
     *
     * @return default camera settings
     */
    public CameraSettings getDefaultSettings() {
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
     * Asynchronously captures a JPEG image with metadata at standard resolution.
     *
     * @param settings camera settings to apply
     * @return a CompletableFuture that completes with the capture result
     */
    public CompletableFuture<CaptureResult> captureWithMetadataAsync(CameraSettings settings) {
        return CameraCapture.captureWithMetadataAsync(WIDTH, HEIGHT, settings);
    }

    /**
     * Asynchronously captures a full resolution JPEG image with metadata.
     *
     * @param settings camera settings to apply
     * @return a CompletableFuture that completes with the capture result
     */
    public CompletableFuture<CaptureResult> captureFullResolutionWithMetadataAsync(CameraSettings settings) {
        return CameraCapture.captureFullResolutionWithMetadataAsync(settings);
    }

    /**
     * Asynchronously captures a raw image and returns it as DNG bytes.
     *
     * @param settings camera settings to apply
     * @return a CompletableFuture that completes with the DNG file content
     */
    public CompletableFuture<byte[]> captureDngAsync(CameraSettings settings) {
        return CameraCapture.captureDngBytesAsync(settings);
    }
}
