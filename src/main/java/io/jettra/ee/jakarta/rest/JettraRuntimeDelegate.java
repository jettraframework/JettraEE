package io.jettra.ee.jakarta.rest;

import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.RuntimeDelegate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Delegado de tiempo de ejecución de Jakarta REST para JettraEE.
 */
public class JettraRuntimeDelegate extends RuntimeDelegate {

    @Override
    public UriBuilder createUriBuilder() {
        return new JettraUriBuilder();
    }

    @Override
    public Response.ResponseBuilder createResponseBuilder() {
        return new JettraResponseBuilder();
    }

    @Override
    public Variant.VariantListBuilder createVariantListBuilder() {
        return null;
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType) throws IllegalArgumentException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) throws IllegalArgumentException {
        if (MediaType.class.isAssignableFrom(type)) {
            return (HeaderDelegate<T>) new HeaderDelegate<MediaType>() {
                @Override
                public MediaType fromString(String value) {
                    if (value == null) return null;
                    String[] parts = value.split(";");
                    String[] slash = parts[0].trim().split("/");
                    String t = slash.length > 0 ? slash[0].trim() : "*";
                    String s = slash.length > 1 ? slash[1].trim() : "*";
                    Map<String, String> params = new HashMap<>();
                    for (int i = 1; i < parts.length; i++) {
                        String[] eq = parts[i].split("=");
                        if (eq.length == 2) {
                            params.put(eq[0].trim(), eq[1].trim());
                        }
                    }
                    return new MediaType(t, s, params);
                }
                @Override
                public String toString(MediaType value) {
                    if (value == null) return null;
                    StringBuilder b = new StringBuilder();
                    b.append(value.getType()).append('/').append(value.getSubtype());
                    for (Map.Entry<String, String> entry : value.getParameters().entrySet()) {
                        b.append(';').append(entry.getKey()).append('=').append(entry.getValue());
                    }
                    return b.toString();
                }
            };
        }
        return (HeaderDelegate<T>) new HeaderDelegate<Object>() {
            @Override
            public Object fromString(String value) { return value; }
            @Override
            public String toString(Object value) { return value != null ? value.toString() : null; }
        };
    }

    @Override
    public Link.Builder createLinkBuilder() {
        return null;
    }

    @Override
    public SeBootstrap.Configuration.Builder createConfigurationBuilder() {
        return null;
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Application application, SeBootstrap.Configuration configuration) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Class<? extends Application> clazz, SeBootstrap.Configuration configuration) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public EntityPart.Builder createEntityPartBuilder(String partName) throws IllegalArgumentException {
        return null;
    }
}
