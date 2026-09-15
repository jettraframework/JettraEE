package io.jettra.ee.security.rest;

import io.jettra.ee.security.entity.JRole;
import io.jettra.ee.security.entity.JUser;
import io.jettra.ee.security.service.JettraSecurityService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.*;

/**
 * Controlador Jakarta REST para autenticación y emisión de JWT Tokens respaldado por JettraSecurityDB.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Seguridad y Autenticación", description = "Endpoints para inicio de sesión, obtención de JWT Tokens y perfil de usuario")
public class SecurityAuthController {

    @Inject
    private JettraSecurityService securityService;

    public static class LoginRequest {
        private String username;
        private String password;

        public LoginRequest() {}

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    private void ensureService() {
        if (securityService == null) {
            securityService = new JettraSecurityService();
        }
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Iniciar sesión y obtener JWT Token", description = "Valida credenciales contra JettraSecurityDB y retorna un token Bearer JWT.")
    @APIResponse(responseCode = "200", description = "Autenticación exitosa")
    @APIResponse(responseCode = "401", description = "Credenciales inválidas")
    public Response login(LoginRequest request, @QueryParam("username") String qUser, @QueryParam("password") String qPass) {
        ensureService();
        String user = request != null && request.getUsername() != null ? request.getUsername() : qUser;
        String pass = request != null && request.getPassword() != null ? request.getPassword() : qPass;

        if (user == null || pass == null || user.isBlank() || pass.isBlank()) {
            return Response.status(401).entity(Map.of("error", "Nombre de usuario y contraseña son requeridos")).build();
        }

        Optional<String> tokenOpt = securityService.authenticate(user, pass);
        if (tokenOpt.isEmpty()) {
            return Response.status(401).entity(Map.of("error", "Credenciales incorrectas o usuario inactivo")).build();
        }

        String token = tokenOpt.get();
        JUser jUser = securityService.findUser(user).orElse(null);
        List<String> roles = new ArrayList<>();
        if (jUser != null && jUser.jRoles() != null) {
            for (JRole r : jUser.jRoles()) {
                if (r != null && r.name() != null) roles.add(r.name());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", "Bearer " + token);
        response.put("rawToken", token);
        response.put("tokenType", "Bearer");
        response.put("username", user);
        response.put("roles", roles);
        response.put("expiresInMs", securityService.getJwtExpirationMs());

        return Response.ok(response).build();
    }

    @GET
    @Path("/login")
    @PermitAll
    @Operation(summary = "Iniciar sesión por query params (GET)", description = "Alternativa para autenticación vía query string.")
    public Response loginGet(@QueryParam("username") String username, @QueryParam("password") String password) {
        return login(new LoginRequest(username, password), username, password);
    }

    @GET
    @Path("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Obtener perfil del usuario autenticado", description = "Retorna la información del usuario autenticado actualmente.")
    @APIResponse(responseCode = "200", description = "Usuario autenticado")
    @APIResponse(responseCode = "401", description = "No autenticado")
    public Response me(@Context SecurityContext securityContext, @HeaderParam("Authorization") String authHeader) {
        ensureService();
        String username = null;
        if (securityContext != null && securityContext.getUserPrincipal() != null) {
            username = securityContext.getUserPrincipal().getName();
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Optional<JUser> uOpt = securityService.validateTokenAndGetUser(authHeader);
            if (uOpt.isPresent()) {
                username = uOpt.get().username();
            }
        }

        if (username == null || username.isBlank()) {
            return Response.status(401).entity(Map.of("error", "No autorizado. Token inválido o ausente.")).build();
        }

        Optional<JUser> userOpt = securityService.findUser(username);
        if (userOpt.isEmpty()) {
            return Response.status(404).entity(Map.of("error", "Usuario no encontrado en JettraSecurityDB")).build();
        }

        JUser u = userOpt.get();
        List<String> roles = new ArrayList<>();
        if (u.jRoles() != null) {
            for (JRole r : u.jRoles()) {
                if (r != null && r.name() != null) roles.add(r.name());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", u.id().toString());
        data.put("username", u.username());
        data.put("email", u.email());
        data.put("phone", u.phone());
        data.put("active", u.active());
        data.put("roles", roles);

        return Response.ok(data).build();
    }
}
