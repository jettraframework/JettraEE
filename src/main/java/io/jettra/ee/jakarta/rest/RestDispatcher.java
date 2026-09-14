package io.jettra.ee.jakarta.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.core.IO;
import io.jettra.ee.jakarta.cdi.JettraCDIContainer;
import io.jettra.ee.jakarta.validation.ValidationEngine;
import io.jettra.json.JettraJson;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Despachador de peticiones Jakarta REST (JAX-RS 3.1 / 4.0) de alto rendimiento para JettraEE.
 * Enruta peticiones HTTP hacia métodos anotados con @Path, @GET, @POST, @PUT, @DELETE, etc.
 * Resuelve parámetros, ejecuta validaciones Jakarta/JettraRules y autorizaciones de seguridad.
 */
public class RestDispatcher implements HttpHandler {

    private final JettraJson json = new JettraJson();
    private final List<RestEndpoint> endpoints = new ArrayList<>();
    private final String contextPath;

    public RestDispatcher(String contextPath) {
        this.contextPath = (contextPath == null || contextPath.isBlank() || contextPath.equals("/"))
                ? ""
                : (contextPath.startsWith("/") ? contextPath : "/" + contextPath);
    }

    public static class RestEndpoint {
        public final String httpMethod;
        public final Pattern pathPattern;
        public final List<String> pathParamNames;
        public final Method method;
        public final Class<?> resourceClass;
        public final String produces;
        public final String consumes;
        public final boolean isPermitAll;
        public final boolean isDenyAll;
        public final Set<String> rolesAllowed;

        public RestEndpoint(String httpMethod, Pattern pathPattern, List<String> pathParamNames, Method method,
                            Class<?> resourceClass, String produces, String consumes,
                            boolean isPermitAll, boolean isDenyAll, Set<String> rolesAllowed) {
            this.httpMethod = httpMethod;
            this.pathPattern = pathPattern;
            this.pathParamNames = pathParamNames;
            this.method = method;
            this.resourceClass = resourceClass;
            this.produces = produces != null ? produces : MediaType.APPLICATION_JSON;
            this.consumes = consumes != null ? consumes : MediaType.APPLICATION_JSON;
            this.isPermitAll = isPermitAll;
            this.isDenyAll = isDenyAll;
            this.rolesAllowed = rolesAllowed != null ? rolesAllowed : Collections.emptySet();
        }
    }

    /**
     * Registra una clase de recurso Jakarta REST (@Path).
     */
    public void registerResource(Class<?> resourceClass) {
        if (resourceClass.isInterface() || java.lang.reflect.Modifier.isAbstract(resourceClass.getModifiers())) {
            return;
        }
        if (!resourceClass.isAnnotationPresent(Path.class)) {
            return;
        }

        Path classPathAnn = resourceClass.getAnnotation(Path.class);
        String baseClassPath = normalizePath(classPathAnn.value());

        // Registrar también en CDI
        JettraCDIContainer.getInstance().registerBean(resourceClass);

        for (Method method : resourceClass.getDeclaredMethods()) {
            String httpMethod = getHttpMethod(method);
            if (httpMethod == null) {
                continue;
            }

            String methodPath = "";
            if (method.isAnnotationPresent(Path.class)) {
                methodPath = normalizePath(method.getAnnotation(Path.class).value());
            }

            String fullPath = normalizePath(baseClassPath + methodPath);
            List<String> pathParamNames = new ArrayList<>();
            Pattern pattern = compilePathPattern(fullPath, pathParamNames);

            String produces = MediaType.APPLICATION_JSON;
            if (method.isAnnotationPresent(Produces.class)) {
                produces = method.getAnnotation(Produces.class).value()[0];
            } else if (resourceClass.isAnnotationPresent(Produces.class)) {
                produces = resourceClass.getAnnotation(Produces.class).value()[0];
            }

            String consumes = MediaType.APPLICATION_JSON;
            if (method.isAnnotationPresent(Consumes.class)) {
                consumes = method.getAnnotation(Consumes.class).value()[0];
            } else if (resourceClass.isAnnotationPresent(Consumes.class)) {
                consumes = resourceClass.getAnnotation(Consumes.class).value()[0];
            }

            boolean permitAll = method.isAnnotationPresent(PermitAll.class) || resourceClass.isAnnotationPresent(PermitAll.class);
            boolean denyAll = method.isAnnotationPresent(DenyAll.class) || resourceClass.isAnnotationPresent(DenyAll.class);
            Set<String> roles = new HashSet<>();
            if (method.isAnnotationPresent(RolesAllowed.class)) {
                roles.addAll(Arrays.asList(method.getAnnotation(RolesAllowed.class).value()));
            } else if (resourceClass.isAnnotationPresent(RolesAllowed.class)) {
                roles.addAll(Arrays.asList(resourceClass.getAnnotation(RolesAllowed.class).value()));
            }

            RestEndpoint endpoint = new RestEndpoint(httpMethod, pattern, pathParamNames, method, resourceClass,
                    produces, consumes, permitAll, denyAll, roles);
            endpoints.add(endpoint);
            IO.info("Endpoint Jakarta REST registrado: [" + httpMethod + "] " + fullPath + " -> " + resourceClass.getSimpleName() + "." + method.getName());
        }
    }

