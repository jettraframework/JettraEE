package io.jettra.ee.microprofile.health;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación de HealthCheckResponseBuilder para MicroProfile Health 4.0 en JettraEE.
 */
public class JettraHealthCheckResponseBuilder extends HealthCheckResponseBuilder {

    private String name = "jettra-health";
    private boolean isUp = true;
    private final Map<String, Object> data = new HashMap<>();

    @Override
    public HealthCheckResponseBuilder name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public HealthCheckResponseBuilder withData(String key, String value) {
        data.put(key, value);
        return this;
    }

    @Override
    public HealthCheckResponseBuilder withData(String key, long value) {
        data.put(key, value);
        return this;
    }

    @Override
    public HealthCheckResponseBuilder withData(String key, boolean value) {
        data.put(key, value);
        return this;
    }

    @Override
    public HealthCheckResponseBuilder up() {
        this.isUp = true;
        return this;
    }

    @Override
    public HealthCheckResponseBuilder down() {
        this.isUp = false;
        return this;
    }

    @Override
    public HealthCheckResponseBuilder status(boolean up) {
        this.isUp = up;
        return this;
    }

    @Override
    public HealthCheckResponse build() {
        HealthCheckResponse.Status status = isUp ? HealthCheckResponse.Status.UP : HealthCheckResponse.Status.DOWN;
        Optional<Map<String, Object>> dataOpt = data.isEmpty() ? Optional.empty() : Optional.of(new HashMap<>(data));
        return new HealthCheckResponse(name, status, dataOpt);
    }
}
