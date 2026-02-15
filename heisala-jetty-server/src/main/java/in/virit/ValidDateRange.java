package in.virit;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation to ensure that the 'to' date is not before the 'from' date.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
@Documented
public @interface ValidDateRange {
    String message() default "End time must be after start time";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}