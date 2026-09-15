package io.jettra.ee.test;

import io.jettra.ee.security.repository.JettraSecurityDBInitializer;
import io.jettra.ee.security.shell.JettraSecurityShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JettraSecurityShellTest {

    private static JettraSecurityShell shell;

    @BeforeAll
    public static void setUp() {
        JettraSecurityDBInitializer.initializeIfEmpty();
        shell = new JettraSecurityShell();
    }

    @Test
    @DisplayName("Ejecución de comandos básicos: help, status, info")
    public void testBasicCommands() {
        String helpRes = shell.executeLine("help");
        assertEquals("help displayed", helpRes);

        String statusRes = shell.executeLine("status");
        assertTrue(statusRes.contains("JettraSecurityDB"));

        String infoRes = shell.executeLine("info");
        assertTrue(infoRes.contains("db/securitydb"));
    }

    @Test
    @DisplayName("Gestión completa de usuarios desde el Shell CLI")
    public void testUserLifecycleInShell() {
        String username = "shelluser_" + System.currentTimeMillis();

        // 1. Crear usuario
        String createRes = shell.executeLine("user create " + username + " password123 " + username + "@jettra.io USER db1,db2");
        assertTrue(createRes.contains("[SUCCESS]"), "Debe crear el usuario: " + createRes);

        // 2. Mostrar detalles
        String showRes = shell.executeLine("user show " + username);
        assertTrue(showRes.contains(username));
        assertTrue(showRes.contains("db1, db2") || showRes.contains("db1"));

        // 3. Listar usuarios
        String listRes = shell.executeLine("users");
        assertTrue(listRes.contains(username));

        // 4. Cambiar contraseña
        String passwdRes = shell.executeLine("user passwd " + username + " newPass456");
        assertTrue(passwdRes.contains("[SUCCESS]"));

        // 5. Autenticación exitosa con nueva contraseña
        String loginRes = shell.executeLine("login " + username + " newPass456");
        assertTrue(loginRes.contains("[SUCCESS]"));

        // 6. Asignar rol
        String addRoleRes = shell.executeLine("user role add " + username + " MANAGER");
        assertTrue(addRoleRes.contains("[SUCCESS]"));

        // 7. Desactivar usuario
        String statusRes = shell.executeLine("user status " + username + " inactive");
        assertTrue(statusRes.contains("[SUCCESS]"));

        // 8. Autenticación debe fallar cuando usuario está inactivo
        String loginInactiveRes = shell.executeLine("login " + username + " newPass456");
        assertTrue(loginInactiveRes.contains("[ERROR]"));

        // 9. Reactivar usuario
        shell.executeLine("user status " + username + " active");

        // 10. Eliminar usuario de prueba
        String delRes = shell.executeLine("user delete " + username);
        assertTrue(delRes.contains("[SUCCESS]"));
    }

    @Test
    @DisplayName("Protección contra eliminación del usuario maestro 'admin'")
    public void testAdminDeletionProtection() {
        String delAdminRes = shell.executeLine("user delete admin");
        assertTrue(delAdminRes.contains("[ERROR]"), "No debe permitir eliminar al usuario 'admin'");
    }

    @Test
    @DisplayName("Gestión de roles y emisión/verificación de tokens JWT")
    public void testRolesAndTokenVerification() {
        String roleRes = shell.executeLine("role create AUDITOR");
        assertTrue(roleRes.contains("[SUCCESS]"));

        String rolesList = shell.executeLine("roles");
        assertTrue(rolesList.contains("AUDITOR"));

        // Login de admin para obtener token
        String loginRes = shell.executeLine("login admin admin");
        assertTrue(loginRes.contains("[SUCCESS]"));

        // Extraer token de la salida
        String[] lines = loginRes.split("\n");
        String jwtToken = null;
        for (String l : lines) {
            String clean = l.replaceAll("\u001B\\[[;\\d]*m", "").trim();
            if (clean.split("\\.").length == 3) {
                jwtToken = clean;
                break;
            }
        }

        assertNotNull(jwtToken, "Debe generar un token JWT válido");
        String verifyRes = shell.executeLine("token verify " + jwtToken);
        assertTrue(verifyRes.contains("admin"));
        assertTrue(verifyRes.contains("SÍ (Válida)"));
    }
}
