package in.virit;

import in.virit.libcamera4j.CameraCapture;
import in.virit.libcamera4j.CaptureResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.CompletableFuture;

/**
 * Service for capturing photos from the camera.
 */
@ApplicationScoped
public class CameraService {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    /**
     * Asynchronously captures a JPEG image at standard resolution (1920x1080).
     *
     * @return a CompletableFuture that completes with the JPEG data
     */
    public CompletableFuture<byte[]> captureJpegAsync() {
        return CameraCapture.captureJpegAsync(WIDTH, HEIGHT);
    }

    /**
     * Asynchronously captures a full resolution JPEG image.
     *
     * @return a CompletableFuture that completes with the JPEG data
     */
    public CompletableFuture<byte[]> captureFullResolutionJpegAsync() {
        return CameraCapture.captureFullResolutionJpegAsync();
    }

    /**
     * Asynchronously captures a JPEG image with metadata at standard resolution.
     *
     * @return a CompletableFuture that completes with the capture result
     */
    public CompletableFuture<CaptureResult> captureWithMetadataAsync() {
        return CameraCapture.captureWithMetadataAsync(WIDTH, HEIGHT);
    }

    /**
     * Asynchronously captures a full resolution JPEG image with metadata.
     *
     * @return a CompletableFuture that completes with the capture result
     */
    public CompletableFuture<CaptureResult> captureFullResolutionWithMetadataAsync() {
        return CameraCapture.captureFullResolutionWithMetadataAsync();
    }

    /**
     * Asynchronously captures a raw image and returns it as DNG bytes.
     *
     * <p>DNG (Digital Negative) is an open raw image format compatible with
     * professional image editors like Adobe Lightroom, Pixelmator Pro, and RawTherapee.</p>
     *
     * @return a CompletableFuture that completes with the DNG file content
     */
    public CompletableFuture<byte[]> captureDngAsync() {
        return CameraCapture.captureDngBytesAsync();
    }
}
