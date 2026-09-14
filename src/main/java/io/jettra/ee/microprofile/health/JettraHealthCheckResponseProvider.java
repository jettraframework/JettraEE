package io.jettra.ee.microprofile.health;

import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.spi.HealthCheckResponseProvider;

/**
 * Proveedor SPI de MicroProfile Health para JettraEE.
 */
public class JettraHealthCheckResponseProvider implements HealthCheckResponseProvider {

    @Override
    public HealthCheckResponseBuilder createResponseBuilder() {
        return new JettraHealthCheckResponseBuilder();
    }
}
