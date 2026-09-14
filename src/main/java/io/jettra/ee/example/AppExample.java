package io.jettra.ee.example;

import io.jettra.core.server.Page;
import io.jettra.ee.JettraEE;
import io.jettra.flux.pages.FluxBaseHandler;
import io.jettra.rules.annotations.Rules;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

/**
 * Ejemplo completo de aplicación construida sobre JettraEE.
 * Demuestra:
 * 1. Jakarta REST (JAX-RS 3.1)
 * 2. Jakarta CDI 4.1 (@Inject, @ApplicationScoped)
 * 3. Eclipse MicroProfile Config (@ConfigProperty)
 * 4. Eclipse MicroProfile Health (@Liveness, @Readiness)
 * 5. Eclipse MicroProfile OpenAPI (@Operation, @Tag, Swagger UI)
 * 6. Eclipse MicroProfile Fault Tolerance (@Retry, @Fallback)
 * 7. Jakarta Validation + JettraRules (@Rules, @NotBlank, @Email)
 * 8. JettraFlux (@Page, UI Reactiva)
 */
public class AppExample {

    // --- 1. DTO con Jakarta Validation y JettraRules ---
    public static class ClienteDTO {
        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        private String nombre;

        @NotBlank(message = "El email es requerido")
        @Email(message = "Formato de email inválido")
        private String email;

        public ClienteDTO() {}

        public ClienteDTO(String nombre, String email) {
            this.nombre = nombre;
            this.email = email;
        }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    // --- 2. Servicio de Negocio con CDI y ConfigProperty ---
    @ApplicationScoped
    public static class ClienteService {

        @Inject
        @ConfigProperty(name = "app.descuento.default", defaultValue = "10")
        private int descuentoPorDefecto;

        @Retry(maxRetries = 2)
        @Fallback(fallbackMethod = "obtenerClientesRespaldo")
        public List<ClienteDTO> listarClientes() {
            return List.of(
                    new ClienteDTO("Empresa Alfa", "contacto@alfa.com"),
                    new ClienteDTO("Beta Logistics", "info@beta.com")
            );
        }

        public List<ClienteDTO> obtenerClientesRespaldo() {
            return List.of(new ClienteDTO("Modo Respaldo / Cache", "cache@local.net"));
        }

        public int getDescuentoPorDefecto() {
            return descuentoPorDefecto;
        }
    }

    // --- 3. Recurso Jakarta REST con MicroProfile OpenAPI ---
    @Path("/clientes")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Tag(name = "Clientes", description = "Gestión de clientes y servicios corporativos")
    public static class ClienteResource {

        @Inject
        private ClienteService clienteService;

        @GET
        @Operation(summary = "Listar clientes", description = "Retorna todos los clientes registrados")
        @APIResponse(responseCode = "200", description = "Lista de clientes obtenida correctamente")
        public Response getClientes() {
            List<ClienteDTO> list = clienteService.listarClientes();
            return Response.ok(list).build();
        }

        @POST
        @Operation(summary = "Registrar nuevo cliente", description = "Crea un cliente con validación Jakarta y JettraRules")
        @APIResponse(responseCode = "201", description = "Cliente creado exitosamente")
        @APIResponse(responseCode = "400", description = "Datos de cliente inválidos")
        public Response crearCliente(@Valid ClienteDTO cliente) {
            return Response.status(201).entity(Map.of(
                    "mensaje", "Cliente registrado con éxito",
                    "descuentoAplicado", clienteService.getDescuentoPorDefecto() + "%",
                    "cliente", cliente
            )).build();
        }
    }

    // --- 4. MicroProfile Health (Liveness & Readiness) ---
    @Liveness
    @ApplicationScoped
    public static class AppLivenessCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("ServicioActivo")
                    .up()
                    .withData("hilosVirtuales", "OK")
                    .build();
        }
    }

    @Readiness
    @ApplicationScoped
    public static class AppReadinessCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("BaseDeDatos")
                    .up()
                    .withData("conexion", "OK")
                    .build();
        }
    }

    // --- 5. Página de Interfaz Reactiva JettraFlux ---
    @Page(path = "/inicio")
    public static class InicioPage extends FluxBaseHandler {
        @Override
        protected String getTitle() {
            return "Bienvenido a JettraEE";
        }

        @Override
        protected io.jettra.flux.core.Widget buildUI(com.sun.net.httpserver.HttpExchange exchange, Map<String, String> params, String currentTheme) {
            return io.jettra.flux.widgets.Center.of(
                    io.jettra.flux.widgets.Column.of(
                            io.jettra.flux.widgets.Header.of(1, "JettraEE - Servidor Reactivo"),
                            io.jettra.flux.widgets.Paragraph.of("Microservicios con Jakarta EE 11/12, Eclipse MicroProfile y JettraFlux.")
                    )
            );
        }
    }

    // --- 6. Método Principal de Arranque ---
    public static void main(String[] args) {
        JettraEE.start(AppExample.class, args);
    }
}
