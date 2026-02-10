package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.Route;
import in.virit.libcamera4j.CaptureResult;
import in.virit.libcamera4j.ImageMetadata;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route
public class MainView extends VerticalLayout {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final CameraService cameraService;
    private final ProgressBar progressBar;
    private final Image capturedImage;
    private final ButtonBar buttonBar;
    private final MetadataGrid metadataGrid;

    @Inject
    public MainView(CameraService cameraService) {
        this.cameraService = cameraService;

        add(new H1("Jetty @ Heisala"));

        buttonBar = new ButtonBar();
        add(buttonBar);

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidth("300px");
        add(progressBar);

        HorizontalLayout contentLayout = new HorizontalLayout();
        contentLayout.setWidthFull();
        contentLayout.setAlignItems(Alignment.START);

        capturedImage = new Image();
        capturedImage.setMaxWidth("60%");
        capturedImage.setMaxHeight("70vh");
        capturedImage.setVisible(false);

        metadataGrid = new MetadataGrid();
        metadataGrid.setVisible(false);

        contentLayout.add(capturedImage, metadataGrid);
        add(contentLayout);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
    }

    private class ButtonBar extends HorizontalLayout {
        private final Button quickPhotoButton = new Button("Quick Photo", e -> captureQuickPhoto());
        private final Button fullResButton = new Button("Full Resolution JPEG", e -> captureFullResolution());
        private final Anchor dngAnchor = new Anchor(){{
            setDownload(true);
            setText("Download DNG");
            getElement().addEventListener("click", domEvent -> {
                Notification.show("DNG generation started, this might take a while...");
                startCapture();
            });
            setHref(downloadEvent -> {
                UI ui = downloadEvent.getUI();
                try {
                    byte[] dngBytes = cameraService.captureDngAsync().get();
                    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                    String fileTimestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
                    String fileName = "capture_" + fileTimestamp + ".dng";
                    downloadEvent.setFileName(fileName);
                    downloadEvent.setContentType("image/x-adobe-dng");
                    downloadEvent.setContentLength(dngBytes.length);
                    downloadEvent.getOutputStream().write(dngBytes);
                    ui.access(() -> {
                        metadataGrid.setVisible(false);
                        endCapture("DNG captured at " + timestamp);
                    });
                } catch (Exception e) {
                    handleCaptureError(ui, e);
                }
            });
        }};

        ButtonBar() {
            quickPhotoButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(quickPhotoButton, fullResButton, dngAnchor);
            setSpacing(true);
            setAlignItems(Alignment.CENTER);
        }

        void setAllEnabled(boolean enabled) {
            quickPhotoButton.setEnabled(enabled);
            fullResButton.setEnabled(enabled);
        }
    }

    /**
     * Simple key-value pair for displaying metadata.
     */
    public record MetadataEntry(String property, String value) {}

    private class MetadataGrid extends VerticalLayout {
        private final Grid<MetadataEntry> grid = new Grid<>(MetadataEntry.class, false);

        MetadataGrid() {
            add(new H3("Image Metadata"));
            grid.addColumn(MetadataEntry::property).setHeader("Property").setFlexGrow(1);
            grid.addColumn(MetadataEntry::value).setHeader("Value").setFlexGrow(2);
            grid.setAllRowsVisible(true);
            grid.setWidth("350px");
            add(grid);
            setPadding(false);
            setSpacing(false);
        }

        void setMetadata(ImageMetadata metadata) {
            List<MetadataEntry> entries = new ArrayList<>();
            entries.add(new MetadataEntry("Resolution", metadata.width() + " × " + metadata.height()));
            entries.add(new MetadataEntry("Pixel Format", metadata.pixelFormat()));
            entries.add(new MetadataEntry("Exposure", metadata.exposureTimeFormatted()));
            entries.add(new MetadataEntry("ISO (approx)", String.valueOf(metadata.approximateIso())));
            entries.add(new MetadataEntry("Analogue Gain", String.format("%.2f", metadata.analogueGain())));
            entries.add(new MetadataEntry("Digital Gain", String.format("%.2f", metadata.digitalGain())));
            entries.add(new MetadataEntry("Total Gain", String.format("%.2f", metadata.totalGain())));
            entries.add(new MetadataEntry("Red Gain (AWB)", String.format("%.2f", metadata.redGain())));
            entries.add(new MetadataEntry("Blue Gain (AWB)", String.format("%.2f", metadata.blueGain())));
            if (metadata.colourTemperature() > 0) {
                entries.add(new MetadataEntry("Color Temp", metadata.colourTemperature() + " K"));
            }
            if (metadata.lux() > 0) {
                entries.add(new MetadataEntry("Lux", String.format("%.1f", metadata.lux())));
            }
            entries.add(new MetadataEntry("Sequence", String.valueOf(metadata.sequence())));
            grid.setItems(entries);
        }
    }

    private void captureQuickPhoto() {
        startCapture();
        UI ui = UI.getCurrent();

        cameraService.captureWithMetadataAsync()
            .thenAccept(result -> ui.access(() -> {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String fileTimestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
                showCapturedImage(result.jpeg(), "capture_" + fileTimestamp + ".jpg", timestamp);
                showMetadata(result.metadata());
                endCapture("Photo captured at " + timestamp);
            }))
            .exceptionally(ex -> handleCaptureError(ui, ex));
    }

    private void captureFullResolution() {
        startCapture();
        UI ui = UI.getCurrent();

        cameraService.captureFullResolutionWithMetadataAsync()
            .thenAccept(result -> ui.access(() -> {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String fileTimestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
                showCapturedImage(result.jpeg(), "fullres_" + fileTimestamp + ".jpg", timestamp);
                showMetadata(result.metadata());
                endCapture("Full resolution photo captured at " + timestamp);
            }))
            .exceptionally(ex -> handleCaptureError(ui, ex));
    }

    private void startCapture() {
        buttonBar.setAllEnabled(false);
        progressBar.setVisible(true);
    }

    private void endCapture(String message) {
        buttonBar.setAllEnabled(true);
        progressBar.setVisible(false);
        Notification.show(message, 3000, Notification.Position.BOTTOM_CENTER);
    }

    private Void handleCaptureError(UI ui, Throwable ex) {
        ui.access(() -> {
            Notification.show("Capture failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            buttonBar.setAllEnabled(true);
            progressBar.setVisible(false);
        });
        return null;
    }

    private void showCapturedImage(byte[] jpeg, String fileName, String timestamp) {
        capturedImage.setSrc(downloadEvent -> {
            downloadEvent.setFileName(fileName);
            downloadEvent.setContentType("image/jpeg");
            downloadEvent.setContentLength(jpeg.length);
            downloadEvent.getOutputStream().write(jpeg);
        });
        capturedImage.setAlt("Captured at " + timestamp);
        capturedImage.setVisible(true);
    }

    private void showMetadata(ImageMetadata metadata) {
        metadataGrid.setMetadata(metadata);
        metadataGrid.setVisible(true);
    }

}
