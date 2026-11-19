package io.github.xylars.valsage.dto;


import io.github.xylars.valsage.annotation.PasswordMatches;
import io.github.xylars.valsage.annotation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatches
public record RegisterRequest(
        @NotBlank(message = "Username cannot be empty")
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username cannot contain special characters")
        String username,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters long")
        String password,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, max = 72, message = "Matching password must be at least 8 characters long")
        String matchingPassword,

        @ValidEmail
        @Size(max = 50, message = "Email is too long")
        String email
)
{}
