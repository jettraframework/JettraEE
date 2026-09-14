package io.jettra.ee.microprofile.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.core.IO;
import io.jettra.ee.jakarta.cdi.JettraCDIContainer;
import io.jettra.json.JettraJson;
import org.eclipse.microprofile.health.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Endpoint de MicroProfile Health 4.0 para JettraEE.
 * Expone /q/health, /q/health/live, /q/health/ready, /q/health/started.
 */
public class HealthEndpointHandler implements HttpHandler {

    private final JettraJson json = new JettraJson();
    private final List<Class<? extends HealthCheck>> livenessChecks = new CopyOnWriteArrayList<>();
    private final List<Class<? extends HealthCheck>> readinessChecks = new CopyOnWriteArrayList<>();
    private final List<Class<? extends HealthCheck>> startupChecks = new CopyOnWriteArrayList<>();

    public void registerCheck(Class<? extends HealthCheck> checkClass) {
        boolean registered = false;
        if (checkClass.isAnnotationPresent(Liveness.class)) {
            livenessChecks.add(checkClass);
            registered = true;
        }
        if (checkClass.isAnnotationPresent(Readiness.class)) {
            readinessChecks.add(checkClass);
            registered = true;
        }
        if (checkClass.isAnnotationPresent(Startup.class)) {
            startupChecks.add(checkClass);
            registered = true;
        }
        if (!registered) {
            // Por defecto, si implementa HealthCheck y no tiene anotación específica, se toma como Liveness
            livenessChecks.add(checkClass);
        }
        JettraCDIContainer.getInstance().registerBean(checkClass);
        IO.info("MicroProfile HealthCheck registrado: " + checkClass.getSimpleName());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        List<Class<? extends HealthCheck>> checksToRun = new ArrayList<>();

        if (path.endsWith("/live")) {
            checksToRun.addAll(livenessChecks);
        } else if (path.endsWith("/ready")) {
            checksToRun.addAll(readinessChecks);
        } else if (path.endsWith("/started")) {
            checksToRun.addAll(startupChecks);
        } else {
            // /q/health o /health ejecuta todos
            Set<Class<? extends HealthCheck>> all = new LinkedHashSet<>();
            all.addAll(livenessChecks);
            all.addAll(readinessChecks);
            all.addAll(startupChecks);
            checksToRun.addAll(all);
        }

        boolean overallUp = true;
        List<Map<String, Object>> checksData = new ArrayList<>();

        // Si no hay chequeos registrados, por defecto el servidor está UP
        if (checksToRun.isEmpty()) {
            Map<String, Object> defaultCheck = new LinkedHashMap<>();
            defaultCheck.put("name", "server-status");
            defaultCheck.put("status", "UP");
            defaultCheck.put("data", Map.of("engine", "JettraEE", "mode", "virtual-threads"));
            checksData.add(defaultCheck);
        } else {
            for (Class<? extends HealthCheck> checkClass : checksToRun) {
                try {
                    HealthCheck check = JettraCDIContainer.getInstance().getBean(checkClass);
                    HealthCheckResponse response = check.call();
                    boolean isUp = response.getStatus() == HealthCheckResponse.Status.UP;
                    if (!isUp) {
                        overallUp = false;
                    }

                    Map<String, Object> cMap = new LinkedHashMap<>();
                    cMap.put("name", response.getName());
                    cMap.put("status", response.getStatus().name());
                    response.getData().ifPresent(d -> cMap.put("data", d));
                    checksData.add(cMap);
                } catch (Throwable t) {
                    overallUp = false;
                    Map<String, Object> errCheck = new LinkedHashMap<>();
                    errCheck.put("name", checkClass.getSimpleName());
                    errCheck.put("status", "DOWN");
                    errCheck.put("data", Map.of("error", String.valueOf(t.getMessage())));
                    checksData.add(errCheck);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", overallUp ? "UP" : "DOWN");
        result.put("checks", checksData);

        int statusCode = overallUp ? 200 : 503;
        byte[] bytes = json.toJson(result).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
