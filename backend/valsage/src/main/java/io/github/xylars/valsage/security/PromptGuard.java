package io.github.xylars.valsage.security;

import java.util.List;
import java.util.regex.Pattern;

import io.github.xylars.valsage.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** High-confidence patterns only; model-level trust boundaries are still mandatory. */
@Component
public class PromptGuard {
    private static final List<Pattern> BLOCK = List.of(
            Pattern.compile("(?is)\\b(?:ignore|disregard|override)\\s+(?:all\\s+)?(?:previous|prior|system|developer)\\s+(?:instructions|prompts|rules)\\b"),
            Pattern.compile("(?is)\\b(?:reveal|print|dump|expose)\\s+(?:the\\s+)?(?:system|developer)\\s+(?:prompt|instructions)\\b"),
            Pattern.compile("(?is)\\b(?:you are now|act as)\\s+(?:the\\s+)?(?:system|developer)\\b")
    );
    public void inspect(String inquiry) {
        for (Pattern p : BLOCK) {
            if (p.matcher(inquiry).find()) {
                throw new GatewayException(HttpStatus.BAD_REQUEST, "PROMPT_REJECTED",
                        "Inquiry rejected by input security policy");
            }
        }
    }
}
