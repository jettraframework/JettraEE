package io.jettra.ee.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.core.IO;
import io.jettra.flux.pages.FluxBaseHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integración nativa de JettraFlux en JettraEE.
 * Permite hospedar y despachar páginas de interfaz reactiva (@Page, FluxBaseHandler),
 * widgets y recursos estáticos de temas y estilos.
 */
public class FluxIntegration {

    private final Map<String, Object> registeredFluxPages = new ConcurrentHashMap<>();

    /**
     * Registra una página JettraFlux asociada a una ruta URL.
     */
    public void registerPage(String path, Class<?> pageClass) {
        String normalized = normalizePath(path);
        registeredFluxPages.put(normalized, pageClass);
        IO.info("Página JettraFlux registrada: " + normalized + " -> " + pageClass.getSimpleName());
    }

    /**
     * Registra una instancia directa de manejador JettraFlux.
     */
    public void registerHandler(String path, HttpHandler handler) {
        String normalized = normalizePath(path);
        registeredFluxPages.put(normalized, handler);
        IO.info("Manejador JettraFlux registrado: " + normalized + " -> " + handler.getClass().getSimpleName());
    }

    public Map<String, Object> getRegisteredFluxPages() {
        return registeredFluxPages;
    }

    /**
     * Envuelve un handler Flux para inyectar manejo de errores y ciclo de vida de sesión.
     */
    public static HttpHandler wrapFluxHandler(Object handlerObj) {
        return exchange -> {
            boolean contextCreated = false;
            if (io.jettra.server.core.JettraContext.getCurrent() == null) {
                io.jettra.server.core.JettraContext.setCurrent(new io.jettra.server.core.JettraContext("flux-session"));
                contextCreated = true;
            }
            try {
                HttpHandler actualHandler;
                if (handlerObj instanceof HttpHandler h) {
                    actualHandler = h;
                    try {
                        io.jettra.ee.jakarta.cdi.JettraCDIContainer.getInstance().inject(actualHandler);
                    } catch (Exception ignored) {}
                } else if (handlerObj instanceof Class<?> c) {
                    var constructor = c.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    actualHandler = (HttpHandler) constructor.newInstance();
                    try {
                        io.jettra.ee.jakarta.cdi.JettraCDIContainer.getInstance().inject(actualHandler);
                    } catch (Exception ignored) {}
                } else {
                    throw new IllegalArgumentException("Objeto de página no soportado: " + handlerObj);
                }
                actualHandler.handle(exchange);
            } catch (Throwable t) {
                IO.error("Error procesando página JettraFlux", t);
                sendInternalError(exchange, t.getMessage());
            } finally {
                if (contextCreated) {
                    io.jettra.server.core.JettraContext.clear();
                }
            }
        };
    }

    private static void sendInternalError(HttpExchange exchange, String message) throws IOException {
        String html = "<html><body style='font-family:sans-serif;padding:40px;'>"
                + "<h1 style='color:#c00;'>Error en JettraFlux</h1>"
                + "<p>Se produjo un error al procesar la página:</p>"
                + "<pre style='background:#f4f4f4;padding:15px;border-radius:4px;'>" + message + "</pre>"
                + "</body></html>";
        byte[] bytes = html.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
        exchange.sendResponseHeaders(500, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        String p = path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        return p;
    }
}
