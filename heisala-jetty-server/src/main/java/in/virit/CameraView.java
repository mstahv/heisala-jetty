package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import in.virit.libcamera4j.AfMode;
import in.virit.libcamera4j.CameraSettings;
import in.virit.libcamera4j.ImageMetadata;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route(value = "camera", layout = TopLayout.class)
@Menu(title = "Camera", icon = "vaadin:camera", order = 1)
public class CameraView extends VerticalLayout {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final CameraService cameraService;
    private final ProgressBar progressBar;
    private final Image capturedImage;
    private final ButtonBar buttonBar;
    private final MetadataGrid metadataGrid;
    private final CameraSettingsPanel settingsPanel;

    @Inject
    public CameraView(CameraService cameraService) {
        this.cameraService = cameraService;

        add(new H1("Camera Control"));

        if (!cameraService.isCameraAvailable()) {
            Notification.show("Camera hardware not available - running in UI preview mode",
                5000, Notification.Position.MIDDLE);
        }

        buttonBar = new ButtonBar();
        buttonBar.setAllEnabled(cameraService.isCameraAvailable());
        add(buttonBar);

        settingsPanel = new CameraSettingsPanel();
        add(settingsPanel);

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

    /**
     * Represents a shutter speed option with display label and microseconds value.
     */
    private record ShutterSpeed(String label, int microseconds) {
        static final List<ShutterSpeed> PRESETS = List.of(
            new ShutterSpeed("1/4000s", 250),
            new ShutterSpeed("1/2000s", 500),
            new ShutterSpeed("1/1000s", 1000),
            new ShutterSpeed("1/500s", 2000),
            new ShutterSpeed("1/250s", 4000),
            new ShutterSpeed("1/125s", 8000),
            new ShutterSpeed("1/60s", 16667),
            new ShutterSpeed("1/30s", 33333),
            new ShutterSpeed("1/15s", 66667),
            new ShutterSpeed("1/8s", 125000),
            new ShutterSpeed("1/4s", 250000),
            new ShutterSpeed("1/2s", 500000),
            new ShutterSpeed("1s", 1000000),
            new ShutterSpeed("2s", 2000000),
            new ShutterSpeed("5s", 5000000),
            new ShutterSpeed("10s", 10000000),
            new ShutterSpeed("30s", 30000000)
        );

        static ShutterSpeed findClosest(int microseconds) {
            return PRESETS.stream()
                .min((a, b) -> Integer.compare(
                    Math.abs(a.microseconds - microseconds),
                    Math.abs(b.microseconds - microseconds)))
                .orElse(PRESETS.get(6)); // 1/60s default
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Represents an ISO/gain option with display label and gain value.
     */
    private record IsoGain(String label, float gain) {
        static final List<IsoGain> PRESETS = List.of(
            new IsoGain("ISO 100", 1.0f),
            new IsoGain("ISO 200", 2.0f),
            new IsoGain("ISO 400", 4.0f),
            new IsoGain("ISO 800", 8.0f),
            new IsoGain("ISO 1600", 16.0f)
        );

        static IsoGain findClosest(float gain) {
            return PRESETS.stream()
                .min((a, b) -> Float.compare(
                    Math.abs(a.gain - gain),
                    Math.abs(b.gain - gain)))
                .orElse(PRESETS.get(0)); // ISO 100 default
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private class CameraSettingsPanel extends Details {
        private final RadioButtonGroup<Integer> rotationGroup = new RadioButtonGroup<>("Rotation");
        private final RadioButtonGroup<String> focusModeGroup = new RadioButtonGroup<>("Focus Mode");
        private final NumberField lensPositionField = new NumberField("Lens Position (dioptres)");
        private final RadioButtonGroup<String> exposureModeGroup = new RadioButtonGroup<>("Exposure Mode");
        private final ComboBox<ShutterSpeed> shutterSpeedField = new ComboBox<>("Shutter Speed");
        private final ComboBox<IsoGain> gainField = new ComboBox<>("ISO");

        private static final String HELP_TEXT = """
            ## Camera Settings Guide

            ### Rotation
            Compensates for physical camera mounting orientation.
            - **0°** - Camera mounted normally
            - **180°** - Camera mounted upside down

            ### Focus Mode
            - **Continuous AF** - Camera continuously adjusts focus (good for moving subjects)
            - **Single AF** - Camera focuses once when capturing
            - **Manual** - Fixed focus at specified lens position

            ### Lens Position (Manual Focus)
            Distance in dioptres (1/meters):
            | Position | Focus Distance |
            |----------|----------------|
            | 0.0 | Infinity (landscapes, stars) |
            | 0.5 | 2 meters |
            | 1.0 | 1 meter |
            | 2.0 | 0.5 meters |
            | 5.0 | 0.2 meters |
            | 10.0 | 0.1 meters (macro) |

            **Tip:** For shooting through a window, use manual focus at 0.0 (infinity) to prevent autofocus from focusing on the glass.

            ### Exposure Mode
            - **Auto** - Camera automatically sets shutter speed and gain
            - **Manual** - You control shutter speed and gain

            ### Shutter Speed (Manual Exposure)
            | Speed | Best For |
            |-------|----------|
            | 1/1000s - 1/4000s | Fast action, sports, bright daylight |
            | 1/250s - 1/500s | General outdoor photography |
            | 1/60s - 1/125s | General indoor, overcast |
            | 1/30s - 1/15s | Low light (use tripod) |
            | 1s - 30s | Night sky, star trails, light painting |

            ### Gain (ISO)
            Sensor amplification. Higher = brighter but noisier.
            | Gain | Approx. ISO | Use Case |
            |------|-------------|----------|
            | 1.0 | 100 | Bright daylight, best quality |
            | 2.0 | 200 | Overcast, shade |
            | 4.0 | 400 | Indoor, cloudy |
            | 8.0 | 800 | Low light, evening |
            | 16.0 | 1600 | Very low light (noisy) |

            **Tip for sunset/night sky:** Use manual exposure with long shutter speed (1s-30s) and low gain (1.0-2.0) on a tripod.
            """;

        CameraSettingsPanel() {
            setSummary(new HorizontalLayout() {{
                setAlignItems(Alignment.CENTER);
                add(
                    new com.vaadin.flow.component.html.Span("Camera Settings"),
                    new HelpButton(HELP_TEXT)
                );
            }});

            FormLayout content = new FormLayout();
            content.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

            // Rotation
            rotationGroup.setItems(0, 90, 180, 270);
            rotationGroup.setItemLabelGenerator(deg -> deg + "°");

            // Focus
            focusModeGroup.setItems("Continuous AF", "Single AF", "Manual");
            focusModeGroup.addValueChangeListener(e -> {
                lensPositionField.setVisible("Manual".equals(e.getValue()));
            });

            lensPositionField.setMin(0);
            lensPositionField.setMax(15);
            lensPositionField.setStep(0.5);
            lensPositionField.setHelperText("0 = infinity, higher = closer");
            lensPositionField.setVisible(false);

            // Exposure
            exposureModeGroup.setItems("Auto", "Manual");
            exposureModeGroup.addValueChangeListener(e -> {
                boolean manual = "Manual".equals(e.getValue());
                shutterSpeedField.setVisible(manual);
                gainField.setVisible(manual);
            });

            shutterSpeedField.setItems(ShutterSpeed.PRESETS);
            shutterSpeedField.setItemLabelGenerator(ShutterSpeed::label);
            shutterSpeedField.setVisible(false);

            gainField.setItems(IsoGain.PRESETS);
            gainField.setItemLabelGenerator(IsoGain::label);
            gainField.setVisible(false);

            loadDefaultSettings();

            content.add(rotationGroup, focusModeGroup, lensPositionField,
                       exposureModeGroup, shutterSpeedField, gainField);
            this.add(content);
        }

        private void loadDefaultSettings() {
            CameraSettings settings = cameraService.getCurrentSettings();

            int rotation = switch (settings.transform()) {
                case ROT90 -> 90;
                case ROT180 -> 180;
                case ROT270 -> 270;
                default -> 0;
            };
            rotationGroup.setValue(rotation);

            String focusMode = switch (settings.afMode()) {
                case MANUAL -> "Manual";
                case AUTO -> "Single AF";
                case CONTINUOUS -> "Continuous AF";
            };
            focusModeGroup.setValue(focusMode);
            lensPositionField.setVisible("Manual".equals(focusMode));
            lensPositionField.setValue((double) settings.lensPosition());

            exposureModeGroup.setValue(settings.autoExposure() ? "Auto" : "Manual");
            shutterSpeedField.setVisible(!settings.autoExposure());
            gainField.setVisible(!settings.autoExposure());
            shutterSpeedField.setValue(ShutterSpeed.findClosest(settings.exposureTimeMicros()));
            gainField.setValue(IsoGain.findClosest(settings.analogueGain()));
        }

        CameraSettings getSettings() {
            Integer rotation = rotationGroup.getValue();
            String focusMode = focusModeGroup.getValue();
            Double lensPosition = lensPositionField.getValue();
            String exposureMode = exposureModeGroup.getValue();
            ShutterSpeed shutterSpeed = shutterSpeedField.getValue();
            IsoGain isoGain = gainField.getValue();

            CameraSettings.Builder builder = CameraSettings.builder();

            if (rotation != null) {
                builder.rotation(rotation);
            }

            if ("Manual".equals(focusMode)) {
                float pos = (lensPosition != null) ? lensPosition.floatValue() : 0f;
                builder.manualFocus(pos);
            } else if ("Single AF".equals(focusMode)) {
                builder.autofocus(false);
            } else {
                builder.autofocus(true);
            }

            if ("Manual".equals(exposureMode)) {
                int time = (shutterSpeed != null) ? shutterSpeed.microseconds() : 16667;
                float g = (isoGain != null) ? isoGain.gain() : 1.0f;
                builder.manualExposure(time, g);
            } else {
                builder.autoExposure(true);
            }

            return builder.build();
        }

    }

    private static class HelpButton extends Button {
        HelpButton(String markdownContent) {
            super(VaadinIcon.QUESTION_CIRCLE.create());
            addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            addClickListener(e -> new Dialog() {{
                setHeaderTitle("Help");
                setWidth("600px");
                setMaxHeight("80vh");
                add(new Markdown(markdownContent));
                getFooter().add(new Button("Close", ce -> close()) {{
                    addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                }});
            }}.open());
        }
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
                    CameraSettings settings = settingsPanel.getSettings();
                    byte[] dngBytes = cameraService.captureDngAsync(settings).get();
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
        CameraSettings settings = settingsPanel.getSettings();
        cameraService.setCurrentSettings(settings);

        cameraService.captureAndSaveAsync(settings)
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
        CameraSettings settings = settingsPanel.getSettings();
        cameraService.setCurrentSettings(settings);

        cameraService.captureFullResolutionWithMetadataAsync(settings)
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
        settingsPanel.setOpened(false);
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
