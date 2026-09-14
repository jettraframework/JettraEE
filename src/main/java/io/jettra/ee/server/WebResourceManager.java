package io.jettra.ee.server;

import com.sun.net.httpserver.HttpExchange;
import io.jettra.ee.core.IO;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Gestor de recursos web conforme con la especificación de Servlet / Jakarta EE 10/11.
 * Administra el Document Root (por defecto src/main/webapp), la resolución de welcome files,
 * la protección de WEB-INF / META-INF, la resolución de recursos estáticos y bibliotecas JSF/Faces.
 */
public class WebResourceManager {

    private final String webappRoot;
    private final List<String> welcomeFiles = new ArrayList<>();
    private final Map<String, String> contextParams = new HashMap<>();

    public WebResourceManager() {
        this(detectWebappRoot());
    }

    public WebResourceManager(String webappRoot) {
        this.webappRoot = (webappRoot != null && !webappRoot.isBlank()) ? webappRoot : detectWebappRoot();
        loadWebXml();
    }

    /**
     * Autodetecta la raíz web estándar según el entorno de ejecución.
     */
    public static String detectWebappRoot() {
        String[] candidates = new String[]{
                "src/main/webapp",
                "webapp",
                "src/main/resources/META-INF/resources",
                "src/main/resources/webapp"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists() && f.isDirectory()) {
                return c;
            }
        }
        return "src/main/webapp";
    }

    public String getWebappRoot() {
        return webappRoot;
    }

    public List<String> getWelcomeFiles() {
        return Collections.unmodifiableList(welcomeFiles);
    }

    public Map<String, String> getContextParams() {
        return Collections.unmodifiableMap(contextParams);
    }

    /**
     * Verifica si la ruta solicitada corresponde a una zona protegida por especificación
     * (Servlet 6.0 §10.5: /WEB-INF/* y /META-INF/* nunca deben ser servidos vía HTTP).
     */
    public boolean isProtectedPath(String path) {
        if (path == null) return false;
        String normalized = path.trim();
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        String upper = normalized.toUpperCase();
        return upper.startsWith("/WEB-INF") || upper.startsWith("/META-INF");
    }

    /**
     * Resuelve un welcome file (ej. index.xhtml, index.html) si la ruta solicitada es un directorio o raíz "/".
     */
    public String resolveWelcomeFile(String path) {
        String dirPath = path;
        if (!dirPath.endsWith("/")) {
            dirPath += "/";
        }

        List<String> checks = new ArrayList<>(welcomeFiles);
        if (checks.isEmpty()) {
            checks.add("index.xhtml");
            checks.add("index.html");
            checks.add("index.htm");
            checks.add("default.xhtml");
            checks.add("default.html");
        }

        for (String wf : checks) {
            String candidate = dirPath + wf;
            if (resourceExists(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Verifica si existe un recurso web físico o en classpath.
     */
    public boolean resourceExists(String path) {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        // 1. En Document Root
        File file = new File(webappRoot, rel);
        if (file.exists() && !file.isDirectory()) {
            return true;
        }

        // 2. En posibles rutas del sistema de archivos
        String[] fallbackDirs = new String[]{
                "src/main/webapp",
                "src/main/resources/META-INF/resources",
                "webapp",
                "src/main/resources/webapp"
        };
        for (String dir : fallbackDirs) {
            File f = new File(dir, rel);
            if (f.exists() && !f.isDirectory()) {
                return true;
            }
        }

        // 3. En ClassLoader (META-INF/resources estándar de Servlet 3.0+)
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = getClass().getClassLoader();
        if (cl.getResource("META-INF/resources/" + rel) != null) return true;
        if (cl.getResource(rel) != null) return true;
        if (cl.getResource("webapp/" + rel) != null) return true;

        return false;
    }

    /**
     * Obtiene el contenido de un recurso como InputStream.
     */
    public InputStream getResourceAsStream(String path) {
        String rel = path.startsWith("/") ? path.substring(1) : path;

        // 1. En Document Root
        File file = new File(webappRoot, rel);
        if (file.exists() && !file.isDirectory()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException ignored) {}
        }

        // 2. En posibles rutas del sistema de archivos
        String[] fallbackDirs = new String[]{
                "src/main/webapp",
                "src/main/resources/META-INF/resources",
                "webapp",
                "src/main/resources/webapp"
        };
        for (String dir : fallbackDirs) {
            File f = new File(dir, rel);
            if (f.exists() && !f.isDirectory()) {
                try {
                    return new FileInputStream(f);
                } catch (FileNotFoundException ignored) {}
            }
        }

        // 3. En ClassLoader
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = getClass().getClassLoader();

        InputStream is = cl.getResourceAsStream("META-INF/resources/" + rel);
        if (is != null) return is;

        is = cl.getResourceAsStream(rel);
        if (is != null) return is;

        return cl.getResourceAsStream("webapp/" + rel);
    }

    /**
     * Lee y sirve un recurso estático (CSS, JS, imágenes, HTML, etc.) con Content-Type adecuado.
     * Retorna true si el recurso fue encontrado y servido, false de lo contrario.
     */
    public boolean serveStaticResource(HttpExchange exchange, String path) throws IOException {
        if (isProtectedPath(path)) {
            sendForbidden(exchange);
            return true;
        }

        // Manejar recursos JSF / Faces library: /jakarta.faces.resource/{name}?ln={library}
        if (path.contains("jakarta.faces.resource")) {
            return serveFacesResource(exchange, path);
        }

        if (!resourceExists(path)) {
            return false;
        }

        byte[] data;
        try (InputStream is = getResourceAsStream(path)) {
            if (is == null) return false;
            data = is.readAllBytes();
        }

        String mimeType = getMimeType(path);
        exchange.getResponseHeaders().set("Content-Type", mimeType);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
        return true;
    }

    /**
     * Sirve recursos según la especificación de JSF/Faces Resource Handler
     * (/jakarta.faces.resource/{name}?ln={lib} o /resources/{lib}/{name}).
     */
    private boolean serveFacesResource(HttpExchange exchange, String path) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String library = null;
        if (query != null && !query.isBlank()) {
            for (String param : query.split("&")) {
                if (param.startsWith("ln=")) {
                    library = URLDecoder.decode(param.substring(3), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        String resourceName = path.substring(path.indexOf("jakarta.faces.resource") + "jakarta.faces.resource".length());
        if (resourceName.startsWith("/")) resourceName = resourceName.substring(1);

        String resolvedPath;
        if (library != null && !library.isBlank()) {
            resolvedPath = "resources/" + library + "/" + resourceName;
        } else {
            resolvedPath = "resources/" + resourceName;
        }

        if (resourceExists(resolvedPath)) {
            return serveDirect(exchange, resolvedPath);
        }

        // Intentar en META-INF/resources/jakarta.faces.resource/
        String metaPath = "META-INF/resources/jakarta.faces.resource/" + resourceName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(metaPath)) {
            if (is != null) {
                byte[] data = is.readAllBytes();
                String mime = getMimeType(resourceName);
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
                return true;
            }
        }

        return false;
    }

    private boolean serveDirect(HttpExchange exchange, String path) throws IOException {
        try (InputStream is = getResourceAsStream(path)) {
            if (is == null) return false;
            byte[] data = is.readAllBytes();
            String mime = getMimeType(path);
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
            return true;
        }
    }

    public static String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html;charset=UTF-8";
        if (lower.endsWith(".xhtml")) return "application/xhtml+xml;charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css;charset=UTF-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript;charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json;charset=UTF-8";
        if (lower.endsWith(".xml")) return "application/xml;charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain;charset=UTF-8";

        try {
            Path p = Path.of(fileName);
            String probe = Files.probeContentType(p);
            if (probe != null) return probe;
        } catch (Exception ignored) {}

        return "application/octet-stream";
    }

    private void sendForbidden(HttpExchange exchange) throws IOException {
        byte[] b = "{\"status\":403,\"message\":\"Access to WEB-INF / META-INF is forbidden\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(403, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    /**
     * Parsea src/main/webapp/WEB-INF/web.xml si existe para cargar welcome-files y context-params.
     */
    private void loadWebXml() {
        File webXml = new File(webappRoot, "WEB-INF/web.xml");
        if (!webXml.exists()) {
            webXml = new File("src/main/webapp/WEB-INF/web.xml");
        }

        if (webXml.exists()) {
            try {
                String xml = Files.readString(webXml.toPath(), StandardCharsets.UTF_8);
                parseWebXml(xml);
            } catch (Exception e) {
                IO.warn("No se pudo parsear WEB-INF/web.xml: " + e.getMessage());
            }
        }
    }

    private void parseWebXml(String xml) {
        // Welcome files: <welcome-file>index.xhtml</welcome-file>
        java.util.regex.Pattern wfPattern = java.util.regex.Pattern.compile("<welcome-file>\\s*([^<]+?)\\s*</welcome-file>");
        java.util.regex.Matcher wfMatcher = wfPattern.matcher(xml);
        while (wfMatcher.find()) {
            String wf = wfMatcher.group(1).trim();
            if (!wf.isEmpty() && !welcomeFiles.contains(wf)) {
                welcomeFiles.add(wf);
            }
        }

        // Context params: <context-param><param-name>...</param-name><param-value>...</param-value></context-param>
        java.util.regex.Pattern cpPattern = java.util.regex.Pattern.compile(
                "<context-param>\\s*<param-name>\\s*([^<]+?)\\s*</param-name>\\s*<param-value>\\s*([^<]+?)\\s*</param-value>\\s*</context-param>",
                java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher cpMatcher = cpPattern.matcher(xml);
        while (cpMatcher.find()) {
            contextParams.put(cpMatcher.group(1).trim(), cpMatcher.group(2).trim());
        }

        if (!welcomeFiles.isEmpty()) {
            IO.info("web.xml cargado: Welcome files registrados: " + welcomeFiles);
        }
    }
}
