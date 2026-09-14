package io.jettra.ee.test;

import io.jettra.ee.JettraEE;
import io.jettra.ee.core.IO;
import io.jettra.ee.jakarta.cdi.JettraCDIContainer;
import io.jettra.ee.microprofile.faulttolerance.FaultToleranceExecutor;
import io.jettra.ee.server.JettraEEServer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class JettraEEServerTest {

    private static JettraEEServer server;
    private static final int TEST_PORT = 18888;
    private static HttpClient client;

    // --- Clases de Prueba ---

    @ApplicationScoped
    public static class GreetingService {
        @Inject
        @ConfigProperty(name = "app.greeting.prefix", defaultValue = "Bienvenido")
        private String prefix;

        public String greet(String name) {
            return prefix + ", " + (name != null ? name : "Desconocido") + "!";
        }
    }

    public static class UserDto {
        @NotNull(message = "El nombre es obligatorio")
        @NotBlank(message = "El nombre no puede estar en blanco")
        private String name;

        private int age;

        public UserDto() {}
        public UserDto(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public static class UserResource {

        @Inject
        private GreetingService greetingService;

        @GET
        @Path("/hello")
        public Response sayHello(@QueryParam("name") String name) {
            String msg = greetingService != null ? greetingService.greet(name) : "Hola " + name;
            return Response.ok("{\"message\":\"" + msg + "\"}").build();
        }

        @POST
        public Response createUser(@Valid UserDto dto) {
            return Response.status(201).entity("{\"status\":\"created\",\"name\":\"" + dto.getName() + "\"}").build();
        }
    }

    @Liveness
    @ApplicationScoped
    public static class SystemHealthCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("SystemTestProbe")
                    .up()
                    .withData("memory", "ok")
                    .build();
        }
    }

    public static class ResilientService {
        private int attempts = 0;

        @Retry(maxRetries = 2)
        @Fallback(fallbackMethod = "fallbackGreet")
        public String unstableOperation() {
            attempts++;
            if (attempts < 5) {
                throw new RuntimeException("Simulated failure in attempt " + attempts);
            }
            return "Success";
        }

        public String fallbackGreet() {
            return "Fallback activated";
        }
    }

    @BeforeAll
    public static void setUp() {
        client = HttpClient.newHttpClient();

        server = JettraEE.builder()
                .port(TEST_PORT)
                .contextPath("/")
                .registerResource(UserResource.class)
                .registerHealthCheck(SystemHealthCheck.class)
                .build();

        server.start();
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testServerIsRunning() {
        assertTrue(server.isRunning(), "El servidor JettraEE debería estar en ejecución");
    }

    @Test
    public void testJakartaRestGetEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/users/hello?name=Carlos"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Bienvenido, Carlos!"), "La respuesta debe contener el saludo inyectado por CDI");
    }

    @Test
    public void testJakartaRestPostWithValidationSuccess() throws Exception {
        String jsonPayload = "{\"name\":\"Maria\",\"age\":28}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("created"));
        assertTrue(response.body().contains("Maria"));
    }

    @Test
    public void testJakartaRestPostValidationFailure() throws Exception {
        // Objeto inválido sin nombre (@NotNull, @NotBlank)
        String invalidPayload = "{\"name\":\"\",\"age\":18}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode(), "Debe retornar 400 Bad Request por violación de restricciones");
        assertTrue(response.body().contains("Validation Failed"));
    }

    @Test
    public void testMicroProfileHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/q/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), "El estado general debe ser UP");
        assertTrue(response.body().contains("SystemTestProbe"), "Debe listar la sonda registrada");
    }

    @Test
    public void testMicroProfileMetricsEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/q/metrics"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("base_memory_usedHeap_bytes"), "Debe contener métricas Prometheus");
        assertTrue(response.body().contains("vendor_http_requests_total"), "Debe contener métricas de peticiones de JettraEE");
    }

    @Test
    public void testMicroProfileOpenApiEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/q/openapi"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"openapi\":\"3.1.0\""), "Debe responder con especificación OpenAPI 3.1");
        assertTrue(response.body().contains("/users"), "Debe listar el path /users");
    }

    @Test
    public void testMicroProfileFaultToleranceFallback() throws Throwable {
        ResilientService service = new ResilientService();
        var method = ResilientService.class.getMethod("unstableOperation");
        Object result = FaultToleranceExecutor.execute(service, method, new Object[]{});

        assertEquals("Fallback activated", result, "Debe activar el método fallback ante fallo reiterado");
    }
}
