# Guía de Uso e Implementación de JettraEE 📘

Esta guía documenta paso a paso cómo crear un nuevo proyecto o migrar un proyecto existente para utilizar **JettraEE** como servidor de microservicios y aplicaciones empresariales.

---

## 📑 Tabla de Contenido

1. [Requisitos Previos](#1-requisitos-previos)
2. [Estructura Típica de un Proyecto](#2-estructura-típica-de-un-proyecto)
3. [Configuración del archivo pom.xml](#3-configuración-del-archivo-pomxml)
4. [Punto de Entrada de la Aplicación](#4-punto-de-entrada-de-la-aplicación)
5. [Inyección de Dependencias con Jakarta CDI](#5-inyección-de-dependencias-con-jakarta-cdi)
6. [Creación de Endpoints con Jakarta REST](#6-creación-de-endpoints-con-jakarta-rest)
7. [Validación con Jakarta Validation y JettraRules](#7-validación-con-jakarta-validation-y-jettrarules)
8. [Configuración Externa con MicroProfile Config](#8-configuración-externa-con-microprofile-config)
9. [Observabilidad: Health Checks y Métricas Prometheus](#9-observabilidad-health-checks-y-métricas-prometheus)
10. [Documentación Automática con MicroProfile OpenAPI](#10-documentación-automática-con-microprofile-openapi)
11. [Resiliencia con MicroProfile Fault Tolerance](#11-resiliencia-con-microprofile-fault-tolerance)
12. [Consumo de Microservicios con MicroProfile Rest Client](#12-consumo-de-microservicios-con-microprofile-rest-client)
13. [Vistas Reactivas con JettraFlux](#13-vistas-reactivas-con-jettraflux)
14. [Despliegue y Optimización en Producción](#14-despliegue-y-optimización-en-producción)

---

## 1. Requisitos Previos

- **Java JDK**: Versión 21 o 25 (recomendado Java 25 para soporte de Compact Object Headers `JEP 450`).
- **Apache Maven**: Versión 3.9 o superior.

---

## 2. Estructura Típica de un Proyecto

```
mi-proyecto-ee/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ejemplo/
│   │   │       ├── App.java                   # Clase Principal
│   │   │       ├── rest/                      # Endpoints Jakarta REST
│   │   │       ├── service/                   # Lógica de Negocio CDI
│   │   │       ├── model/                     # DTOs y Entidades con validaciones
│   │   │       ├── health/                    # Sondas MicroProfile Health
│   │   │       └── pages/                     # Páginas JettraFlux (opcional)
│   │   └── resources/
│   │       ├── application.properties         # Configuración del servidor
│   │       └── META-INF/
│   │           └── microprofile-config.properties
│   └── test/
│       └── java/
│           └── com/ejemplo/test/
```

---

## 3. Configuración del archivo pom.xml

En su proyecto Maven, declare como dependencia a `JettraEE`. Como `JettraEE` incluye de forma transitiva las APIs de Jakarta EE, Eclipse MicroProfile, JettraFlux y JettraRules, su `pom.xml` se mantiene sumamente limpio:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ejemplo</groupId>
    <artifactId>mi-servicio-ee</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jettra.ee.version>1.0.0-SNAPSHOT</jettra.ee.version>
    </properties>

    <dependencies>
        <!-- Servidor JettraEE -->
        <dependency>
            <groupId>io.jettra</groupId>
            <artifactId>JettraEE</artifactId>
            <version>${jettra.ee.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.ejemplo.App</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. Punto de Entrada de la Aplicación

Existen dos formas de iniciar JettraEE:

### Modo 1: Autodescubrimiento Automático (Recomendado)

```java
package com.ejemplo;

import io.jettra.ee.JettraEE;

public class App {
    public static void main(String[] args) {
        // Escanea el paquete com.ejemplo y subpaquetes buscando @Path, @ApplicationScoped, @Liveness, etc.
        JettraEE.start(App.class, args);
    }
}
```

### Modo 2: Fluent Builder Programático

```java
package com.ejemplo;

import io.jettra.ee.JettraEE;
import io.jettra.ee.server.JettraEEServer;

public class App {
    public static void main(String[] args) {
        JettraEEServer server = JettraEE.builder()
                .port(9090)
                .contextPath("/api/v1")
                .scanPackages("com.ejemplo")
                .title("Servicio de Pagos")
                .version("2.1.0")
                .build();

        server.start();
    }
}
```

---

## 5. Inyección de Dependencias con Jakarta CDI

Defina sus servicios de negocio anotándolos con `@ApplicationScoped` (o `@RequestScoped`):

```java
package com.ejemplo.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@ApplicationScoped
public class FacturaService {

    @PostConstruct
    public void iniciar() {
        System.out.println("FacturaService inicializado.");
    }

    public double aplicarDescuento(double subtotal, double porcentaje) {
        return subtotal * (1.0 - (porcentaje / 100.0));
    }

    @PreDestroy
    public void limpiar() {
        System.out.println("Cerrando recursos de FacturaService...");
    }
}
```

---

## 6. Creación de Endpoints con Jakarta REST

```java
package com.ejemplo.rest;

import com.ejemplo.service.FacturaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/facturacion")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FacturaResource {

    @Inject
    private FacturaService facturaService;

    @GET
    @Path("/descuento")
    public Response calcularDescuento(@QueryParam("monto") double monto,
                                      @QueryParam("pct") @DefaultValue("10") double pct) {
        double resultado = facturaService.aplicarDescuento(monto, pct);
        return Response.ok("{\"montoOriginal\":" + monto + ",\"total\":" + resultado + "}").build();
    }
}
```

---

## 7. Validación con Jakarta Validation y JettraRules

JettraEE integra validaciones Jakarta Validation (`@NotNull`, `@Size`, `@Min`, `@Max`, `@Email`) junto con reglas de negocio de `JettraRules` (`@Rules` y `@Compute`):

```java
package com.ejemplo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Email;

public class SolicitudDTO {

    @NotBlank(message = "El cliente no puede estar en blanco")
    private String cliente;

    @Email(message = "El email debe ser válido")
    private String email;

    @Min(value = 1, message = "La cantidad mínima debe ser 1")
    private int cantidad;

    // Getters y Setters
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
```

En su recurso REST, agregue `@Valid` al parámetro del método:

```java
@POST
@Path("/solicitudes")
public Response crearSolicitud(@Valid SolicitudDTO dto) {
    return Response.status(201).entity("{\"estado\":\"aceptado\"}").build();
}
```

Si el cliente envía datos no conformes, JettraEE responde automáticamente con `400 Bad Request` detallando las violaciones.

---

## 8. Configuración Externa con MicroProfile Config

Inyecte variables de entorno, propiedades del sistema o valores de archivos de configuración sin acoplar el código:

```java
@ApplicationScoped
public class ConfiguracionApp {

    @Inject
    @ConfigProperty(name = "notificaciones.habilitadas", defaultValue = "true")
    private boolean notificacionesActivas;

    @Inject
    @ConfigProperty(name = "api.clave.secreta")
    private String claveSecreta;
}
```

Archivo `src/main/resources/application.properties`:
```properties
server.port=8080
server.contextpath=/
notificaciones.habilitadas=true
api.clave.secreta=ClaveSuperSecreta2026
```

---

## 9. Observabilidad: Health Checks y Métricas Prometheus

### Sondas de Salud para Kubernetes

```java
package com.ejemplo.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class DatabaseReadinessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        boolean dbOk = verificarConexion();
        return HealthCheckResponse.named("DatabaseCheck")
                .status(dbOk)
                .withData("tiempoRespuestaMs", 3)
                .build();
    }

    private boolean verificarConexion() {
        return true;
    }
}
```

- Endpoint de sondeo: `http://localhost:8080/q/health`
- Endpoint de métricas en formato Prometheus: `http://localhost:8080/q/metrics`

---

## 10. Documentación Automática con MicroProfile OpenAPI

Añada metadatos descriptivos a sus endpoints:

```java
@GET
@Path("/resumen")
@Operation(summary = "Resumen de ventas", description = "Calcula el balance mensual consolidado")
@APIResponse(responseCode = "200", description = "Resumen obtenido correctamente")
public Response obtenerResumen() {
    return Response.ok().build();
}
```

Acceda a la interfaz gráfica interactiva en:
`http://localhost:8080/q/swagger-ui`

---

## 11. Resiliencia con MicroProfile Fault Tolerance

Proteja su aplicación frente a caídas de servicios externos o picos de latencia:

```java
package com.ejemplo.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@ApplicationScoped
public class PasarelaPagoService {

    @Timeout(1500)
    @Retry(maxRetries = 3, delay = 200)
    @Fallback(fallbackMethod = "procesarPagoEnColaOffline")
    public boolean procesarTransaccion(String idTransaccion, double monto) {
        // Llamada a la pasarela bancaria externa
        return llamarApiBancaria(idTransaccion, monto);
    }

    public boolean procesarPagoEnColaOffline(String idTransaccion, double monto) {
        System.out.println("Pasarela caída. Encolando transacción " + idTransaccion + " para reintento asíncrono.");
        return true;
    }
}
```

---

## 12. Consumo de Microservicios con MicroProfile Rest Client

Comuníquese con otros microservicios de manera tipada y declarativa:

```java
package com.ejemplo.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/v1/inventario")
@RegisterRestClient(baseUri = "https://inventario.interno.net")
public interface InventarioClient {

    @GET
    @Path("/{codigo}")
    @Produces(MediaType.APPLICATION_JSON)
    String consultarStock(@PathParam("codigo") String codigoArticulo);
}
```

Inyéctelo directamente en sus beans CDI:

```java
@Inject
private InventarioClient inventarioClient;
```

---

## 13. Vistas Reactivas con JettraFlux

JettraEE le permite servir interfaces gráficas ricas creadas con `JettraFlux` dentro del mismo proceso sin configurar Servlets adicionales:

```java
package com.ejemplo.pages;

import com.sun.net.httpserver.HttpExchange;
import io.jettra.core.server.Page;
import io.jettra.flux.core.Widget;
import io.jettra.flux.pages.FluxBaseHandler;
import io.jettra.flux.widgets.*;

import java.util.Map;

@Page(path = "/panel")
public class PanelAdminPage extends FluxBaseHandler {

    @Override
    protected String getTitle() {
        return "JettraEE - Panel de Monitoreo";
    }

    @Override
    protected Widget buildUI(HttpExchange exchange, Map<String, String> params, String currentTheme) {
        return Center.of(
            Column.of(
                Header.of(1, "Panel de Administración JettraEE"),
                Paragraph.of("Servidor empresarial ligero y escalable.")
            )
        );
    }
}
```

Al navegar a `http://localhost:8080/panel`, JettraEE renderiza directamente el componente reactivo.

---

## 14. Despliegue y Optimización en Producción

### Ejecución en Consola / CLI

```bash
java -XX:+UseCompactObjectHeaders -jar target/mi-servicio-ee.jar --port 8080 --context-path /
```

### Contenedor Docker Ultra Ligero

```dockerfile
# Imagen base optimizada con JDK 25
FROM bellsoft/liberica-openjdk-debian:25-cds

WORKDIR /app
COPY target/mi-servicio-ee.jar app.jar

EXPOSE 8080

# Uso de Virtual Threads y Compact Object Headers (JEP 450)
ENTRYPOINT ["java", "-XX:+UseCompactObjectHeaders", "-jar", "app.jar"]
```

¡Listo! Con esto su microservicio cuenta con el máximo rendimiento, estándares abiertos de Jakarta EE y Eclipse MicroProfile, y la potencia reactiva del ecosistema Jettra.
