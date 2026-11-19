package io.github.xylars.valsage.service;


import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import io.github.xylars.valsage.dto.AuditResponse;
import io.github.xylars.valsage.exception.GatewayException;
import io.github.xylars.valsage.security.EngineResponseValidator;
import io.github.xylars.valsage.security.PiiRedactionService;
import io.github.xylars.valsage.security.PromptGuard;
import io.github.xylars.valsage.security.PythonEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final PythonEngineClient client;
    private final PromptGuard promptGuard;
    private final PiiRedactionService pii;
    private final EngineResponseValidator outputValidator;
    private final Semaphore inferencePermits;
    public AuditService(PythonEngineClient client, PromptGuard promptGuard,
            PiiRedactionService pii, EngineResponseValidator outputValidator,
            @Value("${security-pipeline.max-concurrent-inferences}") int maxConcurrent) {
        this.client = client; this.promptGuard = promptGuard; this.pii = pii;
        this.outputValidator = outputValidator;
        this.inferencePermits = new Semaphore(maxConcurrent);
    }
    public AuditResponse audit(String inquiry, Authentication auth) {
        String outcome = "FAILED";

        try {
            if (auth == null || !auth.isAuthenticated()
                    || auth instanceof AnonymousAuthenticationToken) {
                throw new AccessDeniedException("Authentication required");
            }

            Set<String> authorities = auth.getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toUnmodifiableSet());

            promptGuard.inspect(inquiry);

            String sanitized = pii.redact(inquiry);

            if (!inferencePermits.tryAcquire()) {
                throw new GatewayException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "ENGINE_BUSY",
                        "Audit capacity exceeded"
                );
            }

            try {
                AuditResponse safe = outputValidator.validate(
                        client.audit(sanitized, authorities)
                );

                outcome = "SUCCESS";
                return safe;

            } finally {
                inferencePermits.release();
            }

        } finally {
            log.info(
                    "security_audit outcome={} principal_present={}",
                    outcome,
                    auth != null
            );
        }
    }
}
