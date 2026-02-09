package in.virit.libcamera4j;

import java.awt.image.BufferedImage;

/**
 * Converts raw pixel data from various formats to BufferedImage.
 */
public class PixelFormatConverter {

    /**
     * Converts raw frame data to a BufferedImage based on the pixel format.
     *
     * @param data the raw pixel data
     * @param width image width
     * @param height image height
     * @param stride bytes per row (may be larger than width for alignment)
     * @param format the pixel format
     * @return the converted BufferedImage
     */
    public static BufferedImage convert(byte[] data, int width, int height, int stride, PixelFormat format) {
        String formatStr = format.fourccString();

        return switch (formatStr) {
            case "YU12", "I420" -> convertYu12(data, width, height, stride);
            case "NV12" -> convertNv12(data, width, height, stride);
            case "YUYV" -> convertYuyv(data, width, height, stride);
            case "RGB3", "RG24" -> convertRgb(data, width, height, stride);
            case "BGR3", "BG24" -> convertBgr(data, width, height, stride);
            default -> throw new LibCameraException("Unsupported pixel format: " + formatStr);
        };
    }

    private static BufferedImage convertYu12(byte[] data, int width, int height, int stride) {
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

                image.setRGB(x, y, yuvToRgb(yVal, u, v));
            }
        }

        return image;
    }

    private static BufferedImage convertNv12(byte[] data, int width, int height, int stride) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
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

        return image;
    }

    private static BufferedImage convertYuyv(byte[] data, int width, int height, int stride) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

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

        return image;
    }

    private static BufferedImage convertRgb(byte[] data, int width, int height, int stride) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

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

        return image;
    }

    private static BufferedImage convertBgr(byte[] data, int width, int height, int stride) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

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

        return image;
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
}
