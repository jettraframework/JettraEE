package io.jettra.ee.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.jettra.ee.core.IO;
import io.jettra.ee.integration.FluxIntegration;
import io.jettra.ee.jakarta.cdi.JettraCDIContainer;
import io.jettra.ee.jakarta.rest.RestDispatcher;
import io.jettra.ee.microprofile.health.HealthEndpointHandler;
import io.jettra.ee.microprofile.metrics.MetricsEndpointHandler;
import io.jettra.ee.microprofile.openapi.OpenApiEndpointHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP central de JettraEE, optimizado para Java 21/25 con Hilos Virtuales.
 * Despacha de forma unificada recursos Jakarta REST, extensiones MicroProfile y páginas JettraFlux.
 */
public class JettraEEServer {

    private final int port;
    private final String contextPath;
    private final RestDispatcher restDispatcher;
    private final HealthEndpointHandler healthHandler;
    private final MetricsEndpointHandler metricsHandler;
    private final OpenApiEndpointHandler openApiHandler;
    private final FluxIntegration fluxIntegration;

    private final WebResourceManager webResourceManager;

    private HttpServer httpServer;
    private boolean isRunning = false;
    private final Map<String, HttpHandler> customHandlers = new ConcurrentHashMap<>();

    public JettraEEServer(int port, String contextPath,
                          RestDispatcher restDispatcher,
                          HealthEndpointHandler healthHandler,
                          MetricsEndpointHandler metricsHandler,
                          OpenApiEndpointHandler openApiHandler,
                          FluxIntegration fluxIntegration) {
        this(port, contextPath, restDispatcher, healthHandler, metricsHandler, openApiHandler, fluxIntegration, new WebResourceManager());
    }

    public JettraEEServer(int port, String contextPath,
                          RestDispatcher restDispatcher,
                          HealthEndpointHandler healthHandler,
                          MetricsEndpointHandler metricsHandler,
                          OpenApiEndpointHandler openApiHandler,
                          FluxIntegration fluxIntegration,
                          WebResourceManager webResourceManager) {
        this.port = port > 0 ? port : 8080;
        this.contextPath = sanitizeContextPath(contextPath);
        io.jettra.server.JettraServer.setContextPath(this.contextPath);
        this.restDispatcher = restDispatcher != null ? restDispatcher : new RestDispatcher(this.contextPath);
        this.healthHandler = healthHandler != null ? healthHandler : new HealthEndpointHandler();
        this.metricsHandler = metricsHandler != null ? metricsHandler : new MetricsEndpointHandler();
        this.openApiHandler = openApiHandler != null ? openApiHandler : new OpenApiEndpointHandler(this.restDispatcher, "JettraEE API", "1.0.0");
        this.fluxIntegration = fluxIntegration != null ? fluxIntegration : new FluxIntegration();
        this.webResourceManager = webResourceManager != null ? webResourceManager : new WebResourceManager();
    }

    public void addHandler(String path, HttpHandler handler) {
        customHandlers.put(path, handler);
        if (httpServer != null) {
            registerEndpoint(path, handler);
        }
    }

