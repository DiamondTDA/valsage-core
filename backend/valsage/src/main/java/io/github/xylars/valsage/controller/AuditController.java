package io.github.xylars.valsage.controller;


import io.github.xylars.valsage.dto.AuditRequest;
import io.github.xylars.valsage.dto.AuditResponse;
import io.github.xylars.valsage.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {
    private final AuditService auditService;
    public AuditController(AuditService auditService) { this.auditService = auditService; }
    @PostMapping
    public AuditResponse audit(@Valid @RequestBody AuditRequest request, Authentication authentication) {
        return auditService.audit(request.inquiry(), authentication);
    }
}
