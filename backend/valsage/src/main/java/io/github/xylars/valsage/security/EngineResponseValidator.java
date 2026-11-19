package io.github.xylars.valsage.security;

import java.util.regex.Pattern;

import io.github.xylars.valsage.dto.AuditResponse;
import io.github.xylars.valsage.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EngineResponseValidator {
    private final PiiRedactionService pii;
    private static final Pattern UNSAFE = Pattern.compile("(?is)<\\s*/?\\s*(?:script|iframe|object|embed)\\b|javascript\\s*:");
    public EngineResponseValidator(PiiRedactionService pii) { this.pii = pii; }
    public AuditResponse validate(AuditResponse response) {
        if (response == null || response.verdict() == null || !valid(response.cited_section(), 250)
                || !valid(response.reasoning(), 3000)) fail();
        if (UNSAFE.matcher(response.cited_section()).find() || UNSAFE.matcher(response.reasoning()).find()) fail();
        if (pii.containsSensitiveData(response.cited_section()) || pii.containsSensitiveData(response.reasoning())) fail();
        if (response.verdict() == AuditResponse.Verdict.OUT_OF_BOUNDS
                && !response.cited_section().isBlank()) {
            // A citation for an out-of-bounds decision is allowed if engine policy supports it.
            // Validate actual citation provenance against authorized documents in the RAG tier.
        }
        return response;
    }
    private static boolean valid(String s, int limit) {
        if (s == null || s.length() > limit) return false;
        for (int i=0;i<s.length();i++) {
            char c = s.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r') return false;
        }
        return true;
    }
    private static void fail() {
        throw new GatewayException(HttpStatus.BAD_GATEWAY, "INVALID_ENGINE_OUTPUT", "Invalid engine response");
    }
}
