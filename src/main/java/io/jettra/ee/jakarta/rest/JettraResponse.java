package io.jettra.ee.jakarta.rest;

import jakarta.ws.rs.core.*;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.*;

/**
 * Implementación de Response para Jakarta REST en JettraEE.
 */
public class JettraResponse extends Response {

    private final int status;
    private final Object entity;
    private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    private final MediaType mediaType;

    public JettraResponse(int status, Object entity, MultivaluedMap<String, Object> headers, MediaType mediaType) {
        this.status = status;
        this.entity = entity;
        if (headers != null) {
            this.headers.putAll(headers);
        }
        this.mediaType = mediaType;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public StatusType getStatusInfo() {
        return Status.fromStatusCode(status) != null ? Status.fromStatusCode(status) : new StatusType() {
            @Override public int getStatusCode() { return status; }
            @Override public Status.Family getFamily() { return Status.Family.familyOf(status); }
            @Override public String getReasonPhrase() { return "Status " + status; }
        };
    }

    @Override
    public Object getEntity() {
        return entity;
    }

    @Override
    public <T> T readEntity(Class<T> entityType) {
        if (entity == null) return null;
        if (entityType.isInstance(entity)) {
            return entityType.cast(entity);
        }
        return null;
    }

    @Override
    public <T> T readEntity(GenericType<T> entityType) {
        return null;
    }

    @Override
    public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
        return readEntity(entityType);
    }

    @Override
    public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
        return null;
    }

    @Override
    public boolean hasEntity() {
        return entity != null;
    }

    @Override
    public boolean bufferEntity() {
        return false;
    }

    @Override
    public void close() {}

    @Override
    public MediaType getMediaType() {
        return mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE;
    }

    @Override
    public Locale getLanguage() {
        return null;
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public Set<String> getAllowedMethods() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return Collections.emptyMap();
    }

    @Override
    public EntityTag getEntityTag() {
        return null;
    }

    @Override
    public Date getDate() {
        return null;
    }

    @Override
    public Date getLastModified() {
        return null;
    }

    @Override
    public URI getLocation() {
        return null;
    }

    @Override
    public Set<Link> getLinks() {
        return Collections.emptySet();
    }

    @Override
    public boolean hasLink(String relation) {
        return false;
    }

    @Override
    public Link getLink(String relation) {
        return null;
    }

    @Override
    public Link.Builder getLinkBuilder(String relation) {
        return null;
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return headers;
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        MultivaluedMap<String, String> res = new MultivaluedHashMap<>();
        for (Map.Entry<String, List<Object>> e : headers.entrySet()) {
            List<String> list = new ArrayList<>();
            for (Object obj : e.getValue()) {
                if (obj != null) list.add(obj.toString());
            }
            res.put(e.getKey(), list);
        }
        return res;
    }

    @Override
    public String getHeaderString(String name) {
        List<Object> list = headers.get(name);
        if (list == null || list.isEmpty()) return null;
        return list.get(0) != null ? list.get(0).toString() : null;
    }
}
