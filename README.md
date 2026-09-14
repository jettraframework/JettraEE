# JettraEE 🚀

> **Servidor de aplicaciones super ligero, escalable y eficiente para Java 25 / 21+**  
> Compatible con las especificaciones **Eclipse MicroProfile** y **Jakarta EE 11/12**, con soporte nativo para **JettraFlux** y **JettraRules**.

```
     _      _   _             _____ _____ 
    | |    | | | |           |  ___|  ___|
    | | ___| |_| |_ _ __ __ _| |__ | |__  
 _  | |/ _ \ __| __| '__/ _` |  __||  __| 
| |_| |  __/ |_| |_| | | (_| | |___| |___ 
 \___/ \___|\__|\__|_|  \__,_\____/\____/ 
```

---

## ⚡ Características Principales

- **Arranque Ultrarrápido**: Inicia en **menos de 15 milisegundos** (vs. segundos o minutos en servidores convencionales).
- **Mínimo Consumo de Recursos**: Huella de memoria mínima (~20-40 MB RAM), ideal para contenedores, Serverless y Kubernetes.
- **Hilos Virtuales Nativos (Project Loom)**: Soporta millones de conexiones concurrentes sin agotar grupos de hilos.
- **Jakarta EE 11 / 12**:
  - **Jakarta REST (JAX-RS 3.1 / 4.0)**: Enrutamiento ágil con `@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PathParam`, etc.
  - **Jakarta CDI 4.1**: Inyección de dependencias ultraligera con `@Inject`, `@ApplicationScoped`, `@RequestScoped`.
  - **Jakarta Validation**: `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Email`, `@Valid`.
  - **Jakarta Security**: Control de accesos con `@RolesAllowed`, `@PermitAll`, `@DenyAll` y JWT.
- **Eclipse MicroProfile 6.x / 7.x**:
  - **MicroProfile Config 3.1**: Inyección con `@Inject @ConfigProperty`.
  - **MicroProfile Health 4.0**: Sondas de estado `/q/health`, `/q/health/live`, `/q/health/ready` (`@Liveness`, `@Readiness`).
  - **MicroProfile Metrics 5.1**: Métricas en tiempo real en formato **Prometheus** y JSON en `/q/metrics`.
  - **MicroProfile OpenAPI 3.1 & Swagger UI**: Documentación autogenerada en `/q/openapi` e interfaz interactiva en `/q/swagger-ui`.
  - **MicroProfile Fault Tolerance 4.0**: Resiliencia con `@Timeout`, `@Retry` y `@Fallback`.
  - **MicroProfile Rest Client 3.0**: Clientes HTTP declarativos con `@RegisterRestClient`.
  - **MicroProfile JWT Auth 2.1**: Autenticación con `JsonWebToken` y extracción de claims.
- **Estructura 100% Compatible con Jakarta EE**:
  - Soporte nativo de `src/main/webapp`, `src/main/webapp/WEB-INF` (`web.xml`, `beans.xml`, `faces-config.xml`) y `src/main/resources/META-INF`.
  - Resolución automática de *welcome files* (`index.xhtml`, `index.html`) y despacho de recursos web estáticos.
  - Protección estricta de seguridad contra accesos a `/WEB-INF/*` y `/META-INF/*` según la especificación Servlet 6.0 §10.5.
  - Portabilidad total y sin cambios de código entre **JettraEE**, **Payara Micro**, **Helidon** y **WildFly**.
- **Ecosistema Jettra**:
  - **JettraFlux**: Hospedaje de páginas reactivas y widgets sin requerir servidores externos.
  - **JettraRules**: Validación fluida de reglas de negocio y cálculo de atributos mediante `JettraRulesEngine` y `JettraComputeEngine`.

---

## 📦 Instalación en un Proyecto Maven

Agregue la dependencia de **JettraEE** en su archivo `pom.xml`:

```xml
<dependency>
    <groupId>io.jettra</groupId>
    <artifactId>JettraEE</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## 🚀 Inicio Rápido (Quickstart)

### 1. Clase Principal

```java
package com.miempresa;

import io.jettra.ee.JettraEE;

public class App {
    public static void main(String[] args) {
        // Escanea automáticamente paquetes para registrar REST, CDI, Health y Flux
        JettraEE.start(App.class, args);
    }
}
```

O utilizando el **Fluent Builder**:

```java
JettraEEServer server = JettraEE.builder()
        .port(8080)
        .contextPath("/")
        .scanPackages("com.miempresa")
        .build();

server.start();
```

---

## 💡 Ejemplos de Implementación

### 1. Servicio de Negocio con CDI y MicroProfile Config

```java
package com.miempresa.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ProductoService {

    @Inject
    @ConfigProperty(name = "catalogo.impuesto", defaultValue = "0.07")
    private double tasaImpuesto;

    public double calcularTotal(double precio) {
        return precio * (1.0 + tasaImpuesto);
    }
}
```

### 2. Controlador Jakarta REST con Validación y OpenAPI

```java
package com.miempresa.rest;

import com.miempresa.service.ProductoService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/productos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Productos", description = "Catálogo de productos")
public class ProductoResource {

    @Inject
    private ProductoService productoService;

    public static class ProductoDTO {
        @NotBlank(message = "El nombre es obligatorio")
        public String nombre;

        @Positive(message = "El precio debe ser positivo")
        public double precio;
    }

    @GET
    @Path("/calcular")
    @Operation(summary = "Calcular precio final con impuesto")
    public Response calcular(@QueryParam("precio") double precio) {
        double total = productoService.calcularTotal(precio);
        return Response.ok("{\"total\": " + total + "}").build();
    }

    @POST
    @Operation(summary = "Crear nuevo producto")
    public Response crear(@Valid ProductoDTO dto) {
        return Response.status(201).entity(dto).build();
    }
}
```

### 3. Sonda de Salud MicroProfile (Kubernetes Liveness)

```java
package com.miempresa.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class SondaServidor implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("SondaPrimaria")
                .up()
                .withData("memoria", "OK")
                .build();
    }
}
```

Al acceder a `http://localhost:8080/q/health`:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "SondaPrimaria",
      "status": "UP",
      "data": { "memoria": "OK" }
    }
  ]
}
```

### 4. Resiliencia con MicroProfile Fault Tolerance

```java
package com.miempresa.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@ApplicationScoped
public class ServicioExterno {

    @Timeout(1000)
    @Retry(maxRetries = 3, delay = 100)
    @Fallback(fallbackMethod = "metodoAlternativo")
    public String llamadaRemota() {
        // Operación susceptible a fallos
        return "Respuesta Remota";
    }

    public String metodoAlternativo() {
        return "Respuesta desde Caché Local (Fallback)";
    }
}
```

### 5. Interfaz Reactiva JettraFlux

```java
package com.miempresa.pages;

import com.sun.net.httpserver.HttpExchange;
import io.jettra.core.server.Page;
import io.jettra.flux.core.Widget;
import io.jettra.flux.pages.FluxBaseHandler;
import io.jettra.flux.widgets.Center;
import io.jettra.flux.widgets.Column;
import io.jettra.flux.widgets.Header;
import io.jettra.flux.widgets.Paragraph;

import java.util.Map;

@Page(path = "/dashboard")
public class DashboardPage extends FluxBaseHandler {

    @Override
    protected String getTitle() {
        return "Panel de Control";
    }

    @Override
    protected Widget buildUI(HttpExchange exchange, Map<String, String> params, String currentTheme) {
        return Center.of(
            Column.of(
                Header.of(1, "Bienvenido al Panel"),
                Paragraph.of("Servidor JettraEE con interfaz JettraFlux.")
            )
        );
    }
}
```

---

## 🛠️ Endpoints del Sistema

| Endpoint | Descripción | Formato |
|---|---|---|
| `/q/health` | Estado de salud global | JSON |
| `/q/health/live` | Verificación de Liveness (Kubernetes) | JSON |
| `/q/health/ready` | Verificación de Readiness (Kubernetes) | JSON |
| `/q/metrics` | Métricas JVM y de peticiones HTTP | Prometheus / JSON |
| `/q/openapi` | Especificación OpenAPI 3.1 | JSON |
| `/q/swagger-ui` | Interfaz gráfica interactiva Swagger UI | HTML |

---

## ⚙️ Archivo de Configuración (`application.properties`)

Puede crear un archivo `application.properties` en `src/main/resources`:

```properties
server.port=8080
server.contextpath=/
app.greeting.prefix=Hola Mundo
```

---

## 🐳 Despliegue en Docker

```dockerfile
FROM bellsoft/liberica-openjdk-debian:25-cds
WORKDIR /app
COPY target/mi-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseCompactObjectHeaders", "-jar", "app.jar"]
```

---

## 📚 Documentación Adicional

Consulte la [Guía Completa de Uso](file:///home/avbravo/NetBeansProjects/jettrastack_local/JettraWorkspace/JettraEE/guide/README.md) para tutoriales avanzados de integración y arquitectura.
