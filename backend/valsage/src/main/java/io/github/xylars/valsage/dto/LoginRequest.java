package io.github.xylars.valsage.dto;

import io.github.xylars.valsage.annotation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        boolean rememberMe,
        String existingRefreshToken
) {}
