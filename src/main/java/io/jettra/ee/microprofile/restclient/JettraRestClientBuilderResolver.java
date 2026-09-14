package io.jettra.ee.microprofile.restclient;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientBuilderResolver;

/**
 * Proveedor SPI de MicroProfile Rest Client para JettraEE.
 */
public class JettraRestClientBuilderResolver extends RestClientBuilderResolver {

    @Override
    public RestClientBuilder newBuilder() {
        return new JettraRestClientBuilder();
    }
}
