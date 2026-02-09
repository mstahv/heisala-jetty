package in.virit;

import in.virit.libcamera4j.*;
import jakarta.enterprise.context.ApplicationScoped;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@ApplicationScoped
public class CameraService {

    private static final int WARMUP_FRAMES = 10;

    public byte[] captureJpeg(int width, int height) throws IOException {
        try (CameraManager manager = CameraManager.create()) {
            manager.start();

            List<Camera> cameras = manager.cameras();
            if (cameras.isEmpty()) {
                throw new IOException("No cameras found");
            }

            Camera camera = cameras.get(0);
            camera.acquire();

            try {
                CameraConfiguration config = camera.generateConfiguration(StreamRole.STILL_CAPTURE);
                if (config.isEmpty()) {
                    config = camera.generateConfiguration(StreamRole.VIEWFINDER);
                }

                StreamConfiguration streamConfig = config.get(0);
                streamConfig.setSize(new Size(width, height));
                streamConfig.setBufferCount(2);
                config.validate();
                camera.configure(config);

                try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
                    allocator.allocate(0);

                    Request request = camera.createRequest();
                    request.addBuffer(0, 0, allocator);

                    camera.start();

                    // Warm-up frames for auto-exposure
                    for (int i = 0; i < WARMUP_FRAMES; i++) {
                        camera.queueRequest(request);
                        waitForRequest(request);
                        if (i < WARMUP_FRAMES - 1) {
                            request.reuse();
                        }
                    }

                    camera.stop();

                    if (request.status() != Request.Status.COMPLETE) {
                        throw new IOException("Capture failed: " + request.status());
                    }

                    FrameBuffer buffer = new FrameBuffer(allocator, config, 0, 0);
                    byte[] rawData = buffer.getData();

                    BufferedImage image = convertYu12ToImage(
                        rawData,
                        streamConfig.size().width(),
                        streamConfig.size().height(),
                        streamConfig.stride()
                    );

                    return toJpegBytes(image);
                }
            } finally {
                camera.release();
            }
        }
    }

    private void waitForRequest(Request request) {
        int timeout = 5000;
        int waited = 0;
        while (request.status() == Request.Status.PENDING && waited < timeout) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            waited += 50;
        }
    }

    private BufferedImage convertYu12ToImage(byte[] data, int width, int height, int stride) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

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

                int r = clamp((int) (yVal + 1.402 * v));
                int g = clamp((int) (yVal - 0.344136 * u - 0.714136 * v));
                int b = clamp((int) (yVal + 1.772 * u));

                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return image;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
}
