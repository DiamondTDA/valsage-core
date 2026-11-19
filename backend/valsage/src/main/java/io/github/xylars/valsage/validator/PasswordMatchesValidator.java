package io.github.xylars.valsage.validator;

import io.github.xylars.valsage.annotation.PasswordMatches;
import io.github.xylars.valsage.dto.MyUserDto;
import io.github.xylars.valsage.dto.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, Object> {
    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        RegisterRequest user = (RegisterRequest) obj;
        // 1. Prevent NullPointerException if fields are missing in the request payload
        if (user == null || user.password() == null || user.matchingPassword() == null) {
            return false;
        }
        boolean isValid = user.password().equals(user.matchingPassword());

        // 2. Efficiently bind the error directly to the matchingPassword field
        if (!isValid) {
            context.disableDefaultConstraintViolation(); // Turn off global object error
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("matchingPassword")    // Target the specific field
                    .addConstraintViolation();
        }

        return isValid;
    }
}
