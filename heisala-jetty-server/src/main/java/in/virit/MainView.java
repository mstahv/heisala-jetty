package in.virit;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;

@Route(layout = TopLayout.class)
@Menu(title = "Home", icon = "vaadin:home", order = 0)
public class MainView extends VerticalLayout {

    private final CameraService cameraService;
    private final Image latestPhoto;
    private final Span captureTimeLabel;

    @Inject
    public MainView(CameraService cameraService) {
        this.cameraService = cameraService;

        add(new H1("Heisala Jetty Monitor"));

        add(new Paragraph("""
            Welcome to the Heisala Jetty monitoring system. This system provides:
            """));

        add(new Paragraph("Live camera view of the jetty area with automatic photo capture every 5 minutes."));

        captureTimeLabel = new Span("Last capture: " + cameraService.getLastCaptureTimeFormatted());
        captureTimeLabel.getStyle()
            .setFontSize("var(--lumo-font-size-s)")
            .setColor("var(--lumo-secondary-text-color)");

        Button refreshButton = new Button(VaadinIcon.REFRESH.create(), e -> refreshPhoto());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.setTooltipText("Refresh photo");

        HorizontalLayout captureInfo = new HorizontalLayout(captureTimeLabel, refreshButton);
        captureInfo.setAlignItems(Alignment.CENTER);
        add(captureInfo);

        latestPhoto = new Image();
        latestPhoto.setMaxWidth("100%");
        latestPhoto.setMaxHeight("60vh");
        latestPhoto.setAlt("Latest captured photo");
        updatePhotoSource();
        add(latestPhoto);

        setSizeFull();
        setAlignItems(Alignment.CENTER);
    }

    private void refreshPhoto() {
        captureTimeLabel.setText("Last capture: " + cameraService.getLastCaptureTimeFormatted());
        updatePhotoSource();
    }

    private void updatePhotoSource() {
        if (cameraService.hasLatestPhoto()) {
            latestPhoto.setSrc(downloadEvent -> {
                try {
                    Path photoPath = cameraService.getLatestPhotoPath();
                    downloadEvent.setFileName("latest.jpg");
                    downloadEvent.setContentType("image/jpeg");
                    downloadEvent.setContentLength(Files.size(photoPath));
                    Files.copy(photoPath, downloadEvent.getOutputStream());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to stream photo", e);
                }
            });
            latestPhoto.setVisible(true);
        } else {
            latestPhoto.setAlt("No photo available yet");
            latestPhoto.setVisible(false);
        }
    }
}
