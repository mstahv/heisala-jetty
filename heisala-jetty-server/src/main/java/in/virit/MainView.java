package in.virit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Route
public class MainView extends VerticalLayout {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CameraService cameraService;
    private final Image capturedImage;

    @Inject
    public MainView(CameraService cameraService) {
        this.cameraService = cameraService;

        add(new H1("Jetty @ Heisala"));

        add(new Button("Take Photo", e -> capturePhoto()) {{
            getStyle().setFontSize("1.5em");
            getStyle().setPadding("1em 2em");
        }});

        capturedImage = new Image();
        capturedImage.setMaxWidth("100%");
        capturedImage.setMaxHeight("80vh");
        capturedImage.setVisible(false);
        add(capturedImage);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
    }

    private void capturePhoto() {
        try {
            byte[] jpeg = cameraService.captureJpeg(1920, 1080);

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
        } catch (Exception ex) {
            Notification.show("Capture failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }
}
