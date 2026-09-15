package io.jettra.ee.security.rest;

import io.jettra.ee.security.entity.JRole;
import io.jettra.ee.security.entity.JUser;
import io.jettra.ee.security.service.JettraSecurityService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.*;

/**
 * Controlador Jakarta REST para administración de usuarios en JettraSecurityDB.
 * Protegido mediante @RolesAllowed("ADMIN").
 */
@Path("/api/security/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Administración de Usuarios", description = "Gestión de usuarios y credenciales en JettraSecurityDB")
@SecurityRequirement(name = "BearerAuth")
@RolesAllowed("ADMIN")
public class SecurityUserController {

    @Inject
    private JettraSecurityService securityService;

    public static class CreateUserRequest {
        private String username;
        private String password;
        private String email;
        private String phone;
        private Set<String> roles;

        public CreateUserRequest() {}

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
    }

    private void ensureService() {
        if (securityService == null) {
            securityService = new JettraSecurityService();
        }
    }

    @GET
    @Operation(summary = "Listar todos los usuarios", description = "Requiere rol ADMIN. Retorna la lista de usuarios en JettraSecurityDB.")
    @APIResponse(responseCode = "200", description = "Lista obtenida con éxito")
    @APIResponse(responseCode = "403", description = "Acceso denegado (Requiere ADMIN)")
    public Response listUsers() {
        ensureService();
        List<JUser> users = securityService.listUsers();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JUser u : users) {
            List<String> roles = new ArrayList<>();
            if (u.jRoles() != null) {
                for (JRole r : u.jRoles()) {
                    if (r != null && r.name() != null) roles.add(r.name());
                }
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.id().toString());
            map.put("username", u.username());
            map.put("email", u.email());
            map.put("phone", u.phone());
            map.put("active", u.active());
            map.put("roles", roles);
            result.add(map);
        }
        return Response.ok(result).build();
    }

    @POST
    @Operation(summary = "Crear nuevo usuario", description = "Requiere rol ADMIN. Registra un usuario y su credencial en JettraSecurityDB.")
    @APIResponse(responseCode = "201", description = "Usuario creado exitosamente")
    @APIResponse(responseCode = "400", description = "Datos inválidos o usuario duplicado")
    public Response createUser(CreateUserRequest request) {
        ensureService();
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            return Response.status(400).entity(Map.of("error", "Username y password son obligatorios")).build();
        }

        try {
            JUser created = securityService.registerUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    request.getPhone(),
                    request.getRoles()
            );
            return Response.status(201).entity(Map.of(
                    "mensaje", "Usuario creado con éxito",
                    "id", created.id().toString(),
                    "username", created.username()
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{username}")
    @Operation(summary = "Buscar usuario por username", description = "Requiere rol ADMIN.")
    @APIResponse(responseCode = "200", description = "Usuario encontrado")
    @APIResponse(responseCode = "404", description = "Usuario no encontrado")
    public Response getUser(@PathParam("username") String username) {
        ensureService();
        Optional<JUser> userOpt = securityService.findUser(username);
        if (userOpt.isEmpty()) {
            return Response.status(404).entity(Map.of("error", "Usuario no encontrado: " + username)).build();
        }
        JUser u = userOpt.get();
        List<String> roles = new ArrayList<>();
        if (u.jRoles() != null) {
            for (JRole r : u.jRoles()) {
                if (r != null && r.name() != null) roles.add(r.name());
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.id().toString());
        map.put("username", u.username());
        map.put("email", u.email());
        map.put("phone", u.phone());
        map.put("active", u.active());
        map.put("roles", roles);
        return Response.ok(map).build();
    }

    @DELETE
    @Path("/{username}")
    @Operation(summary = "Eliminar usuario", description = "Requiere rol ADMIN. El usuario 'admin' no puede ser eliminado.")
    @APIResponse(responseCode = "200", description = "Usuario eliminado")
    @APIResponse(responseCode = "400", description = "Operación no permitida")
    public Response deleteUser(@PathParam("username") String username) {
        ensureService();
        if ("admin".equalsIgnoreCase(username)) {
            return Response.status(400).entity(Map.of("error", "El usuario 'admin' no puede ser eliminado")).build();
        }
        try {
            securityService.deleteUser(username);
            return Response.ok(Map.of("mensaje", "Usuario eliminado: " + username)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
