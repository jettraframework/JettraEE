package io.jettra.ee.jakarta.rest;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriBuilderException;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;

/**
 * Constructor de URIs para Jakarta REST en JettraEE.
 */
public class JettraUriBuilder extends UriBuilder {

    private String scheme = "http";
    private String host = "localhost";
    private int port = -1;
    private StringBuilder path = new StringBuilder();
    private StringBuilder query = new StringBuilder();
    private String fragment;

    @Override
    public UriBuilder clone() {
        JettraUriBuilder b = new JettraUriBuilder();
        b.scheme = this.scheme;
        b.host = this.host;
        b.port = this.port;
        b.path = new StringBuilder(this.path);
        b.query = new StringBuilder(this.query);
        b.fragment = this.fragment;
        return b;
    }

    @Override
    public UriBuilder uri(URI uri) {
        if (uri != null) {
            if (uri.getScheme() != null) this.scheme = uri.getScheme();
            if (uri.getHost() != null) this.host = uri.getHost();
            this.port = uri.getPort();
            if (uri.getPath() != null) this.path = new StringBuilder(uri.getPath());
            if (uri.getQuery() != null) this.query = new StringBuilder(uri.getQuery());
            this.fragment = uri.getFragment();
        }
        return this;
    }

    @Override
    public UriBuilder uri(String uriTemplate) {
        if (uriTemplate != null) {
            return uri(URI.create(uriTemplate));
        }
        return this;
    }

    @Override
    public UriBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    @Override
    public UriBuilder schemeSpecificPart(String ssp) {
        return this;
    }

    @Override
    public UriBuilder userInfo(String ui) {
        return this;
    }

    @Override
    public UriBuilder host(String host) {
        this.host = host;
        return this;
    }

    @Override
    public UriBuilder port(int port) {
        this.port = port;
        return this;
    }

    @Override
    public UriBuilder replacePath(String path) {
        this.path = new StringBuilder(path != null ? path : "");
        return this;
    }

    @Override
    public UriBuilder path(String path) {
        if (path != null) {
            if (!path.startsWith("/") && !this.path.isEmpty() && !this.path.toString().endsWith("/")) {
                this.path.append("/");
            }
            this.path.append(path);
        }
        return this;
    }

    @Override
    public UriBuilder path(Class resource) {
        if (resource != null && resource.isAnnotationPresent(jakarta.ws.rs.Path.class)) {
            jakarta.ws.rs.Path ann = (jakarta.ws.rs.Path) resource.getAnnotation(jakarta.ws.rs.Path.class);
            return path(ann.value());
        }
        return this;
    }

    @Override
    public UriBuilder path(Class resource, String method) {
        return path(resource);
    }

    @Override
    public UriBuilder path(Method method) {
        if (method != null && method.isAnnotationPresent(jakarta.ws.rs.Path.class)) {
            return path(method.getAnnotation(jakarta.ws.rs.Path.class).value());
        }
        return this;
    }

    @Override
    public UriBuilder segment(String... segments) {
        if (segments != null) {
            for (String seg : segments) {
                path(seg);
            }
        }
        return this;
    }

    @Override
    public UriBuilder replaceMatrix(String matrix) {
        return this;
    }

    @Override
    public UriBuilder matrixParam(String name, Object... values) {
        return this;
    }

    @Override
    public UriBuilder replaceMatrixParam(String name, Object... values) {
        return this;
    }

    @Override
    public UriBuilder replaceQuery(String query) {
        this.query = new StringBuilder(query != null ? query : "");
        return this;
    }

    @Override
    public UriBuilder queryParam(String name, Object... values) {
        if (name != null && values != null) {
            for (Object v : values) {
                if (query.length() > 0) query.append("&");
                query.append(name).append("=").append(v);
            }
        }
        return this;
    }

    @Override
    public UriBuilder replaceQueryParam(String name, Object... values) {
        return queryParam(name, values);
    }

    @Override
    public UriBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    @Override
    public UriBuilder resolveTemplate(String name, Object value) {
        return resolveTemplate(name, value, true);
    }

    @Override
    public UriBuilder resolveTemplate(String name, Object value, boolean encodeSlashInPath) {
        String template = "{" + name + "}";
        String valStr = value != null ? value.toString() : "";
        String p = this.path.toString().replace(template, valStr);
        this.path = new StringBuilder(p);
        return this;
    }

    @Override
    public UriBuilder resolveTemplateFromEncoded(String name, Object value) {
        return resolveTemplate(name, value);
    }

    @Override
    public UriBuilder resolveTemplates(Map<String, Object> templateValues) {
        if (templateValues != null) {
            for (Map.Entry<String, Object> e : templateValues.entrySet()) {
                resolveTemplate(e.getKey(), e.getValue());
            }
        }
        return this;
    }

    @Override
    public UriBuilder resolveTemplates(Map<String, Object> templateValues, boolean encodeSlashInPath) {
        return resolveTemplates(templateValues);
    }

    @Override
    public UriBuilder resolveTemplatesFromEncoded(Map<String, Object> templateValues) {
        return resolveTemplates(templateValues);
    }

    @Override
    public URI buildFromMap(Map<String, ?> values) {
        return build();
    }

    @Override
    public URI buildFromMap(Map<String, ?> values, boolean encodeSlashInPath) {
        return build();
    }

    @Override
    public URI buildFromEncodedMap(Map<String, ?> values) {
        return build();
    }

    @Override
    public URI build(Object... values) throws IllegalArgumentException, UriBuilderException {
        try {
            StringBuilder sb = new StringBuilder();
            if (scheme != null) sb.append(scheme).append("://");
            if (host != null) sb.append(host);
            if (port > 0) sb.append(":").append(port);
            if (path.length() > 0) {
                if (!path.toString().startsWith("/")) sb.append("/");
                sb.append(path);
            }
            if (query.length() > 0) sb.append("?").append(query);
            if (fragment != null) sb.append("#").append(fragment);
            return new URI(sb.toString());
        } catch (Exception e) {
            throw new UriBuilderException(e);
        }
    }

    @Override
    public URI build(Object[] values, boolean encodeSlashInPath) {
        return build(values);
    }

    @Override
    public URI buildFromEncoded(Object... values) {
        return build(values);
    }

    @Override
    public String toTemplate() {
        return path.toString();
    }
}