    public List<RestEndpoint> getEndpoints() {
        return Collections.unmodifiableList(endpoints);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        String fullPath = exchange.getRequestURI().getPath();

        // Eliminar contextPath si está presente
        String relativePath = fullPath;
        if (!contextPath.isEmpty() && relativePath.startsWith(contextPath)) {
            relativePath = relativePath.substring(contextPath.length());
        }
        if (relativePath.isEmpty()) {
            relativePath = "/";
        }

        RestEndpoint matchedEndpoint = null;
        Matcher matchedMatcher = null;

        for (RestEndpoint ep : endpoints) {
            if (ep.httpMethod.equalsIgnoreCase(requestMethod)) {
                Matcher matcher = ep.pathPattern.matcher(relativePath);
                if (matcher.matches()) {
                    matchedEndpoint = ep;
                    matchedMatcher = matcher;
                    break;
                }
            }
        }

        if (matchedEndpoint == null) {
            // Verificar si la ruta coincide pero con otro método (405 Method Not Allowed)
            boolean pathMatchedDifferentMethod = false;
            for (RestEndpoint ep : endpoints) {
                if (ep.pathPattern.matcher(relativePath).matches()) {
                    pathMatchedDifferentMethod = true;
                    break;
                }
            }

            if (pathMatchedDifferentMethod) {
                sendError(exchange, 405, "Method Not Allowed: " + requestMethod);
            } else {
                sendError(exchange, 404, "Resource Not Found: " + relativePath);
            }
            return;
        }

        // 1. Verificación de Seguridad
        if (matchedEndpoint.isDenyAll) {
            sendError(exchange, 403, "Access Denied (@DenyAll)");
            return;
        }

        SecurityContext secContext = createSecurityContext(exchange);
        if (!matchedEndpoint.rolesAllowed.isEmpty() && !matchedEndpoint.isPermitAll) {
            if (secContext.getUserPrincipal() == null) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                sendError(exchange, 401, "Authentication Required");
                return;
            }
            boolean hasRole = false;
            for (String r : matchedEndpoint.rolesAllowed) {
                if (secContext.isUserInRole(r)) {
                    hasRole = true;
                    break;
                }
            }
            if (!hasRole) {
                sendError(exchange, 403, "Forbidden: Missing required role");
                return;
            }
        }

