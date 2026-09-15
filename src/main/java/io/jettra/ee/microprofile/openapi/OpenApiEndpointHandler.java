package io.jettra.ee.microprofile.openapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.jakarta.rest.RestDispatcher;
import io.jettra.json.JettraJson;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Endpoint de MicroProfile OpenAPI 3.1 y Swagger UI interactivo para JettraEE.
 * Expone la especificación OpenAPI en /q/openapi y la UI en /q/swagger-ui con soporte
 * completo para esquemas de seguridad JWT Bearer (botón Authorize habilitado).
 */
public class OpenApiEndpointHandler implements HttpHandler {

    private final JettraJson json = new JettraJson();
    private final RestDispatcher restDispatcher;
    private final String appTitle;
    private final String appVersion;
    private final Set<Class<?>> scannedClasses = new LinkedHashSet<>();

    public OpenApiEndpointHandler(RestDispatcher restDispatcher, String appTitle, String appVersion) {
        this.restDispatcher = restDispatcher;
        this.appTitle = appTitle != null ? appTitle : "JettraEE Microservices API";
        this.appVersion = appVersion != null ? appVersion : "1.0.0";
    }

    public void registerScannedClass(Class<?> clazz) {
        if (clazz != null) {
            scannedClasses.add(clazz);
        }
    }

    public void registerScannedClasses(Collection<Class<?>> classes) {
        if (classes != null) {
            scannedClasses.addAll(classes);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.contains("swagger-ui")) {
            sendSwaggerUI(exchange);
        } else {
            sendOpenApiJson(exchange);
        }
    }

    private void sendOpenApiJson(HttpExchange exchange) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.1.0");

        // Recolectar todas las clases relevantes para inspección OpenAPI
        Set<Class<?>> allClasses = new LinkedHashSet<>(scannedClasses);
        if (restDispatcher != null) {
            for (RestDispatcher.RestEndpoint ep : restDispatcher.getEndpoints()) {
                allClasses.add(ep.resourceClass);
            }
        }

        String title = this.appTitle;
        String version = this.appVersion;
        String description = "Documentación autogenerada por el servidor JettraEE (Eclipse MicroProfile OpenAPI 3.1).";
        List<SecurityRequirement> globalSecRequirements = new ArrayList<>();

