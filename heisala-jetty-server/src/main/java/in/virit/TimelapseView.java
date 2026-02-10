package in.virit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

@Route(value = "timelapse", layout = TopLayout.class)
@Menu(title = "Timelapse", icon = "vaadin:film", order = 3)
public class TimelapseView extends VerticalLayout {

    private static final String RETENTION_POLICY_HELP = """
        ## Image Retention Policy

        Images are captured every **15 seconds** and automatically managed to optimize storage while preserving enough detail for timelapses at various time scales.

        ### Storage Timeline

        | Image Age | Resolution Kept | Images | Storage | Use Case |
        |-----------|-----------------|--------|---------|----------|
        | 0-1 hour | Every 15 sec (all) | ~240 | ~80 MB | Real-time monitoring |
        | 1-24 hours | ~1 per minute | ~1,400 | ~460 MB | Hourly timelapses |
        | 1-7 days | ~1 per 4 min | ~2,200 | ~700 MB | Daily timelapses |
        | 7-30 days | ~1 per 15 min | ~2,200 | ~750 MB | Weekly timelapses |
        | 30-365 days | ~1 per hour | ~8,000 | ~2.7 GB | Monthly/yearly timelapses |
        | >2 years | Deleted | - | - | - |

        **Total estimated storage: ~5 GB** (based on ~340 KB per image)

        ### Cleanup Schedule

        - **Hourly** (at :05): Thins out images 1-24 hours old
        - **Daily** (3:15 AM): Thins out images 1-7 days old
        - **Weekly** (Sunday 3:30 AM): Thins out images 7-30 days old
        - **Monthly** (1st, 3:45 AM): Thins out images 30-365 days old, deletes >2 years

        ### Yearly Timelapse

        With ~1 image per hour for the past year, you get:
        - ~8,760 images per year
        - ~5 min video at 30 fps
        - ~10 min video at 15 fps
        - ~3 GB storage per year

        ### Tips

        - For best results, select a time range that matches the available resolution
        - Lower FPS (5-10) works well for longer timelapses (weeks/months)
        - Higher FPS (24-30) is better for shorter periods (hours/days)
        """;

    private final TimelapseService timelapseService;

    private final DateTimePicker fromPicker = new DateTimePicker("From");
    private final DateTimePicker toPicker = new DateTimePicker("To");
    private final Select<Integer> fpsSelect = new Select<>();
    private final Select<SamplingInterval> samplingSelect = new Select<>();
    private final Select<VideoResolution> resolutionSelect = new Select<>();
    private final Select<VideoQuality> qualitySelect = new Select<>();
    private final Button generateButton = new Button("Generate Timelapse");
    private final Button cancelButton = new Button("Cancel");
    private final Span statusLabel = new Span();

    /** Sampling interval options (how often to pick images) */
    public record SamplingInterval(String label, int seconds) {
        @Override public String toString() { return label; }
    }
    private static final SamplingInterval[] SAMPLING_INTERVALS = {
        new SamplingInterval("All images", 0),
        new SamplingInterval("1 per minute", 60),
        new SamplingInterval("1 per 5 min", 300),
        new SamplingInterval("1 per 15 min", 900),
        new SamplingInterval("1 per hour", 3600)
    };

    /** Video resolution options */
    public record VideoResolution(String label, String scale) {
        @Override public String toString() { return label; }
    }
    private static final VideoResolution[] RESOLUTIONS = {
        new VideoResolution("Original", null),
        new VideoResolution("1080p", "1920:1080"),
        new VideoResolution("720p", "1280:720"),
        new VideoResolution("480p", "854:480")
    };

    /** Video quality/bitrate options */
    public record VideoQuality(String label, String bitrate, int bitrateKbps) {
        @Override public String toString() { return label; }
    }
    private static final VideoQuality[] QUALITIES = {
        new VideoQuality("Low (2 Mbps)", "2M", 2000),
        new VideoQuality("Medium (5 Mbps)", "5M", 5000),
        new VideoQuality("High (8 Mbps)", "8M", 8000),
        new VideoQuality("Very High (12 Mbps)", "12M", 12000)
    };
    private final Pre ffmpegOutput = new Pre();
    private final SystemMonitor systemMonitor = new SystemMonitor();
    private final VerticalLayout downloadArea = new VerticalLayout();
    private final VerticalLayout existingVideosArea = new VerticalLayout();

    private Path generatedVideoPath;

