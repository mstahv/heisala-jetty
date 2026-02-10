package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A widget that displays system CPU and memory usage, updated every 2 seconds.
 * Also tracks file generation progress with ETA.
 */
public class SystemMonitor extends VerticalLayout {

    private final ProgressBar cpuBar = new ProgressBar(0, 1);
    private final ProgressBar memoryBar = new ProgressBar(0, 1);
    private final ProgressBar progressBar = new ProgressBar(0, 1);
    private final Span cpuLabel = new Span();
    private final Span memoryLabel = new Span();
    private final Span progressLabel = new Span();
    private final Div progressSection;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> updateTask;
    private Path monitoredFile;
    private long estimatedFileSizeBytes;
    private long startTimeMs;
    private long lastFileSize;
    private long lastFileSizeTime;

    public SystemMonitor() {
        setSpacing(false);
        setPadding(true);
        setWidth("200px");
        getStyle()
            .setBackground("var(--lumo-contrast-5pct)")
            .setBorderRadius("var(--lumo-border-radius-m)");

        add(createSection("CPU", cpuBar, cpuLabel));
        add(createSection("Memory", memoryBar, memoryLabel));

        // Progress section (hidden initially)
        progressSection = createSection("Progress", progressBar, progressLabel);
        progressSection.setVisible(false);
        progressBar.setIndeterminate(true);
        add(progressSection);

        // Style the bars
        cpuBar.setWidth("100%");
        memoryBar.setWidth("100%");
        progressBar.setWidth("100%");
    }

    private Div createSection(String title, ProgressBar bar, Span label) {
        Div section = new Div();
        section.getStyle().setMarginBottom("var(--lumo-space-s)");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
            .setFontSize("var(--lumo-font-size-xs)")
            .setFontWeight("bold")
            .setColor("var(--lumo-secondary-text-color)");

        label.getStyle()
            .setFontSize("var(--lumo-font-size-xs)")
            .setColor("var(--lumo-secondary-text-color)")
            .setMarginLeft("auto");

        Div header = new Div(titleSpan, label);
        header.getStyle()
            .setDisplay(com.vaadin.flow.dom.Style.Display.FLEX)
            .setJustifyContent(com.vaadin.flow.dom.Style.JustifyContent.SPACE_BETWEEN)
            .setMarginBottom("var(--lumo-space-xs)");

        section.add(header, bar);
        return section;
    }

    /**
     * Sets a file to monitor for size changes with an estimated final size.
     *
     * @param file the file to monitor, or null to stop monitoring
     * @param estimatedSizeBytes estimated final file size in bytes
     */
    public void setMonitoredFile(Path file, long estimatedSizeBytes) {
        this.monitoredFile = file;
        this.estimatedFileSizeBytes = estimatedSizeBytes;
        this.startTimeMs = System.currentTimeMillis();
        this.lastFileSize = 0;
        this.lastFileSizeTime = startTimeMs;

        if (file != null) {
            progressSection.setVisible(true);
            progressBar.setIndeterminate(true);
            progressLabel.setText("Starting...");
        } else {
            progressSection.setVisible(false);
        }
    }

    /**
     * Sets a file to monitor (without size estimation).
     */
    public void setMonitoredFile(Path file) {
        setMonitoredFile(file, 0);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        var ui = attachEvent.getUI();

        updateTask = executor.scheduleAtFixedRate(() -> {
            try {
                double cpuLoad = getCpuLoad();
                double memoryUsage = getMemoryUsage();
                long usedMemoryMB = getUsedMemoryMB();
                long totalMemoryMB = getTotalMemoryMB();

                // File progress tracking
                long currentFileSize = 0;
                double progress = 0;
                String progressText = "Starting...";

                if (monitoredFile != null) {
                    try {
                        if (Files.exists(monitoredFile)) {
                            currentFileSize = Files.size(monitoredFile);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }

                    if (currentFileSize > 0 && estimatedFileSizeBytes > 0) {
                        progress = Math.min(1.0, (double) currentFileSize / estimatedFileSizeBytes);

                        // Calculate ETA based on speed
                        long now = System.currentTimeMillis();
                        long elapsedMs = now - startTimeMs;

                        if (elapsedMs > 1000 && currentFileSize > 0) {
                            double bytesPerMs = (double) currentFileSize / elapsedMs;
                            long remainingBytes = estimatedFileSizeBytes - currentFileSize;
                            long etaMs = (long) (remainingBytes / bytesPerMs);

                            String eta = formatDuration(etaMs);
                            progressText = String.format("%d%% - %s / %s - ETA: %s",
                                (int) (progress * 100),
                                formatBytes(currentFileSize),
                                formatBytes(estimatedFileSizeBytes),
                                eta);
                        } else {
                            progressText = String.format("%d%% - %s / %s",
                                (int) (progress * 100),
                                formatBytes(currentFileSize),
                                formatBytes(estimatedFileSizeBytes));
                        }

                        lastFileSize = currentFileSize;
                        lastFileSizeTime = now;
                    } else if (currentFileSize > 0) {
                        // No estimate, just show current size
                        progressText = formatBytes(currentFileSize);
                    }
                }

                final double finalProgress = progress;
                final String finalProgressText = progressText;
                final long finalFileSize = currentFileSize;

                ui.access(() -> {
                    cpuBar.setValue(cpuLoad);
                    cpuLabel.setText(String.format("%.0f%%", cpuLoad * 100));

                    memoryBar.setValue(memoryUsage);
                    memoryLabel.setText(String.format("%d / %d MB", usedMemoryMB, totalMemoryMB));

                    if (monitoredFile != null) {
                        progressLabel.setText(finalProgressText);

                        // Switch from indeterminate to determinate once we have data
                        if (finalFileSize > 0 && estimatedFileSizeBytes > 0) {
                            if (progressBar.isIndeterminate()) {
                                progressBar.setIndeterminate(false);
                            }
                            progressBar.setValue(finalProgress);
                        }
                    }
                });
            } catch (Exception e) {
                // Ignore errors during polling
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (updateTask != null) {
            updateTask.cancel(true);
        }
    }

    private double getCpuLoad() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            double load = sunOs.getCpuLoad();
            return load >= 0 ? load : 0;
        }
        return os.getSystemLoadAverage() / os.getAvailableProcessors();
    }

    private double getMemoryUsage() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            long total = sunOs.getTotalMemorySize();
            long free = sunOs.getFreeMemorySize();
            return (double) (total - free) / total;
        }
        Runtime runtime = Runtime.getRuntime();
        return (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
    }

    private long getUsedMemoryMB() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            long total = sunOs.getTotalMemorySize();
            long free = sunOs.getFreeMemorySize();
            return (total - free) / (1024 * 1024);
        }
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private long getTotalMemoryMB() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            return sunOs.getTotalMemorySize() / (1024 * 1024);
        }
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return "<1s";
        } else if (ms < 60000) {
            return (ms / 1000) + "s";
        } else if (ms < 3600000) {
            long min = ms / 60000;
            long sec = (ms % 60000) / 1000;
            return String.format("%dm %ds", min, sec);
        } else {
            long hours = ms / 3600000;
            long min = (ms % 3600000) / 60000;
            return String.format("%dh %dm", hours, min);
        }
    }
}
