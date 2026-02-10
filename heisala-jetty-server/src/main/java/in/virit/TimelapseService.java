package in.virit;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Service for managing timelapse images and generating videos.
 */
@ApplicationScoped
public class TimelapseService {

    private static final Logger LOG = Logger.getLogger(TimelapseService.class);
    private static final Path TIMELAPSE_DIR = Path.of("timelapse");
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private boolean ffmpegAvailable;
    private volatile Process currentProcess;
    private volatile boolean cancelled;

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(TIMELAPSE_DIR);
        } catch (IOException e) {
            LOG.error("Failed to create timelapse directory", e);
        }

        // Check if FFmpeg is available
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start();
            int exitCode = process.waitFor();
            ffmpegAvailable = (exitCode == 0);
            if (ffmpegAvailable) {
                LOG.info("FFmpeg detected - timelapse video generation enabled");
            } else {
                LOG.warn("FFmpeg not available - timelapse video generation will be disabled");
            }
        } catch (Exception e) {
            ffmpegAvailable = false;
            LOG.warn("FFmpeg not available - timelapse video generation will be disabled: " + e.getMessage());
        }
    }

    /**
     * Returns whether FFmpeg is available for video generation.
     */
    public boolean isFfmpegAvailable() {
        return ffmpegAvailable;
    }

    /**
     * Cancels the current timelapse generation if one is in progress.
     */
    public void cancelGeneration() {
        cancelled = true;
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
            LOG.info("Timelapse generation cancelled");
        }
    }

    /**
     * Returns whether generation is currently in progress.
     */
    public boolean isGenerating() {
        return currentProcess != null && currentProcess.isAlive();
    }

    /**
     * Record representing a generated timelapse video.
     */
    public record GeneratedVideo(Path path, long sizeBytes, java.time.Instant lastModified) {
        public String getFileName() {
            return path.getFileName().toString();
        }

        public String getFormattedSize() {
            if (sizeBytes < 1024) {
                return sizeBytes + " B";
            } else if (sizeBytes < 1024 * 1024) {
                return String.format("%.1f KB", sizeBytes / 1024.0);
            } else if (sizeBytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
            } else {
                return String.format("%.2f GB", sizeBytes / (1024.0 * 1024 * 1024));
            }
        }
    }

    /**
     * Lists all generated timelapse videos.
     *
     * @return list of generated videos, sorted by modification time (newest first)
     */
    public List<GeneratedVideo> listGeneratedVideos() {
        if (!Files.exists(TIMELAPSE_DIR)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(TIMELAPSE_DIR)) {
            return files
                .filter(p -> p.toString().endsWith(".mp4") || p.toString().endsWith(".avi"))
                .map(p -> {
                    try {
                        return new GeneratedVideo(p, Files.size(p), Files.getLastModifiedTime(p).toInstant());
                    } catch (IOException e) {
                        return null;
                    }
                })
                .filter(v -> v != null)
                .sorted((a, b) -> b.lastModified().compareTo(a.lastModified()))
                .toList();
        } catch (IOException e) {
            LOG.error("Failed to list generated videos", e);
            return List.of();
        }
    }

    /**
     * Deletes a generated video.
     *
     * @param path the path to the video to delete
     * @return true if deleted successfully
     */
    public boolean deleteVideo(Path path) {
        try {
            // Safety check: only delete files in the timelapse directory
            if (!path.startsWith(TIMELAPSE_DIR)) {
                LOG.warn("Attempted to delete file outside timelapse directory: " + path);
                return false;
            }
            Files.deleteIfExists(path);
            LOG.info("Deleted video: " + path);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete video: " + path, e);
            return false;
        }
    }

    /**
     * Saves a JPEG image for timelapse with timestamp filename in year/month/day structure.
     *
     * @param jpeg the JPEG image data
     * @param timestamp the capture timestamp
     */
    public void saveTimelapseImage(byte[] jpeg, LocalDateTime timestamp) {
        // Create directory structure: timelapse/YYYY/MM/DD/
        String year = String.valueOf(timestamp.getYear());
        String month = String.format("%02d", timestamp.getMonthValue());
        String day = String.format("%02d", timestamp.getDayOfMonth());

        Path dayDir = TIMELAPSE_DIR.resolve(year).resolve(month).resolve(day);
        try {
            Files.createDirectories(dayDir);
        } catch (IOException e) {
            LOG.error("Failed to create timelapse directory: " + dayDir, e);
            return;
        }

        String filename = timestamp.format(FILE_FORMAT) + ".jpg";
        Path imagePath = dayDir.resolve(filename);
        try {
            Files.write(imagePath, jpeg);
            LOG.debug("Timelapse image saved: " + imagePath);
        } catch (IOException e) {
            LOG.error("Failed to save timelapse image", e);
        }
    }

    /**
     * Record representing a timelapse image with its timestamp and path.
     */
    public record TimelapseImage(LocalDateTime timestamp, Path path) {
        public String getDisplayTime() {
            return timestamp.format(DISPLAY_FORMAT);
        }
    }

    /**
     * Lists all available timelapse images, sorted by timestamp.
     *
     * @return list of timelapse images
     */
    public List<TimelapseImage> listImages() {
        if (!Files.exists(TIMELAPSE_DIR)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(TIMELAPSE_DIR)) {
            return files
                .filter(p -> p.toString().endsWith(".jpg"))
                .map(this::parseTimelapseImage)
                .filter(img -> img != null)
                .sorted(Comparator.comparing(TimelapseImage::timestamp))
                .toList();
        } catch (IOException e) {
            LOG.error("Failed to list timelapse images", e);
            return List.of();
        }
    }

    /**
     * Lists timelapse images within a date range.
     *
     * @param from start time (inclusive)
     * @param to end time (inclusive)
     * @return list of timelapse images in range
     */
    public List<TimelapseImage> listImages(LocalDateTime from, LocalDateTime to) {
        return listImages().stream()
            .filter(img -> !img.timestamp().isBefore(from) && !img.timestamp().isAfter(to))
            .toList();
    }

    /**
     * Returns the time range of available images.
     *
     * @return array with [earliest, latest] or null if no images
     */
    public LocalDateTime[] getTimeRange() {
        List<TimelapseImage> images = listImages();
        if (images.isEmpty()) {
            return null;
        }
        return new LocalDateTime[] {
            images.get(0).timestamp(),
            images.get(images.size() - 1).timestamp()
        };
    }

    /**
     * Returns the count of available timelapse images.
     */
    public int getImageCount() {
        return listImages().size();
    }

    private TimelapseImage parseTimelapseImage(Path path) {
        try {
            String filename = path.getFileName().toString();
            String timestampStr = filename.replace(".jpg", "");
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr, FILE_FORMAT);
            return new TimelapseImage(timestamp, path);
        } catch (Exception e) {
            LOG.debug("Could not parse timelapse image: " + path);
            return null;
        }
    }

    /**
     * Generates a timelapse video from images in the specified time range using FFmpeg.
     *
     * @param from start time
     * @param to end time
     * @param fps frames per second
     * @param samplingSeconds minimum seconds between images (0 for all images)
     * @param scale resolution scale (e.g., "1920:1080"), or null for original
     * @param bitrate video bitrate (e.g., "8M")
     * @param outputConsumer consumer for FFmpeg output lines (can be null)
     * @return path to the generated video, or null if failed
     */
    public Path generateTimelapse(LocalDateTime from, LocalDateTime to, int fps, int samplingSeconds, String scale, String bitrate, Consumer<String> outputConsumer) {
        cancelled = false;

        List<TimelapseImage> allImages = listImages(from, to);
        if (allImages.isEmpty()) {
            LOG.warn("No images found for timelapse generation");
            if (outputConsumer != null) outputConsumer.accept("No images found for timelapse generation");
            return null;
        }

        // Sample images if interval specified
        List<TimelapseImage> images;
        if (samplingSeconds > 0) {
            images = new java.util.ArrayList<>();
            LocalDateTime lastPicked = null;
            for (TimelapseImage image : allImages) {
                if (lastPicked == null ||
                    java.time.Duration.between(lastPicked, image.timestamp()).getSeconds() >= samplingSeconds) {
                    images.add(image);
                    lastPicked = image.timestamp();
                }
            }
            if (outputConsumer != null) {
                outputConsumer.accept("Sampled " + images.size() + " of " + allImages.size() + " images (1 per " + samplingSeconds + "s)");
            }
        } else {
            images = allImages;
        }

        String message = "Generating timelapse from " + images.size() + " images at " + fps + " fps";
        LOG.info(message);
        if (outputConsumer != null) outputConsumer.accept(message);

        String outputFilename = "timelapse_" + from.format(FILE_FORMAT) + "_to_" + to.format(FILE_FORMAT) + ".mp4";
        Path outputPath = TIMELAPSE_DIR.resolve(outputFilename);

        try {
            // Create a temporary file list for FFmpeg concat demuxer
            Path fileList = Files.createTempFile("timelapse_", ".txt");
            StringBuilder listContent = new StringBuilder();
            for (TimelapseImage image : images) {
                listContent.append("file '").append(image.path().toAbsolutePath()).append("'\n");
            }
            Files.writeString(fileList, listContent.toString());

            if (outputConsumer != null) outputConsumer.accept("Created file list with " + images.size() + " images");

            // Try hardware encoder first (Raspberry Pi), then fall back to software
            int exitCode = tryFfmpeg(fileList, fps, scale, outputPath, outputConsumer,
                // Hardware encoder (Raspberry Pi V4L2 M2M)
                "h264_v4l2m2m", "-b:v", bitrate
            );

            if (exitCode != 0) {
                if (outputConsumer != null) outputConsumer.accept("Hardware encoder failed, trying software encoder...");
                exitCode = tryFfmpeg(fileList, fps, scale, outputPath, outputConsumer,
                    // Software encoder with minimal memory usage
                    "libx264", "-preset", "ultrafast", "-b:v", bitrate, "-threads", "2"
                );
            }

            Files.deleteIfExists(fileList);

            if (exitCode != 0) {
                String errorMsg = "FFmpeg failed with exit code " + exitCode;
                LOG.error(errorMsg);
                if (outputConsumer != null) outputConsumer.accept(errorMsg);
                return null;
            }

            String successMsg = "Timelapse generated: " + outputPath + " (" + images.size() + " frames)";
            LOG.info(successMsg);
            if (outputConsumer != null) outputConsumer.accept(successMsg);
            return outputPath;

        } catch (IOException e) {
            LOG.error("Failed to generate timelapse", e);
            return null;
        }
    }

    /**
     * Deletes timelapse images older than the specified number of days.
     *
     * @param daysToKeep number of days to keep
     * @return number of images deleted
     */
    /**
     * Attempts to run FFmpeg with the specified encoder settings.
     */
    private int tryFfmpeg(Path fileList, int fps, String scale, Path outputPath, Consumer<String> outputConsumer, String... encoderArgs) {
        if (cancelled) {
            if (outputConsumer != null) outputConsumer.accept("Generation cancelled");
            return -1;
        }

        try {
            List<String> command = new java.util.ArrayList<>();
            command.addAll(List.of(
                "ffmpeg", "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", fileList.toString(),
                "-framerate", String.valueOf(fps)
            ));

            // Add scale filter if resolution specified
            if (scale != null && !scale.isEmpty()) {
                command.addAll(List.of("-vf", "scale=" + scale));
            }

            command.addAll(List.of("-c:v"));
            command.addAll(List.of(encoderArgs));
            command.addAll(List.of(
                "-pix_fmt", "yuv420p",
                "-progress", "pipe:1",
                outputPath.toString()
            ));

            if (outputConsumer != null) outputConsumer.accept("Running: " + String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            currentProcess = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.debug("FFmpeg: " + line);
                    if (outputConsumer != null) outputConsumer.accept(line);
                }
            }

            int exitCode = currentProcess.waitFor();
            currentProcess = null;

            if (cancelled) {
                if (outputConsumer != null) outputConsumer.accept("Generation cancelled");
                return -1;
            }

            return exitCode;
        } catch (Exception e) {
            LOG.error("FFmpeg execution failed", e);
            if (outputConsumer != null) outputConsumer.accept("Error: " + e.getMessage());
            return -1;
        }
    }

    public int cleanupOldImages(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        List<TimelapseImage> oldImages = listImages().stream()
            .filter(img -> img.timestamp().isBefore(cutoff))
            .toList();

        int deleted = 0;
        for (TimelapseImage img : oldImages) {
            try {
                Files.delete(img.path());
                deleted++;
            } catch (IOException e) {
                LOG.error("Failed to delete old timelapse image: " + img.path(), e);
            }
        }

        if (deleted > 0) {
            LOG.info("Cleaned up " + deleted + " old timelapse images");
        }
        return deleted;
    }

    /**
     * Thins out images in a time range, keeping every nth image.
     *
     * @param from start time
     * @param to end time
     * @param keepEveryNth keep every nth image, delete the rest
     * @return number of images deleted
     */
    public int thinOutImages(LocalDateTime from, LocalDateTime to, int keepEveryNth) {
        List<TimelapseImage> images = listImages(from, to);
        int deleted = 0;
        int index = 0;

        for (TimelapseImage img : images) {
            if (index % keepEveryNth != 0) {
                try {
                    Files.delete(img.path());
                    deleted++;
                } catch (IOException e) {
                    LOG.error("Failed to delete timelapse image: " + img.path(), e);
                }
            }
            index++;
        }

        if (deleted > 0) {
            LOG.info("Thinned out " + deleted + " timelapse images from " + from + " to " + to);
        }
        return deleted;
    }

    /**
     * Hourly cleanup: thin out images from 1-24 hours ago, keeping every 4th (~1 per minute).
     *
     * At 15s capture rate: 240 images/hour → keeps ~60/hour for recent 24h
     */
    @Scheduled(cron = "0 5 * * * ?")
    void hourlyCleanup() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusHours(24);
        LocalDateTime to = now.minusHours(1);

        int deleted = thinOutImages(from, to, 4);
        if (deleted > 0) {
            LOG.info("Hourly cleanup: removed " + deleted + " images (1-24h old, keeping every 4th)");
        }
    }

    /**
     * Daily cleanup: thin out images from 1-7 days ago, keeping every 15th (~1 per 4 minutes).
     *
     * This preserves enough detail for daily timelapses while reducing storage.
     */
    @Scheduled(cron = "0 15 3 * * ?")
    void dailyCleanup() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusDays(7);
        LocalDateTime to = now.minusDays(1);

        int thinned = thinOutImages(from, to, 15);
        if (thinned > 0) {
            LOG.info("Daily cleanup: thinned out " + thinned + " images (1-7 days old, keeping every 15th)");
        }
    }

    /**
     * Weekly cleanup: thin out images from 7-30 days ago, keeping every 60th (~1 per 15 minutes).
     *
     * Good for weekly/monthly timelapses.
     */
    @Scheduled(cron = "0 30 3 ? * SUN")
    void weeklyCleanup() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusDays(30);
        LocalDateTime to = now.minusDays(7);

        int thinned = thinOutImages(from, to, 60);
        if (thinned > 0) {
            LOG.info("Weekly cleanup: thinned out " + thinned + " images (7-30 days old, keeping every 60th)");
        }
    }

    /**
     * Monthly cleanup: thin out images from 30-365 days ago, keeping every 240th (~1 per hour).
     * Also deletes images older than 2 years.
     *
     * Preserves enough for yearly timelapses: ~8,760 images/year at 1/hour = ~3GB/year
     */
    @Scheduled(cron = "0 45 3 1 * ?")
    void monthlyCleanup() {
        LocalDateTime now = LocalDateTime.now();

        // Delete images older than 2 years
        int deleted = cleanupOldImages(730);
        if (deleted > 0) {
            LOG.info("Monthly cleanup: deleted " + deleted + " images older than 2 years");
        }

        // Thin out images from 30-365 days ago, keeping every 240th (1 per hour)
        LocalDateTime from = now.minusDays(365);
        LocalDateTime to = now.minusDays(30);
        int thinned = thinOutImages(from, to, 240);
        if (thinned > 0) {
            LOG.info("Monthly cleanup: thinned out " + thinned + " images (30-365 days old, keeping every 240th)");
        }
    }
}
