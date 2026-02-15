package in.virit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.select.Select;
import org.vaadin.firitin.components.datetimepicker.VDateTimePicker;
import org.vaadin.firitin.components.select.VSelect;
import org.vaadin.firitin.form.BeanValidationForm;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Custom form for timelapse settings that extends BeanValidationForm.
 */
public class TimelapseSettingsForm extends BeanValidationForm<TimelapseSettings> {

    private final DateTimePicker from = new DateTimePicker("From");
    private final DateTimePicker to = new DateTimePicker("To");
    private final Select<Integer> fps = new Select<>();
    private final Select<TimelapseSettings.SamplingInterval> samplingInterval = new Select<>();
    private final Select<TimelapseSettings.VideoResolution> resolution = new Select<>();
    private final Select<TimelapseSettings.VideoQuality> quality = new Select<>();

    public TimelapseSettingsForm() {
        super(TimelapseSettings.class);
        
        // Configure FPS selector
        fps.setLabel("Frames per second");
        fps.setItems(5, 10, 15, 24, 30);
        fps.setItemLabelGenerator(fpsValue -> fpsValue + " fps");
        
        // Configure sampling selector
        samplingInterval.setLabel("Use images");
        samplingInterval.setItems(TimelapseSettings.SAMPLING_INTERVALS);
        
        // Configure resolution selector
        resolution.setLabel("Resolution");
        resolution.setItems(TimelapseSettings.RESOLUTIONS);
        
        // Configure quality selector
        quality.setLabel("Quality");
        quality.setItems(TimelapseSettings.QUALITIES);
        
        // BeanValidationForm automatically binds fields to DTO properties by name
        // Field names match DTO property names for automatic binding
    }

    @Override
    protected List<Component> getFormComponents() {
        return Arrays.asList(from, to, fps, samplingInterval, resolution, quality);
    }

    public void setDateRange(LocalDateTime min, LocalDateTime max, LocalDateTime defaultFrom, LocalDateTime defaultTo) {
        from.setLocale(Locale.UK);
        from.setMin(min);
        from.setMax(max);
        from.setValue(defaultFrom);
        
        to.setLocale(Locale.UK);
        to.setMin(min);
        to.setMax(max);
        to.setValue(defaultTo);
    }

    public TimelapseSettings getSettings() {
        // With proper binding, we can just return the entity
        return getEntity();
    }
}