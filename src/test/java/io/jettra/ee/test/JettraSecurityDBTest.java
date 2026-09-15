package io.jettra.ee.test;

import io.jettra.ee.security.entity.JCredential;
import io.jettra.ee.security.entity.JRole;
import io.jettra.ee.security.entity.JUser;
import io.jettra.ee.security.repository.*;
import io.jettra.ee.security.service.JettraSecurityService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class JettraSecurityDBTest {

    private static JUserRepository userRepo;
    private static JCredentialRepository credRepo;
    private static JRoleRepository roleRepo;
    private static JettraSecurityService securityService;

    @BeforeAll
    public static void setUp() {
        JettraSecurityDBInitializer.initializeIfEmpty();
        userRepo = new JUserRepositoryImpl();
        credRepo = new JCredentialRepositoryImpl();
        roleRepo = new JRoleRepositoryImpl();
        securityService = new JettraSecurityService(userRepo, credRepo, roleRepo);
    }

    @Test
    @DisplayName("Inicialización automática de usuario admin y roles maestros")
    public void testMasterRecordsInitialization() {
        Optional<JUser> adminOpt = userRepo.findByUsername("admin");
        assertTrue(adminOpt.isPresent(), "El usuario 'admin' debe existir");

        JUser admin = adminOpt.get();
        assertEquals("admin", admin.username());
        assertTrue(admin.active());
        assertNotNull(admin.jRoles());
        assertTrue(admin.jRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.name())), "Debe tener rol ADMIN");

        Optional<JCredential> credOpt = credRepo.findByUsername("admin");
        assertTrue(credOpt.isPresent(), "La credencial de 'admin' debe existir");

        String expectedHash = JettraSecurityDBInitializer.hashPassword("admin");
        assertEquals(expectedHash, credOpt.get().passwordHash(), "La contraseña debe estar hasheada con SHA-256");
    }

    @Test
    @DisplayName("Autenticación exitosa y emisión de token JWT con JettraSecurityService")
    public void testAuthenticationAndJwtGeneration() {
        Optional<String> tokenOpt = securityService.authenticate("admin", "admin");
        assertTrue(tokenOpt.isPresent(), "La autenticación debe ser exitosa");

        String token = tokenOpt.get();
        assertNotNull(token);
        assertFalse(token.isBlank());

        // Validar token emitido
        Optional<JUser> authUserOpt = securityService.validateTokenAndGetUser(token);
        assertTrue(authUserOpt.isPresent(), "El token debe resolver al usuario 'admin'");
        assertEquals("admin", authUserOpt.get().username());
    }

    @Test
    @DisplayName("Autenticación fallida con credenciales incorrectas")
    public void testAuthenticationFailure() {
        Optional<String> tokenOpt = securityService.authenticate("admin", "password_invalido");
        assertTrue(tokenOpt.isEmpty(), "La autenticación debe fallar ante contraseña errónea");

        Optional<String> nonExistent = securityService.authenticate("usuario_inexistente", "123");
        assertTrue(nonExistent.isEmpty(), "La autenticación debe fallar ante usuario inexistente");
    }

    @Test
    @DisplayName("Creación, búsqueda y autenticación de un nuevo usuario en JettraSecurityDB")
    public void testNewUserRegistrationAndAuthentication() {
        String testUser = "operador_" + System.currentTimeMillis();
        String testPass = "OperadorPass2026!";

        JUser created = securityService.registerUser(
                testUser,
                testPass,
                testUser + "@empresa.com",
                "555-1234",
                Set.of("USER", "MANAGER")
        );

        assertNotNull(created);
        assertEquals(testUser, created.username());

        // Verificar persistencia en repositorios
        Optional<JUser> found = userRepo.findByUsername(testUser);
        assertTrue(found.isPresent());
        assertTrue(found.get().jRoles().stream().anyMatch(r -> "MANAGER".equalsIgnoreCase(r.name())));

        // Autenticar con el nuevo usuario
        Optional<String> tokenOpt = securityService.authenticate(testUser, testPass);
        assertTrue(tokenOpt.isPresent(), "El nuevo usuario debe autenticarse exitosamente");

        Optional<JUser> tokenUser = securityService.validateTokenAndGetUser(tokenOpt.get());
        assertTrue(tokenUser.isPresent());
        assertEquals(testUser, tokenUser.get().username());

        // Limpiar
        securityService.deleteUser(testUser);
        assertTrue(userRepo.findByUsername(testUser).isEmpty());
        assertTrue(credRepo.findByUsername(testUser).isEmpty());
    }
}
