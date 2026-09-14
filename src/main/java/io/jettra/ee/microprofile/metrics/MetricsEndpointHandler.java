package io.jettra.ee.microprofile.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jettra.json.JettraJson;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Endpoint de MicroProfile Metrics 5.1 para JettraEE.
 * Expone métricas en formato Prometheus OpenMetrics (/q/metrics) y JSON.
 */
public class MetricsEndpointHandler implements HttpHandler {

    private final JettraJson json = new JettraJson();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong activeRequests = new AtomicLong(0);
    private final Map<String, AtomicLong> customCounters = new ConcurrentHashMap<>();
    private final Map<String, java.util.function.Supplier<Number>> customGauges = new ConcurrentHashMap<>();

    public void incrementRequestCount() {
        totalRequests.incrementAndGet();
    }

    public void trackRequestStart() {
        activeRequests.incrementAndGet();
    }

    public void trackRequestEnd() {
        activeRequests.decrementAndGet();
    }

    public void registerCounter(String name) {
        customCounters.computeIfAbsent(name, k -> new AtomicLong(0));
    }

    public void incrementCounter(String name) {
        customCounters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void registerGauge(String name, java.util.function.Supplier<Number> supplier) {
        customGauges.put(name, supplier);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
        boolean wantsJson = acceptHeader != null && acceptHeader.contains("application/json");

        if (wantsJson) {
            sendJsonMetrics(exchange);
        } else {
            sendPrometheusMetrics(exchange);
        }
    }

    private void sendJsonMetrics(HttpExchange exchange) throws IOException {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("memory.usedHeap", mem.getHeapMemoryUsage().getUsed());
        base.put("memory.maxHeap", mem.getHeapMemoryUsage().getMax());
        base.put("memory.committedHeap", mem.getHeapMemoryUsage().getCommitted());
        base.put("thread.count", threads.getThreadCount());
        base.put("thread.peakCount", threads.getPeakThreadCount());
        base.put("jvm.uptime", runtime.getUptime());

        Map<String, Object> vendor = new LinkedHashMap<>();
        vendor.put("http.requests.total", totalRequests.get());
        vendor.put("http.requests.active", activeRequests.get());

        Map<String, Object> application = new LinkedHashMap<>();
        customCounters.forEach((k, v) -> application.put(k, v.get()));
        customGauges.forEach((k, v) -> {
            try { application.put(k, v.get()); } catch (Exception ignored) {}
        });

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("base", base);
        root.put("vendor", vendor);
        root.put("application", application);

        byte[] bytes = json.toJson(root).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendPrometheusMetrics(HttpExchange exchange) throws IOException {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        StringBuilder sb = new StringBuilder();

        // Base JVM Metrics
        sb.append("# HELP base_memory_usedHeap_bytes Current used heap memory in bytes\n");
        sb.append("# TYPE base_memory_usedHeap_bytes gauge\n");
        sb.append("base_memory_usedHeap_bytes ").append(mem.getHeapMemoryUsage().getUsed()).append("\n");

        sb.append("# HELP base_memory_maxHeap_bytes Maximum heap memory in bytes\n");
        sb.append("# TYPE base_memory_maxHeap_bytes gauge\n");
        sb.append("base_memory_maxHeap_bytes ").append(mem.getHeapMemoryUsage().getMax()).append("\n");

        sb.append("# HELP base_thread_count Current live threads\n");
        sb.append("# TYPE base_thread_count gauge\n");
        sb.append("base_thread_count ").append(threads.getThreadCount()).append("\n");

        sb.append("# HELP base_jvm_uptime_seconds JVM uptime in seconds\n");
        sb.append("# TYPE base_jvm_uptime_seconds gauge\n");
        sb.append("base_jvm_uptime_seconds ").append(runtime.getUptime() / 1000.0).append("\n");

        // Vendor / JettraEE Metrics
        sb.append("# HELP vendor_http_requests_total Total incoming HTTP requests handled by JettraEE\n");
        sb.append("# TYPE vendor_http_requests_total counter\n");
        sb.append("vendor_http_requests_total ").append(totalRequests.get()).append("\n");

        sb.append("# HELP vendor_http_requests_active Currently active HTTP requests handled on virtual threads\n");
        sb.append("# TYPE vendor_http_requests_active gauge\n");
        sb.append("vendor_http_requests_active ").append(activeRequests.get()).append("\n");

        // Application Custom Metrics
        customCounters.forEach((k, v) -> {
            String sanitized = k.replace('.', '_').replace('-', '_');
            sb.append("# TYPE application_").append(sanitized).append(" counter\n");
            sb.append("application_").append(sanitized).append(" ").append(v.get()).append("\n");
        });

        customGauges.forEach((k, v) -> {
            try {
                String sanitized = k.replace('.', '_').replace('-', '_');
                sb.append("# TYPE application_").append(sanitized).append(" gauge\n");
                sb.append("application_").append(sanitized).append(" ").append(v.get()).append("\n");
            } catch (Exception ignored) {}
        });

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
