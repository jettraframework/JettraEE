package io.jettra.ee.jakarta.rest;

import jakarta.ws.rs.core.*;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;

/**
 * Constructor de respuestas Jakarta REST para JettraEE.
 */
public class JettraResponseBuilder extends Response.ResponseBuilder {

    private int status = 200;
    private Object entity;
    private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    private MediaType mediaType;

    @Override
    public Response build() {
        return new JettraResponse(status, entity, headers, mediaType);
    }

    @Override
    public Response.ResponseBuilder clone() {
        JettraResponseBuilder copy = new JettraResponseBuilder();
        copy.status = this.status;
        copy.entity = this.entity;
        copy.headers.putAll(this.headers);
        copy.mediaType = this.mediaType;
        return copy;
    }

    @Override
    public Response.ResponseBuilder status(int status) {
        this.status = status;
        return this;
    }

    @Override
    public Response.ResponseBuilder status(int status, String reasonPhrase) {
        this.status = status;
        return this;
    }

    @Override
    public Response.ResponseBuilder status(Response.StatusType status) {
        if (status != null) {
            this.status = status.getStatusCode();
        }
        return this;
    }

    @Override
    public Response.ResponseBuilder status(Response.Status status) {
        if (status != null) {
            this.status = status.getStatusCode();
        }
        return this;
    }

    @Override
    public Response.ResponseBuilder entity(Object entity) {
        this.entity = entity;
        return this;
    }

    @Override
    public Response.ResponseBuilder entity(Object entity, Annotation[] annotations) {
        this.entity = entity;
        return this;
    }

    @Override
    public Response.ResponseBuilder allow(String... methods) {
        return this;
    }

    @Override
    public Response.ResponseBuilder allow(Set<String> methods) {
        return this;
    }

    @Override
    public Response.ResponseBuilder cacheControl(CacheControl cacheControl) {
        return this;
    }

    @Override
    public Response.ResponseBuilder encoding(String encoding) {
        header(HttpHeaders.CONTENT_ENCODING, encoding);
        return this;
    }

    @Override
    public Response.ResponseBuilder header(String name, Object value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public Response.ResponseBuilder replaceAll(MultivaluedMap<String, Object> headers) {
        this.headers.clear();
        if (headers != null) {
            this.headers.putAll(headers);
        }
        return this;
    }

    @Override
    public Response.ResponseBuilder language(String language) {
        header(HttpHeaders.CONTENT_LANGUAGE, language);
        return this;
    }

    @Override
    public Response.ResponseBuilder language(Locale locale) {
        if (locale != null) {
            header(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
        }
        return this;
    }

    @Override
    public Response.ResponseBuilder type(MediaType type) {
        this.mediaType = type;
        if (type != null) {
            header(HttpHeaders.CONTENT_TYPE, type.toString());
        }
        return this;
    }

    @Override
    public Response.ResponseBuilder type(String type) {
        return type(type != null ? MediaType.valueOf(type) : null);
    }

    @Override
    public Response.ResponseBuilder variant(Variant variant) {
        return this;
    }

    @Override
    public Response.ResponseBuilder contentLocation(URI location) {
        return this;
    }

    @Override
    public Response.ResponseBuilder cookie(NewCookie... cookies) {
        return this;
    }

    @Override
    public Response.ResponseBuilder expires(Date expires) {
        return this;
    }

    @Override
    public Response.ResponseBuilder lastModified(Date lastModified) {
        return this;
    }

    @Override
    public Response.ResponseBuilder location(URI location) {
        if (location != null) {
            header(HttpHeaders.LOCATION, location.toString());
        }
        return this;
    }

    @Override
    public Response.ResponseBuilder tag(EntityTag tag) {
        return this;
    }

    @Override
    public Response.ResponseBuilder tag(String tag) {
        return this;
    }

    @Override
    public Response.ResponseBuilder variants(Variant... variants) {
        return this;
    }

    @Override
    public Response.ResponseBuilder variants(List<Variant> variants) {
        return this;
    }

    @Override
    public Response.ResponseBuilder links(Link... links) {
        return this;
    }

    @Override
    public Response.ResponseBuilder link(URI uri, String rel) {
        return this;
    }

    @Override
    public Response.ResponseBuilder link(String uri, String rel) {
        return this;
    }
}
