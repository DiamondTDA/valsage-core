package io.github.xylars.valsage.security;

import io.github.xylars.valsage.dto.AuditRequest;
import io.github.xylars.valsage.dto.AuditResponse;
import io.github.xylars.valsage.dto.EngineAuditRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;

@Component
public class PythonEngineClient {

    private final RestClient restClient;

    public PythonEngineClient(RestClient engineRestClient) {
        this.restClient = engineRestClient;
    }

    public AuditResponse audit(String inquiry, Set<String> authorities) {
        return restClient.post()
                .uri("/audit")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new EngineAuditRequest(inquiry, authorities))
                .retrieve()
                .body(AuditResponse.class);
    }

    public boolean isHealthy() {
        var response = restClient.get()
                .uri("/health")
                .retrieve()
                .body(EngineHealth.class);

        return response != null
                && "ok".equals(response.status());
    }

    private record EngineHealth(String status) {}
}