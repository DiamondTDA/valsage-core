package io.github.xylars.valsage.dto;

public record AuditResponse(Verdict verdict, String cited_section, String reasoning) {
    public enum Verdict { COMPLIANT, VIOLATION, OUT_OF_BOUNDS }
}