        for (Class<?> c : allClasses) {
            if (c.isAnnotationPresent(OpenAPIDefinition.class)) {
                OpenAPIDefinition def = c.getAnnotation(OpenAPIDefinition.class);
                if (def.info() != null) {
                    if (!def.info().title().isEmpty()) title = def.info().title();
                    if (!def.info().version().isEmpty()) version = def.info().version();
                    if (!def.info().description().isEmpty()) description = def.info().description();
                }
                if (def.security() != null && def.security().length > 0) {
                    globalSecRequirements.addAll(Arrays.asList(def.security()));
                }
            }
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", title);
        info.put("version", version);
        info.put("description", description);
        root.put("info", info);

        // Procesar Security Schemes para activar el botón Authorize en Swagger UI
        Map<String, Object> securitySchemes = new LinkedHashMap<>();
        for (Class<?> c : allClasses) {
            if (c.isAnnotationPresent(OpenAPIDefinition.class)) {
                OpenAPIDefinition def = c.getAnnotation(OpenAPIDefinition.class);
                if (def.components() != null && def.components().securitySchemes() != null) {
                    for (SecurityScheme ss : def.components().securitySchemes()) {
                        addSecurityScheme(securitySchemes, ss);
                    }
                }
            }
            if (c.isAnnotationPresent(SecurityScheme.class)) {
                addSecurityScheme(securitySchemes, c.getAnnotation(SecurityScheme.class));
            }
            if (c.isAnnotationPresent(SecuritySchemes.class)) {
                for (SecurityScheme ss : c.getAnnotation(SecuritySchemes.class).value()) {
                    addSecurityScheme(securitySchemes, ss);
                }
            }
        }

        // Esquema por defecto BearerAuth si no se especificó ninguno explícito
        if (securitySchemes.isEmpty()) {
            Map<String, Object> bearerAuth = new LinkedHashMap<>();
            bearerAuth.put("type", "http");
            bearerAuth.put("scheme", "bearer");
            bearerAuth.put("bearerFormat", "JWT");
            bearerAuth.put("description", "Autenticación basada en token JWT Bearer. Ingrese su token para autorizar las solicitudes.");
            securitySchemes.put("BearerAuth", bearerAuth);
        }

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", securitySchemes);
        root.put("components", components);

        // Security global
        List<Map<String, List<String>>> globalSecurity = new ArrayList<>();
        if (!globalSecRequirements.isEmpty()) {
            for (SecurityRequirement sr : globalSecRequirements) {
                Map<String, List<String>> secMap = new LinkedHashMap<>();
                secMap.put(sr.name(), Arrays.asList(sr.scopes()));
                globalSecurity.add(secMap);
            }
        } else {
            for (String schemeKey : securitySchemes.keySet()) {
                Map<String, List<String>> secMap = new LinkedHashMap<>();
                secMap.put(schemeKey, new ArrayList<>());
                globalSecurity.add(secMap);
            }
        }
        root.put("security", globalSecurity);

        // Construir Paths
        Map<String, Map<String, Object>> paths = new LinkedHashMap<>();

        if (restDispatcher != null) {
            for (RestDispatcher.RestEndpoint ep : restDispatcher.getEndpoints()) {
                String patternStr = ep.method.isAnnotationPresent(jakarta.ws.rs.Path.class)
                        ? ep.method.getAnnotation(jakarta.ws.rs.Path.class).value()
                        : "";
                jakarta.ws.rs.Path classPathAnn = ep.resourceClass.getAnnotation(jakarta.ws.rs.Path.class);
                String basePath = classPathAnn != null ? classPathAnn.value() : "";
                if (!basePath.startsWith("/")) basePath = "/" + basePath;
                if (!patternStr.startsWith("/") && !patternStr.isEmpty()) patternStr = "/" + patternStr;
                String fullPath = basePath + patternStr;
                if (fullPath.endsWith("/") && fullPath.length() > 1) fullPath = fullPath.substring(0, fullPath.length() - 1);

                Map<String, Object> pathItem = paths.computeIfAbsent(fullPath, k -> new LinkedHashMap<>());
                Map<String, Object> operationObj = new LinkedHashMap<>();

                Method m = ep.method;
                String summary = m.getName();
                String opDescription = "Operación REST en " + ep.resourceClass.getSimpleName();

                if (m.isAnnotationPresent(Operation.class)) {
                    Operation op = m.getAnnotation(Operation.class);
                    if (!op.summary().isEmpty()) summary = op.summary();
                    if (!op.description().isEmpty()) opDescription = op.description();
                }

                operationObj.put("summary", summary);
                operationObj.put("description", opDescription);

                List<String> tags = new ArrayList<>();
                if (m.isAnnotationPresent(Tag.class)) {
                    tags.add(m.getAnnotation(Tag.class).name());
                } else if (ep.resourceClass.isAnnotationPresent(Tag.class)) {
                    tags.add(ep.resourceClass.getAnnotation(Tag.class).name());
                } else {
                    tags.add(ep.resourceClass.getSimpleName().replace("Resource", "").replace("Controller", ""));
                }
                operationObj.put("tags", tags);

                // Parameters
                List<Map<String, Object>> paramsList = new ArrayList<>();
                for (Parameter p : m.getParameters()) {
                    if (p.isAnnotationPresent(jakarta.ws.rs.PathParam.class)) {
                        String name = p.getAnnotation(jakarta.ws.rs.PathParam.class).value();
                        paramsList.add(Map.of(
                                "name", name,
                                "in", "path",
                                "required", true,
                                "schema", Map.of("type", "string")
                        ));
                    } else if (p.isAnnotationPresent(jakarta.ws.rs.QueryParam.class)) {
                        String name = p.getAnnotation(jakarta.ws.rs.QueryParam.class).value();
                        paramsList.add(Map.of(
                                "name", name,
                                "in", "query",
                                "required", false,
                                "schema", Map.of("type", "string")
                        ));
                    }
                }
                if (!paramsList.isEmpty()) {
                    operationObj.put("parameters", paramsList);
                }

                // Responses
                Map<String, Object> responses = new LinkedHashMap<>();
                if (m.isAnnotationPresent(APIResponse.class)) {
                    APIResponse apiRes = m.getAnnotation(APIResponse.class);
                    responses.put(apiRes.responseCode(), Map.of("description", apiRes.description()));
                } else {
                    responses.put("200", Map.of(
                            "description", "Operación ejecutada con éxito",
                            "content", Map.of(ep.produces, Map.of("schema", Map.of("type", "object")))
                    ));
                }
                operationObj.put("responses", responses);

                // Requerimientos de Seguridad específicos para la operación
                if (m.isAnnotationPresent(jakarta.annotation.security.PermitAll.class) || ep.isPermitAll) {
                    operationObj.put("security", Collections.emptyList());
                } else if (m.isAnnotationPresent(SecurityRequirement.class)) {
                    SecurityRequirement sr = m.getAnnotation(SecurityRequirement.class);
                    List<Map<String, List<String>>> opSec = new ArrayList<>();
                    Map<String, List<String>> secMap = new LinkedHashMap<>();
                    secMap.put(sr.name(), Arrays.asList(sr.scopes()));
                    opSec.add(secMap);
                    operationObj.put("security", opSec);
                } else if (m.isAnnotationPresent(SecurityRequirements.class)) {
                    SecurityRequirements srs = m.getAnnotation(SecurityRequirements.class);
                    List<Map<String, List<String>>> opSec = new ArrayList<>();
                    for (SecurityRequirement sr : srs.value()) {
                        Map<String, List<String>> secMap = new LinkedHashMap<>();
                        secMap.put(sr.name(), Arrays.asList(sr.scopes()));
                        opSec.add(secMap);
                    }
                    operationObj.put("security", opSec);
                } else if (m.isAnnotationPresent(jakarta.annotation.security.RolesAllowed.class) || !ep.rolesAllowed.isEmpty()) {
                    List<Map<String, List<String>>> opSec = new ArrayList<>();
                    Map<String, List<String>> secMap = new LinkedHashMap<>();
                    String primaryScheme = securitySchemes.keySet().iterator().next();
                    secMap.put(primaryScheme, new ArrayList<>(ep.rolesAllowed));
                    opSec.add(secMap);
                    operationObj.put("security", opSec);
                } else if (ep.resourceClass.isAnnotationPresent(SecurityRequirement.class)) {
                    SecurityRequirement sr = ep.resourceClass.getAnnotation(SecurityRequirement.class);
                    List<Map<String, List<String>>> opSec = new ArrayList<>();
                    Map<String, List<String>> secMap = new LinkedHashMap<>();
                    secMap.put(sr.name(), Arrays.asList(sr.scopes()));
                    opSec.add(secMap);
                    operationObj.put("security", opSec);
                } else if (ep.resourceClass.isAnnotationPresent(SecurityRequirements.class)) {
                    SecurityRequirements srs = ep.resourceClass.getAnnotation(SecurityRequirements.class);
                    List<Map<String, List<String>>> opSec = new ArrayList<>();
                    for (SecurityRequirement sr : srs.value()) {
                        Map<String, List<String>> secMap = new LinkedHashMap<>();
                        secMap.put(sr.name(), Arrays.asList(sr.scopes()));
                        opSec.add(secMap);
                    }
                    operationObj.put("security", opSec);
                } else if (ep.resourceClass.isAnnotationPresent(jakarta.annotation.security.PermitAll.class)) {
                    operationObj.put("security", Collections.emptyList());
                }

                pathItem.put(ep.httpMethod.toLowerCase(), operationObj);
            }
        }

        root.put("paths", paths);

        byte[] bytes = json.toJson(root).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void addSecurityScheme(Map<String, Object> securitySchemes, SecurityScheme ss) {
        if (ss == null) return;
        String name = !ss.securitySchemeName().isEmpty() ? ss.securitySchemeName() : "BearerAuth";
        Map<String, Object> schemeMap = new LinkedHashMap<>();
        String typeStr = ss.type() != null ? ss.type().name().toLowerCase() : "http";

        if ("http".equalsIgnoreCase(typeStr)) {
            schemeMap.put("type", "http");
            String scheme = !ss.scheme().isEmpty() ? ss.scheme().toLowerCase() : "bearer";
            schemeMap.put("scheme", scheme);
            if (!ss.bearerFormat().isEmpty()) {
                schemeMap.put("bearerFormat", ss.bearerFormat());
            } else if ("bearer".equalsIgnoreCase(scheme)) {
                schemeMap.put("bearerFormat", "JWT");
            }
        } else if ("apikey".equalsIgnoreCase(typeStr)) {
            schemeMap.put("type", "apiKey");
            schemeMap.put("name", !ss.apiKeyName().isEmpty() ? ss.apiKeyName() : "Authorization");
            schemeMap.put("in", ss.in() != null ? ss.in().name().toLowerCase() : "header");
        } else if ("oauth2".equalsIgnoreCase(typeStr)) {
            schemeMap.put("type", "oauth2");
        } else if ("openidconnect".equalsIgnoreCase(typeStr)) {
            schemeMap.put("type", "openIdConnect");
            if (!ss.openIdConnectUrl().isEmpty()) {
                schemeMap.put("openIdConnectUrl", ss.openIdConnectUrl());
            }
        } else {
            schemeMap.put("type", typeStr);
        }

        if (!ss.description().isEmpty()) {
            schemeMap.put("description", ss.description());
        } else if ("http".equalsIgnoreCase(typeStr) && "bearer".equalsIgnoreCase((String) schemeMap.get("scheme"))) {
            schemeMap.put("description", "Autenticación basada en token JWT Bearer. Ingrese su token para autorizar las solicitudes.");
        }

        securitySchemes.put(name, schemeMap);
    }

    private void sendSwaggerUI(HttpExchange exchange) throws IOException {
        String openApiUrl = "/q/openapi";
        String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <title>JettraEE - Swagger UI</title>
                    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css" />
                    <style>
                        html { box-sizing: border-box; overflow: -moz-scrollbars-vertical; overflow-y: scroll; }
                        *, *:before, *:after { box-sizing: inherit; }
                        body { margin:0; background: #fafafa; font-family: sans-serif; }
                        .topbar { background-color: #008080 !important; }
                        .topbar-wrapper img { content: url('https://microprofile.io/wp-content/uploads/2021/08/MicroProfile-Logo.png'); height: 40px; }
                        .swagger-ui .btn.authorize {
                            border-color: #008080 !important;
                            color: #008080 !important;
                        }
                        .swagger-ui .btn.authorize svg {
                            fill: #008080 !important;
                        }
                    </style>
                </head>
                <body>
                    <div id="swagger-ui"></div>
                    <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js"></script>
                    <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js"></script>
                    <script>
                    window.onload = function() {
                      const ui = SwaggerUIBundle({
                        url: "%s",
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        persistAuthorization: true,
                        displayRequestDuration: true,
                        presets: [
                          SwaggerUIBundle.presets.apis,
                          SwaggerUIStandalonePreset
                        ],
                        plugins: [
                          SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout"
                      });
                      window.ui = ui;
                    };
                    </script>
                </body>
                </html>
                """.formatted(openApiUrl);

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
