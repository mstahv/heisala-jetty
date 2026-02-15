package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;
import org.vaadin.firitin.form.BeanValidationForm;
import in.virit.TimelapseSettings.*;
import org.vaadin.firitin.rad.PrettyPrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
        | 0-48 hours | Every 15 sec (all) | ~11,520 | ~3.4 GB | Real-time monitoring |
        | 48-96 hours | ~1 per minute | ~2,880 | ~0.9 GB | Short-term timelapses |
        | >1 week | ~1 per 5 minutes | ~105,120 | ~32 GB | Long-term timelapses |
        | >2 years | Deleted | - | - | - |

        **Total estimated storage: ~36 GB per year** (based on ~300 KB per image)

        ### Cleanup Schedule

        - **Nightly** (3:00 AM): Ensures max 1 image per minute for 48-96 hour old photos
        - **Nightly** (3:15 AM): Ensures max 1 image per 5 minutes for photos older than 1 week
        - **Monthly** (1st, 3:30 AM): Deletes images older than 2 years

        ### Yearly Timelapse

        With the new storage strategy:
        - ~105,120 images per year (1 image every 5 minutes)
        - ~60 min video at 30 fps
        - ~120 min video at 15 fps
        - ~32 GB storage per year
        
        This provides much higher detail for yearly timelapses while still being manageable.

        ### Tips

        - For best results, select a time range that matches the available resolution
        - Lower FPS (5-10) works well for longer timelapses (weeks/months)
        - Higher FPS (24-30) is better for shorter periods (hours/days)
        """;

    private final TimelapseService timelapseService;
    private final TimelapseSettingsForm form;
    private final Button generateButton = new Button("Generate Timelapse");
    private final Button cancelButton = new Button("Cancel");
    private final Span statusLabel = new Span();
    private final Pre ffmpegOutput = new Pre();
    private final SystemMonitor systemMonitor = new SystemMonitor();
    private final VerticalLayout downloadArea = new VerticalLayout();
    private final VerticalLayout existingVideosArea = new VerticalLayout();

    private Path generatedVideoPath;

    @Inject
    public TimelapseView(TimelapseService timelapseService) {
        this.timelapseService = timelapseService;

        // Create the form with TimelapseSettings DTO
        form = new TimelapseSettingsForm();
        
        // Hide the form's default save button immediately
        form.getSaveButton().setVisible(false);

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

            // Configure form fields
            configureFormFields(range);

            // Use our custom generate button (form's save button already hidden)

            // Generate button
            generateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            generateButton.setText("Generate Timelapse");  // Changed from "Save" to "Generate"
            generateButton.setEnabled(timelapseService.isFfmpegAvailable());
            generateButton.addClickListener(e -> {
                if (form.isValid()) {
                    generateTimelapse(form.getSettings());
                } else {
                    Notification.show("Please fix validation errors before generating", 3000, Notification.Position.MIDDLE);
                }
            });

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
            HorizontalLayout controls = new HorizontalLayout(generateButton, cancelButton);
            controls.setAlignItems(Alignment.END);
            controls.getStyle().setFlexWrap(com.vaadin.flow.dom.Style.FlexWrap.WRAP);

            downloadArea.setPadding(false);
            downloadArea.setSpacing(false);

            // Existing videos section
            existingVideosArea.setPadding(false);
            existingVideosArea.setSpacing(false);
            existingVideosArea.setWidth("100%");
            existingVideosArea.setMaxWidth("600px");

            add(form, controls, statusLabel, downloadArea, outputArea);
            add(new H2("Generated Videos"));
            add(existingVideosArea);

            refreshExistingVideos();
        }

        setSizeFull();
        setAlignItems(Alignment.CENTER);
    }

    private void configureFormFields(LocalDateTime[] range) {
        // Set default values for the current day
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.of(23, 59, 59));
        
        // Ensure the default values are within the available range
        LocalDateTime defaultFrom = startOfDay.isBefore(range[0]) ? range[0] : startOfDay;
        LocalDateTime defaultTo = endOfDay.isAfter(range[1]) ? range[1] : endOfDay;
        
        // If the default range is invalid (from > to), use the full available range
        if (defaultFrom.isAfter(defaultTo)) {
            defaultFrom = range[0];
            defaultTo = range[1];
        }

        // Configure the form - field names now match DTO property names for automatic binding
        form.setDateRange(range[0], range[1], defaultFrom, defaultTo);
        
        // Create and fully configure the default settings DTO first
        TimelapseSettings defaultSettings = new TimelapseSettings();
        defaultSettings.setFrom(defaultFrom);
        defaultSettings.setTo(defaultTo);
        defaultSettings.setFps(15);
        defaultSettings.setSamplingInterval(TimelapseSettings.SAMPLING_INTERVALS[0]);
        defaultSettings.setResolution(TimelapseSettings.RESOLUTIONS[0]);
        defaultSettings.setQuality(TimelapseSettings.QUALITIES[2]);

        // Configure the form with date range constraints
        form.setDateRange(range[0], range[1], defaultFrom, defaultTo);
        
        // Set the fully configured entity to the form
        form.setEntity(defaultSettings);
        
        // Add value change listener to the binder - this will automatically detect all field changes
        form.getBinder().addValueChangeListener(e -> updateImageCount());

        form.getBinder().addValueChangeListener(e -> {
            System.out.println("Value change event: " +
            PrettyPrinter.printOneLiner(e.getValue(), 300));
        });
        // Show initial estimates with default values
        updateImageCount();
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
        TimelapseSettings settings = form.getSettings();
        if (settings != null && settings.getFrom() != null && settings.getTo() != null) {
            var allImages = timelapseService.listImages(settings.getFrom(), settings.getTo());
            int totalCount = allImages.size();
            var sampledImages = sampleImages(allImages, settings.getSamplingInterval());
            int usedCount = sampledImages.size();
            Integer fps = settings.getFps();
            TimelapseSettings.VideoQuality quality = settings.getQuality();

            if (fps != null && usedCount > 0) {
                double durationSec = (double) usedCount / fps;
                
                // Calculate natural duration (actual time period covered)
                long naturalDurationSec = java.time.Duration.between(settings.getFrom(), settings.getTo()).getSeconds();
                double speedRatio = naturalDurationSec / durationSec;
                
                // Estimate file size based on bitrate: bitrate (kbps) / 8 = KB/s
                int bitrateKbps = quality != null ? quality.bitrateKbps() : 8000;
                double fileSizeKB = durationSec * bitrateKbps / 8 * 0.4;
                double fileSizeMB = fileSizeKB / 1024;

                String duration = formatDuration(durationSec);
                String fileSize = fileSizeMB < 1 ? String.format("%.0f KB", fileSizeKB)
                                                 : String.format("%.1f MB", fileSizeMB);
                String naturalDuration = formatDuration(naturalDurationSec);
                String speedComparison = String.format("%.0fx faster than real time", speedRatio);

                if (usedCount < totalCount) {
                    statusLabel.setText(String.format("%d of %d images → %s video (~%s, %s, covering %s)", 
                        usedCount, totalCount, duration, fileSize, speedComparison, naturalDuration));
                } else {
                    statusLabel.setText(String.format("%d images → %s video (~%s, %s, covering %s)", 
                        usedCount, duration, fileSize, speedComparison, naturalDuration));
                }
            }
        }
    }

    /**
     * Samples images based on the selected interval.
     */
    private java.util.List<TimelapseService.TimelapseImage> sampleImages(
            java.util.List<TimelapseService.TimelapseImage> images, TimelapseSettings.SamplingInterval sampling) {
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

    private void generateTimelapse(TimelapseSettings settings) {
        if (settings == null) {
            Notification.show("Invalid settings", 3000, Notification.Position.MIDDLE);
            return;
        }

        LocalDateTime from = settings.getFrom();
        LocalDateTime to = settings.getTo();
        Integer fps = settings.getFps();

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

        TimelapseSettings.VideoResolution resolution = settings.getResolution();
        TimelapseSettings.VideoQuality quality = settings.getQuality();
        TimelapseSettings.SamplingInterval sampling = settings.getSamplingInterval();
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