    @Inject
    public TimelapseView(TimelapseService timelapseService) {
        this.timelapseService = timelapseService;

        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(Alignment.CENTER);
        header.add(new H1("Timelapse Generator"), new HelpButton(RETENTION_POLICY_HELP));
        add(header);

        // Stats
        int imageCount = timelapseService.getImageCount();
        LocalDateTime[] range = timelapseService.getTimeRange();

        if (imageCount == 0) {
            add(new Paragraph("No timelapse images available yet. Images are captured automatically every 15 seconds."));
        } else {
            add(new Paragraph(String.format(
                "%d images available from %s to %s",
                imageCount,
                range[0].format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                range[1].format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            )));

            // Time range pickers
            fromPicker.setLocale(Locale.UK);
            fromPicker.setValue(range[0]);
            fromPicker.setMin(range[0]);
            fromPicker.setMax(range[1]);

            toPicker.setLocale(Locale.UK);
            toPicker.setValue(range[1]);
            toPicker.setMin(range[0]);
            toPicker.setMax(range[1]);

            // FPS selector
            fpsSelect.setLabel("Frames per second");
            fpsSelect.setItems(5, 10, 15, 24, 30);
            fpsSelect.setValue(15);
            fpsSelect.setItemLabelGenerator(fps -> fps + " fps");

            // Sampling selector (how often to pick images)
            samplingSelect.setLabel("Use images");
            samplingSelect.setItems(SAMPLING_INTERVALS);
            samplingSelect.setValue(SAMPLING_INTERVALS[0]); // All images

            // Resolution selector
            resolutionSelect.setLabel("Resolution");
            resolutionSelect.setItems(RESOLUTIONS);
            resolutionSelect.setValue(RESOLUTIONS[0]); // Original

            // Quality selector
            qualitySelect.setLabel("Quality");
            qualitySelect.setItems(QUALITIES);
            qualitySelect.setValue(QUALITIES[2]); // High (8 Mbps)

            // Generate button
            generateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            generateButton.addClickListener(e -> generateTimelapse());
            generateButton.setEnabled(timelapseService.isFfmpegAvailable());

            // Cancel button
            cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
            cancelButton.addClickListener(e -> cancelGeneration());
            cancelButton.setVisible(false);

            if (!timelapseService.isFfmpegAvailable()) {
                add(new Paragraph("FFmpeg not available - video generation is disabled. Install with: sudo apt install ffmpeg"));
            }

            statusLabel.getStyle().setColor("var(--lumo-secondary-text-color)");

            // FFmpeg output area
            ffmpegOutput.setVisible(false);
            ffmpegOutput.setWidth("100%");
            ffmpegOutput.setMaxWidth("600px");
            ffmpegOutput.setMaxHeight("300px");
            ffmpegOutput.getStyle()
                .setOverflow(com.vaadin.flow.dom.Style.Overflow.AUTO)
                .setBackground("var(--lumo-contrast-5pct)")
                .setPadding("var(--lumo-space-s)")
                .setFontSize("var(--lumo-font-size-xs)");

            // System monitor (next to log)
            systemMonitor.setVisible(false);

            HorizontalLayout outputArea = new HorizontalLayout(ffmpegOutput, systemMonitor);
            outputArea.setAlignItems(Alignment.STRETCH);
            outputArea.setWidthFull();
            outputArea.setMaxWidth("850px");

            // Layout
            HorizontalLayout timeRange = new HorizontalLayout(fromPicker, toPicker);
            timeRange.setAlignItems(Alignment.END);

            HorizontalLayout controls = new HorizontalLayout(fpsSelect, samplingSelect, resolutionSelect, qualitySelect, generateButton, cancelButton);
            controls.setAlignItems(Alignment.END);
            controls.getStyle().setFlexWrap(com.vaadin.flow.dom.Style.FlexWrap.WRAP);

            downloadArea.setPadding(false);
            downloadArea.setSpacing(false);

            // Existing videos section
            existingVideosArea.setPadding(false);
            existingVideosArea.setSpacing(false);
            existingVideosArea.setWidth("100%");
            existingVideosArea.setMaxWidth("600px");

            add(timeRange, controls, statusLabel, downloadArea, outputArea);
            add(new H2("Generated Videos"));
            add(existingVideosArea);

            // Update estimates when settings change
            fromPicker.addValueChangeListener(e -> updateImageCount());
            toPicker.addValueChangeListener(e -> updateImageCount());
            fpsSelect.addValueChangeListener(e -> updateImageCount());
            samplingSelect.addValueChangeListener(e -> updateImageCount());
            qualitySelect.addValueChangeListener(e -> updateImageCount());
            updateImageCount();
            refreshExistingVideos();
        }

        setSizeFull();
        setAlignItems(Alignment.CENTER);
    }

