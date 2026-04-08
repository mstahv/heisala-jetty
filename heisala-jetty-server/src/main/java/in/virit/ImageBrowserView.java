package in.virit;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.ElementFactory;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.Registration;
import org.vaadin.firitin.util.fullscreen.FullScreen;
import jakarta.inject.Inject;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Route(layout = TopLayout.class)
@Menu(title = "Image Browser", icon = "vaadin:picture", order = 2)
public class ImageBrowserView extends VerticalLayout {

    private final TimelapseService timelapseService;
    private final Grid<TimelapseService.TimelapseImage> imageGrid;
    private final Image previewImage;
    private final Span previewInfo;
    private final ProgressBar loadingIndicator;
    
    private List<TimelapseService.TimelapseImage> allImages = new ArrayList<>();
    private ListDataProvider<TimelapseService.TimelapseImage> dataProvider;
    
    private DateTimePicker dateTimeFilter;

    @Inject
    public ImageBrowserView(TimelapseService timelapseService) {
        this.timelapseService = timelapseService;
        setSpacing(false);
        setPadding(false);
        setWidth("100%");

        // Create preview area (will be on the right side)
        previewImage = new Image();
        previewImage.setWidth("100%");
        previewImage.setMaxHeight("800px");
        previewImage.getStyle()
            .setBorder("1px solid var(--lumo-contrast-20pct)")
            .setBorderRadius("var(--lumo-border-radius-m)")
            .setBackground("var(--lumo-contrast-5pct)");

        previewInfo = new Span();
        previewInfo.getStyle()
            .setFontSize("var(--lumo-font-size-s)")
            .setColor("var(--lumo-secondary-text-color)")
            .setMarginTop("var(--lumo-space-s)");
            
        // Fullscreen button
        Button fullScreenButton = new Button(VaadinIcon.EXPAND.create());
        fullScreenButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        fullScreenButton.setTooltipText("View full screen");
        fullScreenButton.addClickListener(e -> toggleFullScreen(previewImage));
        fullScreenButton.getStyle().setMarginLeft("auto");
        
        HorizontalLayout previewHeader = new HorizontalLayout(previewInfo, fullScreenButton);
        previewHeader.setWidth("100%");
        previewHeader.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Div previewArea = new Div(previewImage, previewHeader);
        previewArea.getStyle()
            .setBackground("var(--lumo-contrast-5pct)")
            .setPadding("var(--lumo-space-m)")
            .setBorderRadius("var(--lumo-border-radius-m)")
            .set("overflow", "auto");

        // Create filters
        createFilters();
        
        // Create grid
        imageGrid = new Grid<>();
        imageGrid.setHeight("600px");
        imageGrid.setWidth("100%");
        
        imageGrid.addColumn(new ComponentRenderer<>(image -> {
            Image thumb = new Image();
            String relativePath = timelapseService.TIMELAPSE_DIR.relativize(image.path()).toString();
            thumb.setSrc("/api/images/" + relativePath.replace("\\", "/"));
            thumb.setWidth("120px");
            thumb.setHeight("80px");
            thumb.setAlt("Thumbnail: " + image.getDisplayTime());
            thumb.getStyle().setCursor("pointer");
            return thumb;
        })).setHeader("Thumbnail").setWidth("140px").setFlexGrow(0);

        imageGrid.addColumn(TimelapseService.TimelapseImage::getDisplayTime)
            .setHeader("Timestamp (Newest First)")
            .setSortable(true)
            .setWidth("250px");

        imageGrid.addColumn(image -> {
            try {
                return Files.size(image.path()) / 1024 + " KB";
            } catch (IOException e) {
                return "N/A";
            }
        }).setHeader("Size").setWidth("100px");

        imageGrid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                showImagePreview(event.getValue());
            }
        });

        // Loading indicator
        loadingIndicator = new ProgressBar();
        loadingIndicator.setIndeterminate(true);
        loadingIndicator.setVisible(false);
        loadingIndicator.setWidth("100%");

        // Main content layout - grid on left, preview on right
        HorizontalLayout mainContent = new HorizontalLayout();
        mainContent.setWidth("100%");
        mainContent.setSpacing(true);
        mainContent.getStyle().setMarginTop("var(--lumo-space-m)");
        
        // Grid container (left side - 50% width)
        VerticalLayout gridContainer = new VerticalLayout();
        gridContainer.setWidth("50%");
        gridContainer.setSpacing(false);
        gridContainer.add(createFilterLayout());
        gridContainer.add(loadingIndicator);
        gridContainer.add(imageGrid);
        gridContainer.setFlexGrow(1, imageGrid);
        
        // Preview container (right side - 50% width)
        VerticalLayout previewContainer = new VerticalLayout();
        previewContainer.setWidth("50%");
        previewContainer.setSpacing(false);
        previewContainer.add(previewArea);
        previewContainer.setFlexGrow(1, previewArea);
        
        mainContent.add(gridContainer, previewContainer);
        mainContent.setFlexGrow(1, gridContainer);

        add(new H2("Timelapse Image Browser"));
        add(new com.vaadin.flow.component.html.Paragraph("""
            Browse all captured timelapse photos. Use the datetime picker to jump to the closest photo 
            of any specific date and time. Click any thumbnail to view it in the preview panel. 
            Photos are displayed in reverse chronological order (newest first).
        """));
        add(mainContent);
    }

    private void createFilters() {
        // DateTime filter - scroll to closest photo of selected datetime
        dateTimeFilter = new DateTimePicker("Jump to Date/Time");
        dateTimeFilter.setStep(Duration.ofMinutes(1)); // 1-minute increments
        dateTimeFilter.addValueChangeListener(e -> jumpToClosestPhoto());
        
        // Jump button
        Button jumpButton = new Button("Jump to Closest Photo", VaadinIcon.ARROW_RIGHT.create());
        jumpButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        jumpButton.addClickListener(e -> jumpToClosestPhoto());
        
        // Refresh button
        Button refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> loadImages());
    }

    private HorizontalLayout createFilterLayout() {
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setWidth("100%");
        filterLayout.setSpacing(true);
        filterLayout.setAlignItems(Alignment.BASELINE);
        filterLayout.getStyle().setMarginBottom("var(--lumo-space-m)");

        filterLayout.add(dateTimeFilter);
        
        Button jumpButton = new Button("Jump to Closest Photo", VaadinIcon.ARROW_RIGHT.create());
        jumpButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        jumpButton.addClickListener(e -> jumpToClosestPhoto());
        
        filterLayout.add(jumpButton);
        filterLayout.setFlexGrow(1, dateTimeFilter);
        
        return filterLayout;
    }



    private void showImagePreview(TimelapseService.TimelapseImage image) {
        try {
            // Use the same approach as thumbnails for consistency
            String relativePath = timelapseService.TIMELAPSE_DIR.relativize(image.path()).toString();
            previewImage.setSrc("/api/images/" + relativePath.replace("\\", "/"));
            previewInfo.setText("Taken: " + image.getDisplayTime() + " | Size: " + 
                (Files.size(image.path()) / 1024) + " KB | Path: " + image.path());
        } catch (IOException e) {
            Notification.show("Error loading image: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    /**
     * Finds the closest photo to the selected datetime and scrolls to it
     */
    private void jumpToClosestPhoto() {
        if (dateTimeFilter.getValue() == null || allImages.isEmpty()) {
            return;
        }

        LocalDateTime targetDateTime = dateTimeFilter.getValue();
        
        // Find the closest image to the target datetime
        TimelapseService.TimelapseImage closestImage = allImages.stream()
            .min(Comparator.comparingLong(img -> Math.abs(java.time.Duration.between(img.timestamp(), targetDateTime).getSeconds())))
            .orElse(null);

        if (closestImage != null) {
            getUI().ifPresent(ui -> 
                ui.access(() -> {
                    // Select the closest image in the grid
                    imageGrid.asSingleSelect().setValue(closestImage);
                    
                    // Scroll the grid to make the selected image visible
                    int index = allImages.indexOf(closestImage);
                    if (index >= 0) {
                        imageGrid.scrollToIndex(index);
                    }
                    
                    // Show the image in preview
                    showImagePreview(closestImage);
                    
                    // Show notification
                    long secondsDiff = Duration.between(closestImage.timestamp(), targetDateTime).getSeconds();
                    String timeDiff = formatTimeDifference(secondsDiff);
                    Notification.show("Jumped to photo taken " + timeDiff + " " + 
                                    (secondsDiff > 0 ? "before" : "after") + " selected time", 
                                    3000, Notification.Position.MIDDLE);
                })
            );
        } else {
            Notification.show("No photos found near the selected time", 
                            3000, Notification.Position.MIDDLE);
        }
    }

    /**
     * Formats time difference in human-readable format
     */
    private String formatTimeDifference(long seconds) {
        seconds = Math.abs(seconds);
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minutes" + (seconds % 60 > 0 ? " " + (seconds % 60) + " seconds" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long remainingSeconds = seconds % 3600;
            long minutes = remainingSeconds / 60;
            return hours + " hours" + (minutes > 0 ? " " + minutes + " minutes" : "");
        } else {
            long days = seconds / 86400;
            return days + " days";
        }
    }

    private void updateStatus(int imageCount) {
        String status = imageCount + " images found";
        getUI().ifPresent(ui -> 
            ui.access(() -> {
                if (imageGrid != null) {
                    imageGrid.setHeight("400px"); // Reset height
                }
            })
        );
    }

    private void loadImages() {
        loadingIndicator.setVisible(true);
        
        new Thread(() -> {
            try {
                allImages = timelapseService.listImages();
                
                getUI().ifPresent(ui -> 
                    ui.access(() -> {
                        // Sort images by timestamp (newest first)
                        List<TimelapseService.TimelapseImage> sortedImages = allImages.stream()
                            .sorted(Comparator.comparing(TimelapseService.TimelapseImage::timestamp).reversed())
                            .collect(Collectors.toList());
                        
                        if (dataProvider == null) {
                            dataProvider = new ListDataProvider<>(sortedImages);
                            imageGrid.setDataProvider(dataProvider);
                        } else {
                            dataProvider.getItems().clear();
                            dataProvider.getItems().addAll(sortedImages);
                            dataProvider.refreshAll();
                        }
                        
                        loadingIndicator.setVisible(false);
                        updateStatus(sortedImages.size());
                        
                        if (!sortedImages.isEmpty()) {
                            // Select the most recent image by default (first in sorted list)
                            imageGrid.asSingleSelect().setValue(sortedImages.get(0));
                        }
                    })
                );
            } catch (Exception e) {
                getUI().ifPresent(ui -> 
                    ui.access(() -> {
                        loadingIndicator.setVisible(false);
                        Notification.show("Error loading images: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
                    })
                );
            }
        }).start();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        loadImages();
    }

    /**
     * Toggles full screen mode for the preview image using Viritin FullScreen helper
     */
    private void toggleFullScreen(Image image) {
        FullScreen.requestFullscreen(image);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        // Cleanup if needed
    }
}