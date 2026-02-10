package in.virit.libcamera4j;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    static {
        NativeLoader.load();
    }

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
        return captureJpeg(width, height, DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Captures a JPEG image at the specified resolution with custom settings.
     *
     * @param width desired image width
     * @param height desired image height
     * @param settings camera settings for rotation and focus
     * @return JPEG-encoded image data
     * @throws LibCameraException if capture fails
     */
    public static byte[] captureJpeg(int width, int height, CameraSettings settings) {
        return captureJpeg(width, height, DEFAULT_WARMUP_FRAMES, settings);
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
        return captureJpeg(width, height, warmupFrames, CameraSettings.defaults());
    }

    /**
     * Captures a JPEG image at the specified resolution with custom settings.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of frames to capture before the final image (for auto-exposure)
     * @param settings camera settings for rotation and focus
     * @return JPEG-encoded image data
     * @throws LibCameraException if capture fails
     */
    public static byte[] captureJpeg(int width, int height, int warmupFrames, CameraSettings settings) {
        BufferedImage image = captureImage(width, height, warmupFrames, settings);
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
        return captureImage(width, height, DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
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
        return captureImage(width, height, warmupFrames, CameraSettings.defaults());
    }

    /**
     * Captures a BufferedImage at the specified resolution with custom settings.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of frames to capture before the final image
     * @param settings camera settings for rotation and focus
     * @return the captured image
     * @throws LibCameraException if capture fails
     */
    public static BufferedImage captureImage(int width, int height, int warmupFrames, CameraSettings settings) {
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

                    // Apply focus settings
                    applyCameraSettings(request, settings);

                    camera.start();

                    // Warm-up frames for auto-exposure
                    for (int i = 0; i < warmupFrames; i++) {
                        camera.queueRequest(request);
                        waitForRequest(request);
                        if (i < warmupFrames - 1) {
                            request.reuse();
                            applyCameraSettings(request, settings);
                        }
                    }

                    camera.stop();

                    if (request.status() != Request.Status.COMPLETE) {
                        throw new LibCameraException("Capture failed: " + request.status());
                    }

                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    byte[] rawData = buffer.getData();

                    BufferedImage image = PixelFormatConverter.convert(
                        rawData,
                        streamConfig.size().width(),
                        streamConfig.size().height(),
                        streamConfig.stride(),
                        streamConfig.pixelFormat()
                    );

                    return applyTransform(image, settings.transform());
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

    /**
     * Captures a full resolution JPEG image using the camera's native resolution.
     *
     * @return JPEG-encoded image data at full sensor resolution
     * @throws LibCameraException if capture fails
     */
    public static byte[] captureFullResolutionJpeg() {
        return captureFullResolutionJpeg(DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Captures a full resolution JPEG image using the camera's native resolution.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return JPEG-encoded image data at full sensor resolution
     * @throws LibCameraException if capture fails
     */
    public static byte[] captureFullResolutionJpeg(int warmupFrames) {
        BufferedImage image = captureFullResolutionImage(warmupFrames);
        return encodeJpeg(image);
    }

    /**
     * Captures a full resolution BufferedImage using the camera's native resolution.
     *
     * @return the captured image at full sensor resolution
     * @throws LibCameraException if capture fails
     */
    public static BufferedImage captureFullResolutionImage() {
        return captureFullResolutionImage(DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Captures a full resolution BufferedImage using the camera's native resolution.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return the captured image at full sensor resolution
     * @throws LibCameraException if capture fails
     */
    public static BufferedImage captureFullResolutionImage(int warmupFrames) {
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
                    throw new LibCameraException("Could not generate camera configuration");
                }

                // Don't set size - use camera's default (full resolution)
                StreamConfiguration streamConfig = config.get(0);
                streamConfig.setBufferCount(DEFAULT_BUFFER_COUNT);
                config.validate();
                camera.configure(config);

                try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                    allocator.allocate(0);

                    Request request = camera.createRequest();
                    request.addBuffer(0, 0, allocator);

                    camera.start();

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
     * Captures raw image data at full resolution without processing.
     *
     * <p>The returned RawImage contains the unprocessed pixel data along with
     * metadata about the format, dimensions, and stride. This is useful for
     * advanced image processing or archival purposes.</p>
     *
     * @return raw image data with metadata
     * @throws LibCameraException if capture fails
     */
    public static RawImage captureRaw() {
        return captureRaw(DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Captures raw image data at full resolution without processing.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return raw image data with metadata
     * @throws LibCameraException if capture fails
     */
    public static RawImage captureRaw(int warmupFrames) {
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
                    throw new LibCameraException("Could not generate camera configuration");
                }

                // Use full resolution
                StreamConfiguration streamConfig = config.get(0);
                streamConfig.setBufferCount(DEFAULT_BUFFER_COUNT);
                config.validate();
                camera.configure(config);

                try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                    allocator.allocate(0);

                    Request request = camera.createRequest();
                    request.addBuffer(0, 0, allocator);

                    camera.start();

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

                    return new RawImage(
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
     * Asynchronously captures a full resolution JPEG image.
     *
     * @return a CompletableFuture that completes with the JPEG data
     */
    public static CompletableFuture<byte[]> captureFullResolutionJpegAsync() {
        return captureFullResolutionJpegAsync(DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Asynchronously captures a full resolution JPEG image.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the JPEG data
     */
    public static CompletableFuture<byte[]> captureFullResolutionJpegAsync(int warmupFrames) {
        return CompletableFuture.supplyAsync(() -> captureFullResolutionJpeg(warmupFrames), executor);
    }

    /**
     * Asynchronously captures raw image data at full resolution.
     *
     * @return a CompletableFuture that completes with the raw image
     */
    public static CompletableFuture<RawImage> captureRawAsync() {
        return captureRawAsync(DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Asynchronously captures raw image data at full resolution.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the raw image
     */
    public static CompletableFuture<RawImage> captureRawAsync(int warmupFrames) {
        return CompletableFuture.supplyAsync(() -> captureRaw(warmupFrames), executor);
    }

    /**
     * Captures a JPEG image with full metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureWithMetadata(int width, int height) {
        return captureWithMetadata(width, height, DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Captures a JPEG image with full metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param settings camera settings for rotation and focus
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureWithMetadata(int width, int height, CameraSettings settings) {
        return captureWithMetadata(width, height, DEFAULT_WARMUP_FRAMES, settings);
    }

    /**
     * Captures a JPEG image with full metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureWithMetadata(int width, int height, int warmupFrames) {
        return captureWithMetadata(width, height, warmupFrames, CameraSettings.defaults());
    }

    /**
     * Captures a JPEG image with full metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for rotation and focus
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureWithMetadata(int width, int height, int warmupFrames, CameraSettings settings) {
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

                    // Apply focus settings
                    applyCameraSettings(request, settings);

                    camera.start();

                    for (int i = 0; i < warmupFrames; i++) {
                        camera.queueRequest(request);
                        waitForRequest(request);
                        if (i < warmupFrames - 1) {
                            request.reuse();
                            applyCameraSettings(request, settings);
                        }
                    }

                    camera.stop();

                    if (request.status() != Request.Status.COMPLETE) {
                        throw new LibCameraException("Capture failed: " + request.status());
                    }

                    // Extract metadata before reading buffer
                    ImageMetadata metadata = request.getMetadata(0);

                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    byte[] rawData = buffer.getData();

                    BufferedImage image = PixelFormatConverter.convert(
                        rawData,
                        streamConfig.size().width(),
                        streamConfig.size().height(),
                        streamConfig.stride(),
                        streamConfig.pixelFormat()
                    );

                    image = applyTransform(image, settings.transform());
                    byte[] jpeg = encodeJpeg(image);
                    return CaptureResult.ofJpeg(jpeg, metadata);
                }
            } finally {
                camera.release();
            }
        }
    }

    /**
     * Captures a full resolution JPEG with metadata.
     *
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureFullResolutionWithMetadata() {
        return captureFullResolutionWithMetadata(DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Captures a full resolution JPEG with metadata.
     *
     * @param settings camera settings for rotation and focus
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureFullResolutionWithMetadata(CameraSettings settings) {
        return captureFullResolutionWithMetadata(DEFAULT_WARMUP_FRAMES, settings);
    }

    /**
     * Captures a full resolution JPEG with metadata.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureFullResolutionWithMetadata(int warmupFrames) {
        return captureFullResolutionWithMetadata(warmupFrames, CameraSettings.defaults());
    }

    /**
     * Captures a full resolution JPEG with metadata.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for rotation and focus
     * @return capture result containing JPEG data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureFullResolutionWithMetadata(int warmupFrames, CameraSettings settings) {
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
                    throw new LibCameraException("Could not generate camera configuration");
                }

                StreamConfiguration streamConfig = config.get(0);
                streamConfig.setBufferCount(DEFAULT_BUFFER_COUNT);

                config.validate();
                camera.configure(config);

                try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                    allocator.allocate(0);

                    Request request = camera.createRequest();
                    request.addBuffer(0, 0, allocator);

                    // Apply focus settings
                    applyCameraSettings(request, settings);

                    camera.start();

                    for (int i = 0; i < warmupFrames; i++) {
                        camera.queueRequest(request);
                        waitForRequest(request);
                        if (i < warmupFrames - 1) {
                            request.reuse();
                            applyCameraSettings(request, settings);
                        }
                    }

                    camera.stop();

                    if (request.status() != Request.Status.COMPLETE) {
                        throw new LibCameraException("Capture failed: " + request.status());
                    }

                    ImageMetadata metadata = request.getMetadata(0);

                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    byte[] rawData = buffer.getData();

                    BufferedImage image = PixelFormatConverter.convert(
                        rawData,
                        streamConfig.size().width(),
                        streamConfig.size().height(),
                        streamConfig.stride(),
                        streamConfig.pixelFormat()
                    );

                    image = applyTransform(image, settings.transform());
                    byte[] jpeg = encodeJpeg(image);
                    return CaptureResult.ofJpeg(jpeg, metadata);
                }
            } finally {
                camera.release();
            }
        }
    }

    /**
     * Captures raw image data with metadata at full resolution.
     *
     * @return capture result containing raw data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureRawWithMetadata() {
        return captureRawWithMetadata(DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Captures raw image data with metadata at full resolution.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return capture result containing raw data and metadata
     * @throws LibCameraException if capture fails
     */
    public static CaptureResult captureRawWithMetadata(int warmupFrames) {
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
                    throw new LibCameraException("Could not generate camera configuration");
                }

                StreamConfiguration streamConfig = config.get(0);
                streamConfig.setBufferCount(DEFAULT_BUFFER_COUNT);
                config.validate();
                camera.configure(config);

                try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                    allocator.allocate(0);

                    Request request = camera.createRequest();
                    request.addBuffer(0, 0, allocator);

                    camera.start();

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

                    ImageMetadata metadata = request.getMetadata(0);

                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    byte[] rawData = buffer.getData();

                    RawImage rawImage = new RawImage(
                        rawData,
                        streamConfig.size().width(),
                        streamConfig.size().height(),
                        streamConfig.stride(),
                        streamConfig.pixelFormat()
                    );

                    return CaptureResult.ofRaw(rawImage, metadata);
                }
            } finally {
                camera.release();
            }
        }
    }

    /**
     * Asynchronously captures a JPEG with metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureWithMetadataAsync(int width, int height) {
        return captureWithMetadataAsync(width, height, DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a JPEG with metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param settings camera settings for rotation and focus
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureWithMetadataAsync(int width, int height, CameraSettings settings) {
        return captureWithMetadataAsync(width, height, DEFAULT_WARMUP_FRAMES, settings);
    }

    /**
     * Asynchronously captures a JPEG with metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureWithMetadataAsync(int width, int height, int warmupFrames) {
        return captureWithMetadataAsync(width, height, warmupFrames, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a JPEG with metadata at the specified resolution.
     *
     * @param width desired image width
     * @param height desired image height
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for rotation and focus
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureWithMetadataAsync(int width, int height, int warmupFrames, CameraSettings settings) {
        return CompletableFuture.supplyAsync(() -> captureWithMetadata(width, height, warmupFrames, settings), executor);
    }

    /**
     * Asynchronously captures a full resolution JPEG with metadata.
     *
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureFullResolutionWithMetadataAsync() {
        return captureFullResolutionWithMetadataAsync(DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a full resolution JPEG with metadata.
     *
     * @param settings camera settings for rotation and focus
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureFullResolutionWithMetadataAsync(CameraSettings settings) {
        return captureFullResolutionWithMetadataAsync(DEFAULT_WARMUP_FRAMES, settings);
    }

    /**
     * Asynchronously captures a full resolution JPEG with metadata.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureFullResolutionWithMetadataAsync(int warmupFrames) {
        return captureFullResolutionWithMetadataAsync(warmupFrames, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a full resolution JPEG with metadata.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for rotation and focus
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureFullResolutionWithMetadataAsync(int warmupFrames, CameraSettings settings) {
        return CompletableFuture.supplyAsync(() -> captureFullResolutionWithMetadata(warmupFrames, settings), executor);
    }

    /**
     * Asynchronously captures raw image data with metadata.
     *
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureRawWithMetadataAsync() {
        return captureRawWithMetadataAsync(DEFAULT_WARMUP_FRAMES);
    }

    /**
     * Asynchronously captures raw image data with metadata.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the capture result
     */
    public static CompletableFuture<CaptureResult> captureRawWithMetadataAsync(int warmupFrames) {
        return CompletableFuture.supplyAsync(() -> captureRawWithMetadata(warmupFrames), executor);
    }

    // =========================================================================
    // DNG Capture Methods
    // =========================================================================

    /**
     * Captures a raw image and saves it as a DNG file.
     *
     * <p>DNG (Digital Negative) is an open raw image format compatible with
     * professional image editors like Adobe Lightroom, Pixelmator Pro, and RawTherapee.</p>
     *
     * @param path the file path to save the DNG file
     * @throws LibCameraException if capture or DNG writing fails
     */
    public static void captureDng(String path) {
        captureDng(path, DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Captures a raw image and saves it as a DNG file.
     *
     * @param path the file path to save the DNG file
     * @param settings camera settings for focus (rotation not supported in RAW mode)
     * @throws LibCameraException if capture or DNG writing fails
     */
    public static void captureDng(String path, CameraSettings settings) {
        captureDng(path, DEFAULT_WARMUP_FRAMES, settings);
    }

    /**
     * Captures a raw image and saves it as a DNG file.
     *
     * @param path the file path to save the DNG file
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @throws LibCameraException if capture or DNG writing fails
     */
    public static void captureDng(String path, int warmupFrames) {
        captureDng(path, warmupFrames, CameraSettings.defaults());
    }

    /**
     * Captures a raw image and saves it as a DNG file.
     *
     * @param path the file path to save the DNG file
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for focus (rotation not supported in RAW mode)
     * @throws LibCameraException if capture or DNG writing fails
     */
    public static void captureDng(String path, int warmupFrames, CameraSettings settings) {
        try (CameraManager manager = CameraManager.create()) {
            manager.start();

            List<Camera> cameras = manager.cameras();
            if (cameras.isEmpty()) {
                throw new LibCameraException("No cameras found");
            }

            Camera camera = cameras.get(0);
            camera.acquire();

            try {
                // Use RAW stream role for raw Bayer data
                CameraConfiguration config = camera.generateConfiguration(StreamRole.RAW);
                if (config.isEmpty()) {
                    throw new LibCameraException("Camera does not support RAW capture");
                }

                StreamConfiguration streamConfig = config.get(0);
                streamConfig.setBufferCount(DEFAULT_BUFFER_COUNT);
                config.validate();
                camera.configure(config);

                // Re-read the configuration after validation/configuration
                streamConfig = config.get(0);
                PixelFormat actualFormat = streamConfig.pixelFormat();
                String formatStr = actualFormat.fourccString();

                // Validate that we got a raw Bayer format
                boolean isRawFormat = formatStr.matches("^[BGRA][GBAR][0-9]+.*") ||
                                      formatStr.startsWith("S") ||
                                      formatStr.startsWith("p");

                if (!isRawFormat) {
                    throw new LibCameraException(
                        "Camera did not provide raw Bayer format. Got: " + formatStr +
                        " (" + streamConfig.size().width() + "x" + streamConfig.size().height() + ").");
                }

                try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                    int bufferCount = allocator.allocate(0);
                    if (bufferCount == 0) {
                        throw new LibCameraException("No buffers allocated for raw stream");
                    }

                    Request request = camera.createRequest();
                    request.addBuffer(0, 0, allocator);

                    // Apply focus settings
                    applyCameraSettings(request, settings);

                    camera.start();

                    // Warm-up frames for auto-exposure
                    for (int i = 0; i < warmupFrames; i++) {
                        camera.queueRequest(request);
                        waitForRequest(request);
                        if (i < warmupFrames - 1) {
                            request.reuse();
                            applyCameraSettings(request, settings);
                        }
                    }

                    camera.stop();

                    if (request.status() != Request.Status.COMPLETE) {
                        throw new LibCameraException("Capture failed: " + request.status());
                    }

                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    byte[] rawData = buffer.getData();

                    int width = streamConfig.size().width();
                    int height = streamConfig.size().height();
                    int stride = streamConfig.stride();

                    // Validate stride against actual data
                    int actualStride = rawData.length / height;
                    if (stride > actualStride) {
                        stride = actualStride;
                    }

                    // Get metadata for DNG
                    double[] colourGains = request.getColourGains();
                    int[] blackLevels = request.getSensorBlackLevels();
                    double[] colourMatrix = request.getColourCorrectionMatrix();

                    // Unpack 10-bit packed data to 16-bit
                    short[] unpackedData = DngWriter.unpack10bit(rawData, width, height, stride);

                    // Detect Bayer order from format
                    DngWriter.BayerOrder bayerOrder = DngWriter.detectBayerOrder(formatStr);

                    // Calculate white balance neutral values
                    double redGain = colourGains[0] > 0 ? colourGains[0] : 1.0;
                    double blueGain = colourGains[1] > 0 ? colourGains[1] : 1.0;
                    double[] asShotNeutral = {1.0 / redGain, 1.0, 1.0 / blueGain};

                    // Write DNG using pure Java writer
                    DngWriter writer = new DngWriter(width, height, 10, bayerOrder, unpackedData);
                    writer.setMake("Raspberry Pi")
                          .setModel("Camera Module")
                          .setSoftware("libcamera4j")
                          .setColorMatrix(colourMatrix)
                          .setAsShotNeutral(asShotNeutral)
                          .setBlackLevel(blackLevels);

                    try {
                        writer.write(java.nio.file.Path.of(path));
                    } catch (IOException e) {
                        throw new LibCameraException("Failed to write DNG file: " + e.getMessage(), e);
                    }
                }
            } finally {
                camera.release();
            }
        }
    }

    /**
     * Asynchronously captures a raw image and saves it as a DNG file.
     *
     * @param path the file path to save the DNG file
     * @return a CompletableFuture that completes when the DNG is saved
     */
    public static CompletableFuture<Void> captureDngAsync(String path) {
        return captureDngAsync(path, DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a raw image and saves it as a DNG file.
     *
     * @param path the file path to save the DNG file
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes when the DNG is saved
     */
    public static CompletableFuture<Void> captureDngAsync(String path, int warmupFrames) {
        return captureDngAsync(path, warmupFrames, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a raw image and saves it as a DNG file.
     *
     * @param path the file path to save the DNG file
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for focus
     * @return a CompletableFuture that completes when the DNG is saved
     */
    public static CompletableFuture<Void> captureDngAsync(String path, int warmupFrames, CameraSettings settings) {
        return CompletableFuture.runAsync(() -> captureDng(path, warmupFrames, settings), executor);
    }

    /**
     * Captures a raw image and returns it as DNG bytes.
     *
     * @return the DNG file content as a byte array
     * @throws LibCameraException if capture or DNG creation fails
     */
    public static byte[] captureDngBytes() {
        return captureDngBytes(DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Captures a raw image and returns it as DNG bytes.
     *
     * @param settings camera settings for focus
     * @return the DNG file content as a byte array
     * @throws LibCameraException if capture or DNG creation fails
     */
    public static byte[] captureDngBytes(CameraSettings settings) {
        return captureDngBytes(DEFAULT_WARMUP_FRAMES, settings);
    }

    /**
     * Captures a raw image and returns it as DNG bytes.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return the DNG file content as a byte array
     * @throws LibCameraException if capture or DNG creation fails
     */
    public static byte[] captureDngBytes(int warmupFrames) {
        return captureDngBytes(warmupFrames, CameraSettings.defaults());
    }

    /**
     * Captures a raw image and returns it as DNG bytes.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for focus
     * @return the DNG file content as a byte array
     * @throws LibCameraException if capture or DNG creation fails
     */
    public static byte[] captureDngBytes(int warmupFrames, CameraSettings settings) {
        try {
            // Create a temporary file for the DNG
            Path tempFile = Files.createTempFile("capture_", ".dng");
            try {
                captureDng(tempFile.toString(), warmupFrames, settings);
                return Files.readAllBytes(tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new LibCameraException("Failed to create DNG: " + e.getMessage(), e);
        }
    }

    /**
     * Asynchronously captures a raw image and returns it as DNG bytes.
     *
     * @return a CompletableFuture that completes with the DNG bytes
     */
    public static CompletableFuture<byte[]> captureDngBytesAsync() {
        return captureDngBytesAsync(DEFAULT_WARMUP_FRAMES, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a raw image and returns it as DNG bytes.
     *
     * @param settings camera settings for focus
     * @return a CompletableFuture that completes with the DNG bytes
     */
    public static CompletableFuture<byte[]> captureDngBytesAsync(CameraSettings settings) {
        return captureDngBytesAsync(DEFAULT_WARMUP_FRAMES, settings);
    }

    /**
     * Asynchronously captures a raw image and returns it as DNG bytes.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @return a CompletableFuture that completes with the DNG bytes
     */
    public static CompletableFuture<byte[]> captureDngBytesAsync(int warmupFrames) {
        return captureDngBytesAsync(warmupFrames, CameraSettings.defaults());
    }

    /**
     * Asynchronously captures a raw image and returns it as DNG bytes.
     *
     * @param warmupFrames number of warm-up frames for auto-exposure
     * @param settings camera settings for focus
     * @return a CompletableFuture that completes with the DNG bytes
     */
    public static CompletableFuture<byte[]> captureDngBytesAsync(int warmupFrames, CameraSettings settings) {
        return CompletableFuture.supplyAsync(() -> captureDngBytes(warmupFrames, settings), executor);
    }

    private static void applyCameraSettings(Request request, CameraSettings settings) {
        // Focus settings
        request.setAfMode(settings.afMode());
        if (settings.afMode() == AfMode.MANUAL) {
            request.setLensPosition(settings.lensPosition());
        }

        // Exposure settings
        request.setAeEnable(settings.autoExposure());
        if (!settings.autoExposure()) {
            request.setExposureTime(settings.exposureTimeMicros());
            request.setAnalogueGain(settings.analogueGain());
        }
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

    private static BufferedImage applyTransform(BufferedImage image, Transform transform) {
        if (transform == Transform.IDENTITY) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // For 90/270 degree rotations, swap dimensions
        boolean transpose = transform.transpose();
        int newWidth = transpose ? height : width;
        int newHeight = transpose ? width : height;

        BufferedImage result = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g2d = result.createGraphics();

        AffineTransform at = new AffineTransform();

        // Move to center, apply transform, move back
        at.translate(newWidth / 2.0, newHeight / 2.0);

        if (transpose) {
            // 90 degree rotation
            at.rotate(Math.PI / 2);
        }
        if (transform.hflip()) {
            at.scale(-1, 1);
        }
        if (transform.vflip()) {
            at.scale(1, -1);
        }

        at.translate(-width / 2.0, -height / 2.0);

        g2d.drawImage(image, at, null);
        g2d.dispose();

        return result;
    }

}