    private void refreshExistingVideos() {
        existingVideosArea.removeAll();
        var videos = timelapseService.listGeneratedVideos();

        if (videos.isEmpty()) {
            existingVideosArea.add(new Span("No videos generated yet."));
            return;
        }

        for (var video : videos) {
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(Alignment.CENTER);
            row.setWidthFull();

            Anchor downloadLink = new Anchor();
            downloadLink.setText(video.getFileName() + " (" + video.getFormattedSize() + ")");
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.setHref(event -> {
                try {
                    event.setFileName(video.getFileName());
                    event.setContentType(video.getFileName().endsWith(".mp4") ? "video/mp4" : "video/x-msvideo");
                    event.setContentLength(video.sizeBytes());
                    Files.copy(video.path(), event.getOutputStream());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to stream video", e);
                }
            });

            Button deleteButton = new Button(VaadinIcon.TRASH.create(), e -> {
                if (timelapseService.deleteVideo(video.path())) {
                    Notification.show("Deleted " + video.getFileName(), 2000, Notification.Position.BOTTOM_START);
                    refreshExistingVideos();
                } else {
                    Notification.show("Failed to delete " + video.getFileName(), 3000, Notification.Position.MIDDLE);
                }
            });
            deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteButton.setTooltipText("Delete video");

            row.add(downloadLink, deleteButton);
            row.setFlexGrow(1, downloadLink);
            existingVideosArea.add(row);
        }
    }

    private void updateImageCount() {
        LocalDateTime from = fromPicker.getValue();
        LocalDateTime to = toPicker.getValue();
        Integer fps = fpsSelect.getValue();
        VideoQuality quality = qualitySelect.getValue();
        SamplingInterval sampling = samplingSelect.getValue();
        if (from != null && to != null && fps != null) {
            var allImages = timelapseService.listImages(from, to);
            int totalCount = allImages.size();
            var sampledImages = sampleImages(allImages, sampling);
            int usedCount = sampledImages.size();

            double durationSec = (double) usedCount / fps;
            // Estimate file size based on bitrate: bitrate (kbps) / 8 = KB/s
            int bitrateKbps = quality != null ? quality.bitrateKbps() : 8000;
            double fileSizeKB = durationSec * bitrateKbps / 8 * 0.4;
            double fileSizeMB = fileSizeKB / 1024;

            String duration = formatDuration(durationSec);
            String fileSize = fileSizeMB < 1 ? String.format("%.0f KB", fileSizeKB)
                                             : String.format("%.1f MB", fileSizeMB);

            if (usedCount < totalCount) {
                statusLabel.setText(String.format("%d of %d images → %s video (~%s)", usedCount, totalCount, duration, fileSize));
            } else {
                statusLabel.setText(String.format("%d images → %s video (~%s)", usedCount, duration, fileSize));
            }
        }
    }

    /**
     * Samples images based on the selected interval.
     */
    private java.util.List<TimelapseService.TimelapseImage> sampleImages(
            java.util.List<TimelapseService.TimelapseImage> images, SamplingInterval sampling) {
        if (sampling == null || sampling.seconds() == 0 || images.isEmpty()) {
            return images;
        }

        java.util.List<TimelapseService.TimelapseImage> sampled = new java.util.ArrayList<>();
        LocalDateTime lastPicked = null;

        for (var image : images) {
            if (lastPicked == null ||
                java.time.Duration.between(lastPicked, image.timestamp()).getSeconds() >= sampling.seconds()) {
                sampled.add(image);
                lastPicked = image.timestamp();
            }
        }

        return sampled;
    }

    private String formatDuration(double seconds) {
        if (seconds < 60) {
            return String.format("%.1f sec", seconds);
        } else if (seconds < 3600) {
            int min = (int) (seconds / 60);
            int sec = (int) (seconds % 60);
            return String.format("%d:%02d min", min, sec);
        } else {
            int hours = (int) (seconds / 3600);
            int min = (int) ((seconds % 3600) / 60);
            return String.format("%d:%02d hours", hours, min);
        }
    }

