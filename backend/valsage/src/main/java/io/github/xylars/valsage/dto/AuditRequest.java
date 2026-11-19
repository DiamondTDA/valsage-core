package io.github.xylars.valsage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuditRequest(@NotBlank @Size(max = 2000) String inquiry) {}
