package io.github.xylars.valsage.dto;

import java.util.Set;

public record EngineAuditRequest(
        String inquiry,
        Set<String> authorities
) {}