    private String formatFileSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024));
            } else {
                return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
            }
        } catch (Exception e) {
            return "unknown size";
        }
    }

    private void generateTimelapse() {
        LocalDateTime from = fromPicker.getValue();
        LocalDateTime to = toPicker.getValue();
        Integer fps = fpsSelect.getValue();

        if (from == null || to == null || fps == null) {
            Notification.show("Please select time range and FPS", 3000, Notification.Position.MIDDLE);
            return;
        }

        if (from.isAfter(to)) {
            Notification.show("Start time must be before end time", 3000, Notification.Position.MIDDLE);
            return;
        }

        int imageCount = timelapseService.listImages(from, to).size();
        if (imageCount == 0) {
            Notification.show("No images in selected range", 3000, Notification.Position.MIDDLE);
            return;
        }

        VideoResolution resolution = resolutionSelect.getValue();
        VideoQuality quality = qualitySelect.getValue();
        SamplingInterval sampling = samplingSelect.getValue();
        String scale = resolution != null ? resolution.scale() : null;
        String bitrate = quality != null ? quality.bitrate() : "8M";
        int bitrateKbps = quality != null ? quality.bitrateKbps() : 8000;
        int samplingSeconds = sampling != null ? sampling.seconds() : 0;

        generateButton.setEnabled(false);
        cancelButton.setVisible(true);
        cancelButton.setEnabled(true);
        ffmpegOutput.setVisible(true);
        ffmpegOutput.setText("");
        systemMonitor.setVisible(true);
        // Calculate sampled image count for estimates
        var allImages = timelapseService.listImages(from, to);
        var sampledImages = sampleImages(allImages, sampling);
        int usedCount = sampledImages.size();

        statusLabel.setText("Generating timelapse from " + usedCount + " images...");
        downloadArea.removeAll();

        // Calculate output path and estimated file size for progress tracking
        String outputFilename = "timelapse/timelapse_" + from.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            + "_to_" + to.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".mp4";
        // Estimate file size based on bitrate: bitrate (kbps) / 8 = KB/s
        double durationSec = (double) usedCount / fps;
        long estimatedSizeBytes = (long) (durationSec * bitrateKbps / 8 * 0.6 * 1024);
        systemMonitor.setMonitoredFile(Path.of(outputFilename), estimatedSizeBytes);

        // Run in background
        getUI().ifPresent(ui -> {
            Thread.startVirtualThread(() -> {
                try {
                    // Consumer that appends output to the Pre component
                    StringBuilder outputBuffer = new StringBuilder();
                    Path videoPath = timelapseService.generateTimelapse(from, to, fps, samplingSeconds, scale, bitrate, line -> {
                        outputBuffer.append(line).append("\n");
                        ui.access(() -> ffmpegOutput.setText(outputBuffer.toString()));
                    });

                    ui.access(() -> {
                                                generateButton.setEnabled(true);
                        cancelButton.setVisible(false);
                        systemMonitor.setMonitoredFile(null);

                        if (videoPath != null && Files.exists(videoPath)) {
                            generatedVideoPath = videoPath;
                            statusLabel.setText("Timelapse generated successfully!");

                            String filename = videoPath.getFileName().toString();
                            String contentType = filename.endsWith(".mp4") ? "video/mp4" : "video/x-msvideo";
                            String fileSize = formatFileSize(videoPath);

                            Anchor downloadLink = new Anchor();
                            downloadLink.setText("Download " + filename + " (" + fileSize + ")");
                            downloadLink.getElement().setAttribute("download", true);
                            downloadLink.setHref(downloadEvent -> {
                                try {
                                    downloadEvent.setFileName(generatedVideoPath.getFileName().toString());
                                    downloadEvent.setContentType(contentType);
                                    downloadEvent.setContentLength(Files.size(generatedVideoPath));
                                    Files.copy(generatedVideoPath, downloadEvent.getOutputStream());
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to stream video file", e);
                                }
                            });
                            downloadLink.getStyle()
                                .setFontSize("var(--lumo-font-size-l)")
                                .setMarginTop("var(--lumo-space-m)");

                            downloadArea.add(downloadLink);
                            refreshExistingVideos();
                        } else {
                            statusLabel.setText("Failed to generate timelapse");
                            Notification.show("Failed to generate timelapse", 3000, Notification.Position.MIDDLE);
                        }
                    });
                } catch (Exception e) {
                    ui.access(() -> {
                                                generateButton.setEnabled(true);
                        cancelButton.setVisible(false);
                        systemMonitor.setMonitoredFile(null);
                        statusLabel.setText("Error: " + e.getMessage());
                        Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
                    });
                }
            });
        });
    }

    private void cancelGeneration() {
        timelapseService.cancelGeneration();
        statusLabel.setText("Cancelling...");
        cancelButton.setEnabled(false);
    }
}
