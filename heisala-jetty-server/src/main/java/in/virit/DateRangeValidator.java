package in.virit;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for the @ValidDateRange annotation.
 */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, TimelapseSettings> {

    @Override
    public void initialize(ValidDateRange constraintAnnotation) {
        // Initialization not needed
    }

    @Override
    public boolean isValid(TimelapseSettings settings, ConstraintValidatorContext context) {
        if (settings == null || settings.getFrom() == null || settings.getTo() == null) {
            return true; // Let @NotNull handle null checks
        }
        return !settings.getTo().isBefore(settings.getFrom());
    }
}