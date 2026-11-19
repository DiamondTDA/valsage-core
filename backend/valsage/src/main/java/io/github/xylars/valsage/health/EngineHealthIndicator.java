package io.github.xylars.valsage.health;


import io.github.xylars.valsage.security.PythonEngineClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** In production, expose this as readiness, not publicly as detailed health. */
@Component("pythonEngine")
public class EngineHealthIndicator implements HealthIndicator {
    private final PythonEngineClient client;
    public EngineHealthIndicator(PythonEngineClient client) { this.client = client; }
    @Override public Health health() {
        return client.isHealthy() ? Health.up().build() : Health.down().build();
    }
}
