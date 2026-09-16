package io.jettra.ee.test;

import io.jettra.flux.core.FluxConfig;
import io.jettra.flux.core.FluxContext;
import io.jettra.server.JettraServer;
import io.jettra.server.core.JettraContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FluxContextJettraContextInteropTest {

    @BeforeEach
    void setUp() {
        JettraContext.clear();
        FluxContext.clear();
        JettraServer.setContextPath("/");
    }

    @AfterEach
    void tearDown() {
        JettraContext.clear();
        FluxContext.clear();
        JettraServer.setContextPath("/");
    }

    @Test
    void testDataSharedBetweenJettraContextAndFluxContext() {
        // Inicializar contexto con sesión
        JettraContext jCtx = new JettraContext("test-session-123");
        JettraContext.setCurrent(jCtx);

        // 1. Asignar en JettraContext y leer desde FluxContext
        JettraContext.getCurrent().set(JettraContext.Scope.SESSION, "username", "aristides");
        JettraContext.getCurrent().set(JettraContext.Scope.SESSION, "role", "ADMIN");

        assertEquals("aristides", FluxContext.getCurrent().get(FluxContext.Scope.SESSION, "username"));
        assertEquals("ADMIN", FluxContext.getCurrent().get(FluxContext.Scope.SESSION, "role"));
        assertEquals("ADMIN", FluxContext.Scope.SESSION.getRole());
        assertEquals("ADMIN", JettraContext.Scope.SESSION.getRole());

        // 2. Asignar en FluxContext y leer desde JettraContext
        FluxContext.getCurrent().set(FluxContext.Scope.SESSION, "department", "IT");
        assertEquals("IT", JettraContext.getCurrent().get(JettraContext.Scope.SESSION, "department"));

        // 3. Modificar en FluxContext y comprobar actualización en JettraContext
        FluxContext.getCurrent().set(FluxContext.Scope.SESSION, "username", "usuario_actualizado");
        assertEquals("usuario_actualizado", JettraContext.getCurrent().get(JettraContext.Scope.SESSION, "username"));

        // 4. Limpieza de contexto
        JettraContext.clear();
        assertNull(JettraContext.getCurrent());
        assertNull(FluxContext.getCurrent());
    }

    @Test
    void testPathResolutionSynchronization() {
        // Configurar contextPath desde JettraServer
        JettraServer.setContextPath("/api/v1");

        // Comprobar que FluxConfig se actualizó inmediatamente
        assertEquals("/api/v1", FluxConfig.getContextPath());
        assertEquals("/api/v1", JettraServer.getContextPath());

        // Comprobar resolución de rutas
        assertEquals("/api/v1/dashboard", FluxConfig.resolvePath("/dashboard"));
        assertEquals("/api/v1/dashboard", JettraServer.resolvePath("/dashboard"));
        assertEquals("/api/v1/login", FluxConfig.resolvePath("login"));
        assertEquals("/api/v1/login", JettraServer.resolvePath("login"));

        // Restablecer a raíz
        JettraServer.setContextPath("/");
        assertEquals("/", FluxConfig.getContextPath());
        assertEquals("/dashboard", FluxConfig.resolvePath("/dashboard"));
        assertEquals("/dashboard", JettraServer.resolvePath("/dashboard"));
    }
}
