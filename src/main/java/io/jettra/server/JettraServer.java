package io.jettra.server;

import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.JettraEE;
import io.jettra.ee.server.JettraEEServer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor y adaptador de compatibilidad para JettraStack en JettraEE.
 * Permite resolver rutas en páginas y widgets de JettraFlux, e inicia instancias
 * de servidor HTTP optimizadas para Java 25 con Hilos Virtuales (Project Loom).
 */
public class JettraServer {

    private static String contextPath = "/";

    private int customPort = 8080;
    private JettraEEServer eeServer;
    private final Map<String, HttpHandler> handlerRegistry = new ConcurrentHashMap<>();

    public JettraServer() {
    }

    public JettraServer(int port) {
        this.customPort = port;
    }

    public static void setContextPath(String cp) {
        contextPath = (cp == null || cp.isBlank() || cp.equals("/")) ? "/" : (cp.startsWith("/") ? cp : "/" + cp);
        io.jettra.flux.core.FluxConfig.setContextPath(contextPath);
    }

    public static String getContextPath() {
        return contextPath;
    }

    public static String resolvePath(String path) {
        return io.jettra.flux.core.FluxConfig.resolvePath(path);
    }

    public void setPort(int port) {
        this.customPort = port;
    }

    public int getPort() {
        return eeServer != null ? eeServer.getPort() : customPort;
    }

    public void addHandler(String path, HttpHandler handler) {
        handlerRegistry.put(path, handler);
        if (eeServer != null) {
            eeServer.addHandler(path, handler);
        }
    }

    public synchronized void start() {
        if (eeServer == null) {
            eeServer = JettraEE.builder()
                    .port(customPort)
                    .contextPath(contextPath)
                    .build();
            for (Map.Entry<String, HttpHandler> entry : handlerRegistry.entrySet()) {
                eeServer.addHandler(entry.getKey(), entry.getValue());
            }
        }
        eeServer.start();
    }

    public synchronized void stop() {
        if (eeServer != null) {
            eeServer.stop();
        }
    }

    public JettraEEServer getEEServer() {
        return eeServer;
    }
}