        // 2. Extraer Parámetros
        Map<String, String> pathParams = new HashMap<>();
        for (int i = 0; i < matchedEndpoint.pathParamNames.size(); i++) {
            String val = matchedMatcher.group(i + 1);
            pathParams.put(matchedEndpoint.pathParamNames.get(i), URLDecoder.decode(val, StandardCharsets.UTF_8));
        }

        Map<String, List<String>> queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());

        try {
            // 3. Invocar Método de Recurso
            Object targetInstance = JettraCDIContainer.getInstance().getBean(matchedEndpoint.resourceClass);
            Object[] args = resolveMethodArgs(matchedEndpoint.method, exchange, pathParams, queryParams, secContext);

            // Validar argumentos si están anotados con @Valid
            for (int i = 0; i < matchedEndpoint.method.getParameterCount(); i++) {
                Parameter p = matchedEndpoint.method.getParameters()[i];
                if (p.isAnnotationPresent(Valid.class) && args[i] != null) {
                    var errors = ValidationEngine.validate(args[i]);
                    if (!errors.isEmpty()) {
                        sendJsonResponse(exchange, 400, Map.of(
                                "error", "Validation Failed",
                                "details", errors.stream().map(ValidationEngine.ValidationError::toString).toList()
                        ));
                        return;
                    }
                }
            }

            matchedEndpoint.method.setAccessible(true);
            Object result = matchedEndpoint.method.invoke(targetInstance, args);
            handleResult(exchange, result, matchedEndpoint.produces);

        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            IO.error("Error invocando endpoint REST: " + matchedEndpoint.method.getName(), cause);
            sendError(exchange, 500, "Internal Server Error: " + cause.getMessage());
        } finally {
            JettraCDIContainer.getInstance().clearRequestScope();
        }
    }

    private Object[] resolveMethodArgs(Method method, HttpExchange exchange, Map<String, String> pathParams,
                                       Map<String, List<String>> queryParams, SecurityContext secContext) throws Exception {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> pType = p.getType();

            // @PathParam
            if (p.isAnnotationPresent(PathParam.class)) {
                String name = p.getAnnotation(PathParam.class).value();
                args[i] = convertStringToType(pathParams.get(name), pType);
            }
            // @QueryParam
            else if (p.isAnnotationPresent(QueryParam.class)) {
                String name = p.getAnnotation(QueryParam.class).value();
                List<String> vals = queryParams.get(name);
                String val = (vals != null && !vals.isEmpty()) ? vals.get(0) : null;
                if (val == null && p.isAnnotationPresent(DefaultValue.class)) {
                    val = p.getAnnotation(DefaultValue.class).value();
                }
                args[i] = convertStringToType(val, pType);
            }
            // @HeaderParam
            else if (p.isAnnotationPresent(HeaderParam.class)) {
                String name = p.getAnnotation(HeaderParam.class).value();
                String val = exchange.getRequestHeaders().getFirst(name);
                if (val == null && p.isAnnotationPresent(DefaultValue.class)) {
                    val = p.getAnnotation(DefaultValue.class).value();
                }
                args[i] = convertStringToType(val, pType);
            }
            // @Context
            else if (p.isAnnotationPresent(Context.class)) {
                if (SecurityContext.class.isAssignableFrom(pType)) {
                    args[i] = secContext;
                } else if (HttpExchange.class.isAssignableFrom(pType)) {
                    args[i] = exchange;
                } else if (UriInfo.class.isAssignableFrom(pType)) {
                    args[i] = createUriInfo(exchange);
                } else if (HttpHeaders.class.isAssignableFrom(pType)) {
                    args[i] = createHttpHeaders(exchange);
                }
            }
            // Request Body / Payload
            else {
                String body = readRequestBody(exchange);
                if (body != null && !body.trim().isEmpty()) {
                    if (pType == String.class) {
                        args[i] = body;
                    } else {
                        args[i] = json.fromJson(body, pType);
                    }
                }
            }
        }
        return args;
    }

    private void handleResult(HttpExchange exchange, Object result, String defaultProduces) throws IOException {
        if (result == null) {
            exchange.sendResponseHeaders(204, -1);
            exchange.getResponseBody().close();
            return;
        }

        if (result instanceof Response res) {
            int status = res.getStatus();
            MultivaluedMap<String, Object> meta = res.getMetadata();
            for (Map.Entry<String, List<Object>> entry : meta.entrySet()) {
                for (Object v : entry.getValue()) {
                    exchange.getResponseHeaders().add(entry.getKey(), v.toString());
                }
            }

            Object entity = res.getEntity();
            if (entity == null) {
                exchange.sendResponseHeaders(status, -1);
                exchange.getResponseBody().close();
                return;
            }

            String contentType = res.getMediaType() != null ? res.getMediaType().toString() : defaultProduces;
            exchange.getResponseHeaders().set("Content-Type", contentType);

            byte[] bytes;
            if (entity instanceof String s) {
                bytes = s.getBytes(StandardCharsets.UTF_8);
            } else {
                bytes = json.toJson(entity).getBytes(StandardCharsets.UTF_8);
            }

            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        // POJO / String / Collection directo
        exchange.getResponseHeaders().set("Content-Type", defaultProduces);
        byte[] bytes;
        if (result instanceof String s) {
            bytes = s.getBytes(StandardCharsets.UTF_8);
        } else {
            bytes = json.toJson(result).getBytes(StandardCharsets.UTF_8);
        }

        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Object convertStringToType(String val, Class<?> type) {
        if (val == null) return null;
        if (type == String.class) return val;
        if (type == Integer.class || type == int.class) return Integer.parseInt(val);
        if (type == Long.class || type == long.class) return Long.parseLong(val);
        if (type == Double.class || type == double.class) return Double.parseDouble(val);
        if (type == Float.class || type == float.class) return Float.parseFloat(val);
        if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(val);
        if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, val);
        }
        return val;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendError(HttpExchange exchange, int status, String msg) throws IOException {
        byte[] b = json.toJson(Map.of("status", status, "message", msg)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON);
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] b = json.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON);
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    private SecurityContext createSecurityContext(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String username = null;
        Set<String> userRoles = new HashSet<>();

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // Validación básica de token o integración con JettraJWT
                String secret = "default_secret_key_jettra_ee_2026";
                io.jettra.jwt.JettraJWT jwt = new io.jettra.jwt.JettraJWT(secret, 3600000);
                Map<String, Object> payload = jwt.getPayload(token);
                if (payload != null) {
                    username = jwt.extractUsername(token);
                    Object rolesObj = payload.get("roles");
                    if (rolesObj instanceof List<?> rList) {
                        for (Object r : rList) {
                            userRoles.add(r.toString());
                        }
                    } else if (rolesObj instanceof String rStr) {
                        userRoles.addAll(Arrays.asList(rStr.split(",")));
                    }
                }
            } catch (Exception ignored) {}
        }

        final String finalUser = username;
        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return finalUser != null ? () -> finalUser : null;
            }

            @Override
            public boolean isUserInRole(String role) {
                return userRoles.contains(role);
            }

            @Override
            public boolean isSecure() {
                return "https".equalsIgnoreCase(exchange.getRequestURI().getScheme());
            }

            @Override
            public String getAuthenticationScheme() {
                return "BEARER";
            }
        };
    }

    private UriInfo createUriInfo(HttpExchange exchange) {
        return new UriInfo() {
            @Override public String getPath() { return exchange.getRequestURI().getPath(); }
            @Override public String getPath(boolean decode) { return getPath(); }
            @Override public List<PathSegment> getPathSegments() { return Collections.emptyList(); }
            @Override public List<PathSegment> getPathSegments(boolean decode) { return Collections.emptyList(); }
            @Override public URI getRequestUri() { return exchange.getRequestURI(); }
            @Override public UriBuilder getRequestUriBuilder() { return UriBuilder.fromUri(getRequestUri()); }
            @Override public URI getAbsolutePath() { return exchange.getRequestURI(); }
            @Override public UriBuilder getAbsolutePathBuilder() { return UriBuilder.fromUri(getAbsolutePath()); }
            @Override public URI getBaseUri() { return URI.create("http://localhost/"); }
            @Override public UriBuilder getBaseUriBuilder() { return UriBuilder.fromUri(getBaseUri()); }
            @Override public MultivaluedMap<String, String> getPathParameters() { return new MultivaluedHashMap<>(); }
            @Override public MultivaluedMap<String, String> getPathParameters(boolean decode) { return new MultivaluedHashMap<>(); }
            @Override public MultivaluedMap<String, String> getQueryParameters() { return new MultivaluedHashMap<>(); }
            @Override public MultivaluedMap<String, String> getQueryParameters(boolean decode) { return new MultivaluedHashMap<>(); }
            @Override public List<String> getMatchedURIs() { return Collections.emptyList(); }
            @Override public List<String> getMatchedURIs(boolean decode) { return Collections.emptyList(); }
            @Override public List<Object> getMatchedResources() { return Collections.emptyList(); }
            @Override public URI resolve(URI uri) { return uri; }
            @Override public URI relativize(URI uri) { return uri; }
        };
    }

    private HttpHeaders createHttpHeaders(HttpExchange exchange) {
        return new HttpHeaders() {
            @Override public List<String> getRequestHeader(String name) { return exchange.getRequestHeaders().get(name); }
            @Override public String getHeaderString(String name) { return exchange.getRequestHeaders().getFirst(name); }
            @Override public MultivaluedMap<String, String> getRequestHeaders() {
                MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
                map.putAll(exchange.getRequestHeaders());
                return map;
            }
            @Override public List<MediaType> getAcceptableMediaTypes() { return Collections.singletonList(MediaType.APPLICATION_JSON_TYPE); }
            @Override public List<Locale> getAcceptableLanguages() { return Collections.singletonList(Locale.getDefault()); }
            @Override public MediaType getMediaType() {
                String ct = exchange.getRequestHeaders().getFirst("Content-Type");
                return ct != null ? MediaType.valueOf(ct) : MediaType.APPLICATION_JSON_TYPE;
            }
            @Override public Locale getLanguage() { return Locale.getDefault(); }
            @Override public Map<String, Cookie> getCookies() { return Collections.emptyMap(); }
            @Override public Date getDate() { return new Date(); }
            @Override public int getLength() { return -1; }
        };
    }

    private String getHttpMethod(Method method) {
        if (method.isAnnotationPresent(GET.class)) return "GET";
        if (method.isAnnotationPresent(POST.class)) return "POST";
        if (method.isAnnotationPresent(PUT.class)) return "PUT";
        if (method.isAnnotationPresent(DELETE.class)) return "DELETE";
        if (method.isAnnotationPresent(PATCH.class)) return "PATCH";
        if (method.isAnnotationPresent(HEAD.class)) return "HEAD";
        if (method.isAnnotationPresent(OPTIONS.class)) return "OPTIONS";
        return null;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String p = path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        return p;
    }

    private Pattern compilePathPattern(String fullPath, List<String> paramNames) {
        StringBuilder regex = new StringBuilder("^");
        String[] segments = fullPath.split("/");
        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            regex.append("/");
            if (seg.startsWith("{") && seg.endsWith("}")) {
                String paramName = seg.substring(1, seg.length() - 1);
                paramNames.add(paramName);
                regex.append("([^/]+)");
            } else {
                regex.append(Pattern.quote(seg));
            }
        }
        if (regex.length() == 1) {
            regex.append("/");
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    private Map<String, List<String>> parseQueryParams(String rawQuery) {
        Map<String, List<String>> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return map;

        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf("=");
            String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
            String val = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }
        return map;
    }
}
