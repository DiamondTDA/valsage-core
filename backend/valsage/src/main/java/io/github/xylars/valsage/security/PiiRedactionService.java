package io.github.xylars.valsage.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Baseline detector only: expand with jurisdiction-specific identifiers and policy review. */
@Component
public class PiiRedactionService {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern CARD_CANDIDATE = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    public String redact(String input) {
        String emailSafe = EMAIL.matcher(input).replaceAll("[EMAIL_REDACTED]");
        Matcher matcher = CARD_CANDIDATE.matcher(emailSafe);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            matcher.appendReplacement(result, luhnValid(digits) ? "[CARD_REDACTED]" : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
    public boolean containsSensitiveData(String input) { return !redact(input).equals(input); }
    private static boolean luhnValid(String digits) {
        if (digits.length() < 13 || digits.length() > 19) return false;
        int sum = 0; boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (doubleIt) { n *= 2; if (n > 9) n -= 9; }
            sum += n; doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
