package in.virit;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Route
public class MainView extends VerticalLayout {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CameraService cameraService;
    private final Button captureButton;
    private final ProgressBar progressBar;
    private final Image capturedImage;

    @Inject
    public MainView(CameraService cameraService) {
        this.cameraService = cameraService;

        add(new H1("Jetty @ Heisala"));

        captureButton = new Button("Take Photo", e -> capturePhoto());
        captureButton.getStyle()
            .setFontSize("1.5em")
            .setPadding("1em 2em");
        add(captureButton);

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidth("300px");
        add(progressBar);

        capturedImage = new Image();
        capturedImage.setMaxWidth("100%");
        capturedImage.setMaxHeight("80vh");
        capturedImage.setVisible(false);
        add(capturedImage);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
    }

    private void capturePhoto() {
        // Disable button and show progress
        captureButton.setEnabled(false);
        progressBar.setVisible(true);

        UI ui = UI.getCurrent();

        cameraService.captureJpegAsync()
            .thenAccept(jpeg -> ui.access(() -> {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                capturedImage.setSrc(downloadEvent -> {
                    downloadEvent.setFileName("capture_" + timestamp + ".jpg");
                    downloadEvent.setContentType("image/jpeg");
                    downloadEvent.setContentLength(jpeg.length);
                    downloadEvent.getOutputStream().write(jpeg);
                });
                capturedImage.setAlt("Captured at " + timestamp);
                capturedImage.setVisible(true);

                Notification.show("Photo captured at " + timestamp, 3000, Notification.Position.BOTTOM_CENTER);

                // Re-enable button and hide progress
                captureButton.setEnabled(true);
                progressBar.setVisible(false);
            }))
            .exceptionally(ex -> {
                ui.access(() -> {
                    Notification.show("Capture failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
                    captureButton.setEnabled(true);
                    progressBar.setVisible(false);
                });
                return null;
            });
    }
}
