package in.virit.libcamera4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-level API for capturing still images from a camera.
 *
 * <p>This class provides simple methods for capturing images without needing to
 * manage the low-level camera lifecycle. It handles camera initialization,
 * warm-up frames for auto-exposure, pixel format conversion, and cleanup.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Synchronous capture
 * byte[] jpeg = CameraCapture.captureJpeg(1920, 1080);
 *
 * // Asynchronous capture
 * CameraCapture.captureJpegAsync(1920, 1080)
 *     .thenAccept(jpeg -> saveToFile(jpeg));
 * }</pre>
 */
public class CameraCapture {

    private static final int DEFAULT_WARMUP_FRAMES = 10;
    private static final int DEFAULT_BUFFER_COUNT = 2;
    private static final int REQUEST_TIMEOUT_MS = 5000;

    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "CameraCapture");
        t.setDaemon(true);
        return t;
    });

    /**
     * Captures a JPEG image at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @return JPEG-encoded image data
     * @throws LibCameraException if capture fails
     */
    public static byte[] captureJpeg(int width, int height) {
        return captureJpeg(width, height, DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Captures a JPEG image at the specified resolution with custom warm-up frames.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of frames to capture before the final image (for auto-exposure)
     * @return JPEG-encoded image data
     * @throws LibCameraException if capture fails
     */
    public static byte[] captureJpeg(int width, int height, int warmupFrames) {
        BufferedImage image = captureImage(width, height, warmupFrames);
        return encodeJpeg(image);
    }

    /**
     * Captures a BufferedImage at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @return the captured image
     * @throws LibCameraException if capture fails
     */
    public static BufferedImage captureImage(int width, int height) {
        return captureImage(width, height, DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Captures a BufferedImage at the specified resolution with custom warm-up frames.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of frames to capture before the final image
     * @return the captured image
     * @throws LibCameraException if capture fails
     */
    public static BufferedImage captureImage(int width, int height, int warmupFrames) {
        try (CameraManager manager = CameraManager.create()) {
            manager.start();

            List<Camera> cameras = manager.cameras();
            if (cameras.isEmpty()) {
                throw new LibCameraException("No cameras found");
            }

            Camera camera = cameras.get(0);
            camera.acquire();

            try {
                CameraConfiguration config = camera.generateConfiguration(StreamRole.STILL_CAPTURE);
                if (config.isEmpty()) {
                    config = camera.generateConfiguration(StreamRole.VIEWFINDER);
                }
                if (config.isEmpty()) {
                    throw new LibCameraException("Could not generate camera configuration");
                }

                StreamConfiguration streamConfig = config.get(0);
                streamConfig.setSize(new Size(width, height));
                streamConfig.setBufferCount(DEFAULT_BUFFER_COUNT);
                config.validate();
                camera.configure(config);

                try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                    allocator.allocate(0);

                    Request request = camera.createRequest();
                    request.addBuffer(0, 0, allocator);

                    camera.start();

                    // Warm-up frames for auto-exposure
                    for (int i = 0; i < warmupFrames; i++) {
                        camera.queueRequest(request);
                        waitForRequest(request);
                        if (i < warmupFrames - 1) {
                            request.reuse();
                        }
                    }

                    camera.stop();

                    if (request.status() != Request.Status.COMPLETE) {
                        throw new LibCameraException("Capture failed: " + request.status());
                    }

                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    byte[] rawData = buffer.getData();

                    return PixelFormatConverter.convert(
                        rawData,
                        streamConfig.size().width(),
                        streamConfig.size().height(),
                        streamConfig.stride(),
                        streamConfig.pixelFormat()
                    );
                }
            } finally {
                camera.release();
            }
        }
    }

    /**
     * Asynchronously captures a JPEG image at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @return a CompletableFuture that completes with the JPEG data
     */
    public static CompletableFuture<byte[]> captureJpegAsync(int width, int height) {
        return captureJpegAsync(width, height, DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Asynchronously captures a JPEG image at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the JPEG data
     */
    public static CompletableFuture<byte[]> captureJpegAsync(int width, int height, int warmupFrames) {
        return CompletableFuture.supplyAsync(() -> captureJpeg(width, height, warmupFrames), executor);
    }

    /**
     * Asynchronously captures a BufferedImage at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @return a CompletableFuture that completes with the captured image
     */
    public static CompletableFuture<BufferedImage> captureImageAsync(int width, int height) {
        return captureImageAsync(width, height, DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Asynchronously captures a BufferedImage at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the captured image
     */
    public static CompletableFuture<BufferedImage> captureImageAsync(int width, int height, int warmupFrames) {
        return CompletableFuture.supplyAsync(() -> captureImage(width, height, warmupFrames), executor);
    }

    private static void waitForRequest(Request request) {
        int waited = 0;
        while (request.status() == Request.Status.PENDING && waited < REQUEST_TIMEOUT_MS) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LibCameraException("Capture interrupted");
            }
            waited += 50;
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new LibCameraException("Failed to encode JPEG: " + e.getMessage(), e);
        }
    }
}