    /**
     * Inicia el servidor JettraEE en el puerto configurado utilizando Virtual Threads.
     */
    public synchronized void start() {
        if (isRunning) {
            IO.warn("JettraEEServer ya se encuentra en ejecución.");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);

            // 1. Registrar Endpoints de MicroProfile
            registerEndpoint("/q/health", healthHandler);
            registerEndpoint("/q/health/live", healthHandler);
            registerEndpoint("/q/health/ready", healthHandler);
            registerEndpoint("/q/health/started", healthHandler);
            registerEndpoint("/health", healthHandler);
            registerEndpoint("/health/live", healthHandler);
            registerEndpoint("/health/ready", healthHandler);

            registerEndpoint("/q/metrics", metricsHandler);
            registerEndpoint("/metrics", metricsHandler);

            registerEndpoint("/q/openapi", openApiHandler);
            registerEndpoint("/openapi", openApiHandler);
            registerEndpoint("/openapi.json", openApiHandler);
            registerEndpoint("/q/swagger-ui", openApiHandler);
            registerEndpoint("/swagger-ui", openApiHandler);

            // 2. Registrar Páginas de JettraFlux
            HttpHandler rootFluxHandler = null;
            for (Map.Entry<String, Object> entry : fluxIntegration.getRegisteredFluxPages().entrySet()) {
                HttpHandler wrappedFlux = FluxIntegration.wrapFluxHandler(entry.getValue());
                String p = resolvePath(entry.getKey());
                if (p.equals(resolvePath("/"))) {
                    rootFluxHandler = wrappedFlux;
                } else {
                    registerEndpoint(entry.getKey(), wrappedFlux);
                }
            }

            // 3. Registrar Manejadores Personalizados
            String rootPath = resolvePath("/");
            for (Map.Entry<String, HttpHandler> entry : customHandlers.entrySet()) {
                String p = resolvePath(entry.getKey());
                if (p.equals(rootPath)) {
                    continue; // Se integrará en el despachador unificado de raíz
                }
                registerEndpoint(entry.getKey(), entry.getValue());
            }

            // 4. Recursos Estáticos (/static)
            registerEndpoint("/static", createStaticHandler());

            // 5. Manejador Jakarta Faces (.xhtml y /faces/*)
            io.jettra.ee.jakarta.faces.FacesViewHandler facesHandler = new io.jettra.ee.jakarta.faces.FacesViewHandler(webResourceManager);
            registerEndpoint("/faces", facesHandler);

            // 6. Despachador Jakarta REST, Faces y Recursos Web unificado
            final HttpHandler fluxRoot = rootFluxHandler;
            final HttpHandler customRoot = customHandlers.get("/");
            registerEndpoint(rootPath, wrapMetrics(exchange -> {
                String fullPath = exchange.getRequestURI().getPath();
                String p = fullPath;
                if (!contextPath.equals("/") && p.startsWith(contextPath)) {
                    p = p.substring(contextPath.length());
                }
                if (!p.startsWith("/")) p = "/" + p;

                // 1. Protección estricta de seguridad Jakarta EE Servlet 6.0: /WEB-INF y /META-INF
                if (webResourceManager.isProtectedPath(p)) {
                    byte[] b = "{\"status\":403,\"message\":\"Access to WEB-INF / META-INF is forbidden\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(403, b.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
                    return;
                }

                // 2. Custom Root o Flux Root Handler si la solicitud es a "/"
                if (p.equals("/") || p.isEmpty()) {
                    if (customRoot != null) {
                        customRoot.handle(exchange);
                        return;
                    }
                    if (fluxRoot != null) {
                        fluxRoot.handle(exchange);
                        return;
                    }
                }

                // 3. Resolución de Welcome Files (ej. "/" -> "index.xhtml", "index.html", etc.)
                if (p.equals("/") || p.endsWith("/")) {
                    String welcomeFile = webResourceManager.resolveWelcomeFile(p);
                    if (welcomeFile != null) {
                        if (welcomeFile.endsWith(".xhtml")) {
                            facesHandler.handle(exchange);
                            return;
                        } else {
                            if (webResourceManager.serveStaticResource(exchange, welcomeFile)) {
                                return;
                            }
                        }
                    }
                }

                // 4. Jakarta Faces (.xhtml, /faces/* o recursos jakarta.faces.resource)
                if (p.endsWith(".xhtml") || p.startsWith("/faces/") || p.startsWith(resolvePath("/faces/")) || p.contains("jakarta.faces.resource")) {
                    facesHandler.handle(exchange);
                    return;
                }

                // 5. Recursos estáticos web en Document Root (src/main/webapp o META-INF/resources)
                if (webResourceManager.resourceExists(p)) {
                    if (webResourceManager.serveStaticResource(exchange, p)) {
                        return;
                    }
                }

                // 6. Despachar a customRoot si existe y no hay endpoints REST registrados
                if (restDispatcher.getEndpoints().isEmpty() && customRoot != null) {
                    customRoot.handle(exchange);
                    return;
                }

                // 7. Despachar a Jakarta REST (@Path)
                restDispatcher.handle(exchange);
            }));

            // Redirección si contextPath no es "/"
            if (!contextPath.equals("/")) {
                httpServer.createContext("/", exchange -> {
                    String reqPath = exchange.getRequestURI().getPath();
                    if (!reqPath.startsWith(contextPath)) {
                        String target = contextPath + (reqPath.startsWith("/") ? reqPath : "/" + reqPath);
                        String q = exchange.getRequestURI().getQuery();
                        if (q != null && !q.isEmpty()) target += "?" + q;
                        exchange.getResponseHeaders().set("Location", target);
                        exchange.sendResponseHeaders(302, -1);
                        exchange.getResponseBody().close();
                        return;
                    }
                    restDispatcher.handle(exchange);
                });
            }

            // Configurar Virtual Thread per Task Executor (Java 21/25 Loom)
            httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            httpServer.start();
            isRunning = true;

            long elapsed = System.currentTimeMillis() - startTime;
            IO.success("Servidor JettraEE iniciado en puerto " + port + " (contextPath: '" + contextPath + "', webappRoot: '" + webResourceManager.getWebappRoot() + "') en " + elapsed + " ms.");
            IO.info("Virtual Threads activados (Project Loom). Concurrencia masiva y alta eficiencia.");

        } catch (IOException e) {
            IO.error("Error fatal al iniciar servidor JettraEE en puerto " + port, e);
            throw new RuntimeException(e);
        }
    }

    private void registerEndpoint(String path, HttpHandler handler) {
        String resolved = resolvePath(path);
        try {
            httpServer.removeContext(resolved);
        } catch (IllegalArgumentException ignored) {}
        httpServer.createContext(resolved, wrapMetrics(handler));
        if (resolved.endsWith("/") && resolved.length() > 1) {
            String trimmed = resolved.substring(0, resolved.length() - 1);
            try {
                httpServer.removeContext(trimmed);
            } catch (IllegalArgumentException ignored) {}
            httpServer.createContext(trimmed, wrapMetrics(handler));
        }
    }

    private HttpHandler wrapMetrics(HttpHandler handler) {
        return exchange -> {
            metricsHandler.incrementRequestCount();
            metricsHandler.trackRequestStart();
            try {
                handler.handle(exchange);
            } finally {
                metricsHandler.trackRequestEnd();
            }
        };
    }

    private HttpHandler createStaticHandler() {
        return exchange -> {
            String reqPath = exchange.getRequestURI().getPath();
            String prefix = resolvePath("/static");
            String rel = reqPath.substring(prefix.length());
            if (rel.startsWith("/")) rel = rel.substring(1);

            if (webResourceManager.isProtectedPath(rel)) {
                exchange.sendResponseHeaders(403, -1);
                exchange.getResponseBody().close();
                return;
            }

            if (webResourceManager.serveStaticResource(exchange, rel) ||
                webResourceManager.serveStaticResource(exchange, "static/" + rel)) {
                return;
            }

            File file = new File("src/main/resources/static", rel);
            if (!file.exists() || file.isDirectory()) {
                file = new File("static", rel);
            }

            if (file.exists() && !file.isDirectory()) {
                String contentType = Files.probeContentType(file.toPath());
                if (contentType == null) contentType = WebResourceManager.getMimeType(file.getName());
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.getResponseBody().close();
            }
        };
    }

    public synchronized void stop() {
        if (!isRunning) return;
        IO.info("Deteniendo JettraEEServer...");
        JettraCDIContainer.getInstance().invokePreDestroyAll();
        if (httpServer != null) {
            httpServer.stop(1);
        }
        isRunning = false;
        IO.info("JettraEEServer detenido.");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    public String getContextPath() {
        return contextPath;
    }

    public RestDispatcher getRestDispatcher() {
        return restDispatcher;
    }

    public HealthEndpointHandler getHealthHandler() {
        return healthHandler;
    }

    public MetricsEndpointHandler getMetricsHandler() {
        return metricsHandler;
    }

    public OpenApiEndpointHandler getOpenApiHandler() {
        return openApiHandler;
    }

    public FluxIntegration getFluxIntegration() {
        return fluxIntegration;
    }

    public WebResourceManager getWebResourceManager() {
        return webResourceManager;
    }

    private String sanitizeContextPath(String path) {
        if (path == null || path.isBlank() || path.equals("/")) return "/";
        String p = path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        return p;
    }

    private String resolvePath(String path) {
        if (contextPath.equals("/")) {
            return path.startsWith("/") ? path : "/" + path;
        }
        if (!path.startsWith("/")) path = "/" + path;
        return contextPath + path;
    }
}
