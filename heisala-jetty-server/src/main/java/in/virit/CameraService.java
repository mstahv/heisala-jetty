package in.virit;

import in.virit.libcamera4j.CameraCapture;
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
     * Asynchronously captures a JPEG image.
     *
     * @return a CompletableFuture that completes with the JPEG data
     */
    public CompletableFuture<byte[]> captureJpegAsync() {
        return CameraCapture.captureJpegAsync(WIDTH, HEIGHT);
    }

    /**
     * Synchronously captures a JPEG image.
     *
     * @return JPEG-encoded image data
     */
    public byte[] captureJpeg() {
        return CameraCapture.captureJpeg(WIDTH, HEIGHT);
    }
}
