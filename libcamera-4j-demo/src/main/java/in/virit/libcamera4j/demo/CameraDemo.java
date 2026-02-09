package in.virit.libcamera4j.demo;

import in.virit.libcamera4j.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Demo application showing basic libcamera-4j usage.
 * Captures a single frame and saves it as a JPEG image.
 */
public class CameraDemo {

    private static final String OUTPUT_DIR = System.getProperty("user.home");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static void main(String[] args) {
        System.out.println("libcamera-4j Demo");
        System.out.println("=================");
        System.out.println("libcamera version: " + CameraManager.version());
        System.out.println();

        try (CameraManager manager = CameraManager.create()) {
            System.out.println("Starting camera manager...");
            manager.start();

            List<Camera> cameras = manager.cameras();
            System.out.println("Found " + cameras.size() + " camera(s)");

            if (cameras.isEmpty()) {
                System.out.println("No cameras found. Make sure:");
                System.out.println("  - libcamera is installed: sudo apt install libcamera-dev");
                System.out.println("  - Camera is connected and enabled");
                System.out.println("  - You have permissions (try: sudo usermod -aG video $USER)");
                return;
            }

            // List all cameras
            for (int i = 0; i < cameras.size(); i++) {
                Camera camera = cameras.get(i);
                System.out.println("Camera " + i + ": " + camera.id());
            }

            // Capture with first camera
            Camera camera = cameras.get(0);
            captureAndSaveJpeg(camera);

        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native library not found!");
            System.err.println("Make sure libcamera4j.so is built and accessible.");
            System.err.println("Build with: cd libcamera-4j/src/main/native && ./build.sh");
            System.err.println();
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void captureAndSaveJpeg(Camera camera) {
        System.out.println();
        System.out.println("Capturing image with camera: " + camera.id());

        camera.acquire();
        try {
            // Generate configuration for still capture
            CameraConfiguration config = camera.generateConfiguration(StreamRole.STILL_CAPTURE);
            if (config.isEmpty()) {
                // Fall back to viewfinder
                config = camera.generateConfiguration(StreamRole.VIEWFINDER);
            }

            if (config.isEmpty()) {
                System.out.println("Could not generate camera configuration");
                return;
            }

            // Configure the stream
            StreamConfiguration streamConfig = config.get(0);
            System.out.println("Default config: " + streamConfig);

            // Request a reasonable resolution
            streamConfig.setSize(new Size(1920, 1080));
            streamConfig.setBufferCount(2);

            // Validate and apply
            CameraConfiguration.Status status = config.validate();
            System.out.println("After validation: " + streamConfig);
            System.out.println("Validation status: " + status);

            if (status == CameraConfiguration.Status.INVALID) {
                System.out.println("Configuration is invalid");
                return;
            }

            camera.configure(config);
            System.out.println("Camera configured");

            // Allocate buffers
            try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                int bufferCount = allocator.allocate(0);
                System.out.println("Allocated " + bufferCount + " buffers");

                // Create request with buffer
                Request request = camera.createRequest();
                request.addBuffer(0, 0, allocator);

                // Start camera
                camera.start();
                System.out.println("Camera started, warming up...");

                // Capture warm-up frames to let auto-exposure stabilize
                int warmupFrames = 10;
                for (int i = 0; i < warmupFrames; i++) {
                    camera.queueRequest(request);

                    // Wait for completion
                    int timeout = 5000;
                    int waited = 0;
                    while (request.status() == Request.Status.PENDING && waited < timeout) {
                        Thread.sleep(50);
                        waited += 50;
                    }

                    if (i < warmupFrames - 1) {
                        // Reuse request for next frame (except last one which we'll save)
                        request.reuse();
                    }
                }
                System.out.println("Capture complete!");

                camera.stop();

                if (request.status() == Request.Status.COMPLETE) {
                    System.out.println("Frame sequence: " + request.getSequence(0));
                    System.out.println("Timestamp: " + request.getTimestamp(0) + " ns");

                    // Get buffer data and save as JPEG
                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    saveAsJpeg(buffer, streamConfig);
                } else {
                    System.out.println("Capture failed or timed out. Status: " + request.status());
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Capture interrupted");
        } finally {
            camera.release();
        }
    }

    private static void saveAsJpeg(FrameBuffer buffer, StreamConfiguration streamConfig) {
        System.out.println("Processing captured frame...");

        Size size = streamConfig.size();
        PixelFormat format = streamConfig.pixelFormat();
        int stride = streamConfig.stride();

        System.out.println("Frame size: " + size);
        System.out.println("Pixel format: " + format);
        System.out.println("Stride: " + stride);

        try {
            byte[] data = buffer.getData();
            System.out.println("Buffer size: " + data.length + " bytes");

            BufferedImage image = convertToBufferedImage(data, size, stride, format);

            // Generate filename
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename = "libcamera4j_" + timestamp + ".jpg";
            File outputFile = new File(OUTPUT_DIR, filename);

            // Save as JPEG
            ImageIO.write(image, "jpg", outputFile);
            System.out.println();
            System.out.println("Image saved: " + outputFile.getAbsolutePath());
            System.out.println("Resolution: " + image.getWidth() + "x" + image.getHeight());

        } catch (IOException e) {
            System.err.println("Failed to save image: " + e.getMessage());
        }
    }

    private static BufferedImage convertToBufferedImage(byte[] data, Size size, int stride, PixelFormat format) {
        int width = size.width();
        int height = size.height();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        String formatStr = format.fourccString();
        System.out.println("Converting format: " + formatStr);

        // Handle common formats
        if (formatStr.equals("YUYV")) {
            convertYuyv(data, image, width, height, stride);
        } else if (formatStr.equals("NV12")) {
            convertNv12(data, image, width, height, stride);
        } else if (formatStr.equals("YU12") || formatStr.equals("I420")) {
            // YU12/I420 is YUV420 planar (3 separate planes: Y, U, V)
            convertYu12(data, image, width, height, stride);
        } else if (formatStr.startsWith("RGB") || formatStr.equals("RG24")) {
            convertRgb(data, image, width, height, stride);
        } else if (formatStr.startsWith("BGR") || formatStr.equals("BG24")) {
            convertBgr(data, image, width, height, stride);
        } else {
            System.out.println("Unknown format, creating test pattern");
            createTestPattern(image, width, height);
        }

        return image;
    }

    private static void convertYuyv(byte[] data, BufferedImage image, int width, int height, int stride) {
        for (int y = 0; y < height; y++) {
            int rowOffset = y * stride;
            for (int x = 0; x < width; x += 2) {
                int offset = rowOffset + x * 2;
                if (offset + 3 >= data.length) break;

                int y0 = data[offset] & 0xFF;
                int u = (data[offset + 1] & 0xFF) - 128;
                int y1 = (offset + 2 < data.length) ? (data[offset + 2] & 0xFF) : y0;
                int v = (data[offset + 3] & 0xFF) - 128;

                image.setRGB(x, y, yuvToRgb(y0, u, v));
                if (x + 1 < width) {
                    image.setRGB(x + 1, y, yuvToRgb(y1, u, v));
                }
            }
        }
    }

    private static void convertNv12(byte[] data, BufferedImage image, int width, int height, int stride) {
        int yPlaneSize = stride * height;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yOffset = y * stride + x;
                int uvOffset = yPlaneSize + (y / 2) * stride + (x / 2) * 2;

                if (yOffset >= data.length || uvOffset + 1 >= data.length) continue;

                int yVal = data[yOffset] & 0xFF;
                int u = (data[uvOffset] & 0xFF) - 128;
                int v = (data[uvOffset + 1] & 0xFF) - 128;

                image.setRGB(x, y, yuvToRgb(yVal, u, v));
            }
        }
    }

    private static void convertYu12(byte[] data, BufferedImage image, int width, int height, int stride) {
        // YU12/I420: Y plane, then U plane (1/4 size), then V plane (1/4 size)
        int yPlaneSize = stride * height;
        int uvStride = stride / 2;
        int uvHeight = height / 2;
        int uPlaneOffset = yPlaneSize;
        int vPlaneOffset = yPlaneSize + uvStride * uvHeight;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yOffset = y * stride + x;
                int uvX = x / 2;
                int uvY = y / 2;
                int uOffset = uPlaneOffset + uvY * uvStride + uvX;
                int vOffset = vPlaneOffset + uvY * uvStride + uvX;

                if (yOffset >= data.length || uOffset >= data.length || vOffset >= data.length) continue;

                int yVal = data[yOffset] & 0xFF;
                int u = (data[uOffset] & 0xFF) - 128;
                int v = (data[vOffset] & 0xFF) - 128;

                image.setRGB(x, y, yuvToRgb(yVal, u, v));
            }
        }
    }

    private static void convertRgb(byte[] data, BufferedImage image, int width, int height, int stride) {
        for (int y = 0; y < height; y++) {
            int rowOffset = y * stride;
            for (int x = 0; x < width; x++) {
                int offset = rowOffset + x * 3;
                if (offset + 2 >= data.length) break;

                int r = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int b = data[offset + 2] & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
    }

    private static void convertBgr(byte[] data, BufferedImage image, int width, int height, int stride) {
        for (int y = 0; y < height; y++) {
            int rowOffset = y * stride;
            for (int x = 0; x < width; x++) {
                int offset = rowOffset + x * 3;
                if (offset + 2 >= data.length) break;

                int b = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int r = data[offset + 2] & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
    }

    private static int yuvToRgb(int y, int u, int v) {
        int r = clamp((int) (y + 1.402 * v));
        int g = clamp((int) (y - 0.344136 * u - 0.714136 * v));
        int b = clamp((int) (y + 1.772 * u));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void createTestPattern(BufferedImage image, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / width;
                int g = (y * 255) / height;
                int b = 128;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
    }
}
