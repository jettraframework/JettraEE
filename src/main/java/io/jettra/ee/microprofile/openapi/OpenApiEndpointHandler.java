package io.jettra.ee.microprofile.openapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.jakarta.rest.RestDispatcher;
import io.jettra.json.JettraJson;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Endpoint de MicroProfile OpenAPI 3.1 y Swagger UI interactivo para JettraEE.
 * Expone la especificación OpenAPI en /q/openapi y la UI en /q/swagger-ui.
 */
public class OpenApiEndpointHandler implements HttpHandler {

    private final JettraJson json = new JettraJson();
    private final RestDispatcher restDispatcher;
    private final String appTitle;
    private final String appVersion;

    public OpenApiEndpointHandler(RestDispatcher restDispatcher, String appTitle, String appVersion) {
        this.restDispatcher = restDispatcher;
        this.appTitle = appTitle != null ? appTitle : "JettraEE Microservices API";
        this.appVersion = appVersion != null ? appVersion : "1.0.0";
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

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", appTitle);
        info.put("version", appVersion);
        info.put("description", "Documentación autogenerada por el servidor JettraEE (Eclipse MicroProfile OpenAPI 3.1).");
        root.put("info", info);

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
                String description = "Operación REST en " + ep.resourceClass.getSimpleName();

                if (m.isAnnotationPresent(Operation.class)) {
                    Operation op = m.getAnnotation(Operation.class);
                    if (!op.summary().isEmpty()) summary = op.summary();
                    if (!op.description().isEmpty()) description = op.description();
                }

                operationObj.put("summary", summary);
                operationObj.put("description", description);

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
