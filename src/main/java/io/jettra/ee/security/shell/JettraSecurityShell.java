package io.jettra.ee.security.shell;

import io.jettra.ee.core.IO;
import io.jettra.ee.security.entity.JCredential;
import io.jettra.ee.security.entity.JRole;
import io.jettra.ee.security.entity.JUser;
import io.jettra.ee.security.repository.JettraSecurityDBInitializer;
import io.jettra.ee.security.service.JettraSecurityService;
import io.jettra.jwt.JettraJWT;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shell Interactivo (CLI / REPL) de administración de seguridad para JettraEE.
 * Permite gestionar usuarios, contraseñas, roles, scopes de bases de datos
 * y emitir/verificar tokens JWT directamente sobre JettraSecurityDB.
 */
public class JettraSecurityShell {

    private final JettraSecurityService securityService;
    private boolean running = true;

    public JettraSecurityShell() {
        // Asegurar que el almacenamiento base esté inicializado
        JettraSecurityDBInitializer.initializeIfEmpty();
        this.securityService = new JettraSecurityService();
    }

    public JettraSecurityShell(JettraSecurityService securityService) {
        this.securityService = securityService;
    }

    public static void main(String[] args) {
        JettraSecurityShell shell = new JettraSecurityShell();
        if (args != null && args.length > 0) {
            String fullCmd = String.join(" ", args).trim();
            if (!fullCmd.isEmpty()) {
                shell.executeLine(fullCmd);
                return;
            }
        }
        shell.startInteractive();
    }

    public void startInteractive() {
        printBanner();
        Scanner scanner = new Scanner(System.in);

        while (running) {
            System.out.print(IO.CYAN + "jettra-security> " + IO.RESET);
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            executeLine(line);
        }
        System.out.println(IO.YELLOW + "Saliendo de Jettra Security Shell. ¡Hasta pronto!" + IO.RESET);
    }

    public String executeLine(String line) {
        if (line == null || line.isBlank()) return "";

        String trimmed = line.trim();
        String[] tokens = trimmed.split("\\s+");
        String mainCmd = tokens[0].toLowerCase();

        try {
            switch (mainCmd) {
                case "exit", "quit", "q" -> {
                    running = false;
                    return "bye";
                }
                case "help", "?" -> {
                    printHelp();
                    return "help displayed";
                }
                case "clear", "cls" -> {
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    return "cleared";
                }
                case "info", "status" -> {
                    return printStatus();
                }
                case "init", "seed" -> {
                    JettraSecurityDBInitializer.initializeIfEmpty();
                    String msg = IO.GREEN + "[SUCCESS] JettraSecurityDB verificado e inicializado correctamente." + IO.RESET;
                    System.out.println(msg);
                    return msg;
                }
                case "users" -> {
                    return listUsers();
                }
                case "roles" -> {
                    return listRoles();
                }
                case "login", "auth" -> {
                    if (tokens.length < 3) {
                        String err = IO.RED + "Uso: login <username> <password>" + IO.RESET;
                        System.out.println(err);
                        return err;
                    }
                    return doLogin(tokens[1], tokens[2]);
                }
                case "token" -> {
                    if (tokens.length >= 3 && "verify".equalsIgnoreCase(tokens[1])) {
                        return verifyToken(tokens[2]);
                    }
                    String err = IO.RED + "Uso: token verify <jwt_token>" + IO.RESET;
                    System.out.println(err);
                    return err;
                }
                case "role" -> {
                    if (tokens.length >= 2) {
                        String sub = tokens[1].toLowerCase();
                        if ("list".equals(sub)) {
                            return listRoles();
                        } else if ("create".equals(sub) || "add".equals(sub)) {
                            if (tokens.length < 3) {
                                String err = IO.RED + "Uso: role create <roleName>" + IO.RESET;
                                System.out.println(err);
                                return err;
                            }
                            return createRole(tokens[2]);
                        }
                    }
                    String err = IO.RED + "Uso: role <list|create> [args]" + IO.RESET;
                    System.out.println(err);
                    return err;
                }
                case "user" -> {
                    if (tokens.length < 2) {
                        return listUsers();
                    }
                    String sub = tokens[1].toLowerCase();
                    return switch (sub) {
                        case "list" -> listUsers();
                        case "show", "info", "get" -> {
                            if (tokens.length < 3) {
                                yield error("Uso: user show <username>");
                            }
                            yield showUser(tokens[2]);
                        }
                        case "create", "add" -> {
                            if (tokens.length < 4) {
                                yield error("Uso: user create <username> <password> [email] [role] [databases]");
                            }
                            String username = tokens[2];
                            String password = tokens[3];
                            String email = tokens.length >= 5 && !"-".equals(tokens[4]) ? tokens[4] : username + "@jettra.local";
                            String role = tokens.length >= 6 ? tokens[5] : "USER";
                            String dbs = tokens.length >= 7 ? tokens[6] : "*";
                            yield createUser(username, password, email, role, dbs);
                        }
                        case "passwd", "password" -> {
                            if (tokens.length < 4) {
                                yield error("Uso: user passwd <username> <newPassword>");
                            }
                            yield changePassword(tokens[2], tokens[3]);
                        }
                        case "status" -> {
                            if (tokens.length < 4) {
                                yield error("Uso: user status <username> <active|inactive>");
                            }
                            boolean active = "active".equalsIgnoreCase(tokens[3]) || "true".equalsIgnoreCase(tokens[3]);
                            yield setUserStatus(tokens[2], active);
                        }
                        case "role" -> {
                            if (tokens.length < 5) {
                                yield error("Uso: user role <add|remove> <username> <roleName>");
                            }
                            String action = tokens[2].toLowerCase();
                            String username = tokens[3];
                            String roleName = tokens[4];
                            if ("add".equals(action)) {
                                yield addRoleToUser(username, roleName);
                            } else if ("remove".equals(action) || "del".equals(action)) {
                                yield removeRoleFromUser(username, roleName);
                            } else {
                                yield error("Acción de rol no reconocida: " + action + ". Use 'add' o 'remove'.");
                            }
                        }
                        case "db" -> {
                            if (tokens.length < 5) {
                                yield error("Uso: user db <assign|revoke> <username> <database>");
                            }
                            String action = tokens[2].toLowerCase();
                            String username = tokens[3];
                            String database = tokens[4];
                            if ("assign".equals(action) || "add".equals(action)) {
                                yield assignDatabase(username, database);
                            } else if ("revoke".equals(action) || "remove".equals(action)) {
                                yield revokeDatabase(username, database);
                            } else {
                                yield error("Acción de base de datos no válida. Use 'assign' o 'revoke'.");
                            }
                        }
                        case "delete", "remove", "drop", "rm" -> {
                            if (tokens.length < 3) {
                                yield error("Uso: user delete <username>");
                            }
                            yield deleteUser(tokens[2]);
                        }
                        default -> error("Comando de usuario no reconocido: '" + sub + "'. Escriba 'help' para ayuda.");
                    };
                }
                default -> {
                    return error("Comando no reconocido: '" + mainCmd + "'. Escriba 'help' para consultar comandos disponibles.");
                }
            }
        } catch (Exception e) {
            String err = IO.RED + "[ERROR] " + e.getMessage() + IO.RESET;
            System.out.println(err);
            return err;
        }
    }

    private String listUsers() {
        List<JUser> users = securityService.listUsers();
        StringBuilder sb = new StringBuilder();
        sb.append(IO.BOLD).append(IO.CYAN).append(String.format("%-18s %-16s %-10s %-28s %-16s\n",
                "USERNAME", "ROLES", "ESTADO", "EMAIL", "DATABASES")).append(IO.RESET);
        sb.append("-".repeat(92)).append("\n");

        for (JUser u : users) {
            String rolesStr = (u.jRoles() != null && !u.jRoles().isEmpty())
                    ? u.jRoles().stream().map(JRole::name).collect(Collectors.joining(","))
                    : "-";
            String dbsStr = (u.assignedDatabases() != null && !u.assignedDatabases().isEmpty())
                    ? String.join(",", u.assignedDatabases())
                    : "*";
            String status = (u.active() != null && u.active())
                    ? IO.GREEN + "ACTIVO" + IO.RESET
                    : IO.RED + "INACTIVO" + IO.RESET;

            sb.append(String.format("%-18s %-16s %-19s %-28s %-16s\n",
                    u.firstName(), rolesStr, status, u.email() != null ? u.email() : "-", dbsStr));
        }
        sb.append(IO.YELLOW).append("Total usuarios: ").append(users.size()).append(IO.RESET);
        String res = sb.toString();
        System.out.println(res);
        return res;
    }

    private String showUser(String username) {
        Optional<JUser> opt = securityService.findUser(username);
        if (opt.isEmpty()) {
            return error("Usuario '" + username + "' no encontrado en JettraSecurityDB.");
        }
        JUser u = opt.get();
        Optional<JCredential> credOpt = securityService.getCredentialRepository().findByUsername(username);

        StringBuilder sb = new StringBuilder();
        sb.append(IO.BOLD).append(IO.GREEN).append("Detalles del Usuario: ").append(u.firstName()).append(IO.RESET).append("\n");
        sb.append(" • UUID:           ").append(u.id()).append("\n");
        sb.append(" • Username:       ").append(u.firstName()).append("\n");
        sb.append(" • Email:          ").append(u.email()).append("\n");
        sb.append(" • Teléfono:       ").append(u.phone() != null ? u.phone() : "-").append("\n");
        sb.append(" • Estado Cuenta:  ").append(u.active() != null && u.active() ? IO.GREEN + "ACTIVO" + IO.RESET : IO.RED + "INACTIVO" + IO.RESET).append("\n");
        sb.append(" • Roles:          ").append(u.jRoles() != null ? u.jRoles().stream().map(JRole::name).collect(Collectors.joining(", ")) : "Ninguno").append("\n");
        sb.append(" • Bases de Datos: ").append(u.assignedDatabases() != null ? String.join(", ", u.assignedDatabases()) : "*").append("\n");
        if (credOpt.isPresent()) {
            JCredential c = credOpt.get();
            sb.append(" • Credencial:     ").append(c.active() != null && c.active() ? IO.GREEN + "ACTIVA" + IO.RESET : IO.RED + "DESACTIVADA" + IO.RESET).append("\n");
            sb.append(" • Último Login:   ").append(c.lastLogin() != null ? c.lastLogin().toString() : "Nunca").append("\n");
        }
        String res = sb.toString();
        System.out.println(res);
        return res;
    }

    private String createUser(String username, String password, String email, String role, String databases) {
        Set<String> roles = Arrays.stream(role.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        JUser created = securityService.registerUser(username, password, email, "", roles);

        if (databases != null && !databases.isBlank() && !"*".equals(databases)) {
            for (String db : databases.split(",")) {
                securityService.assignDatabase(username, db.trim());
            }
        }
        String msg = IO.GREEN + "[SUCCESS] Usuario '" + username + "' creado exitosamente (UUID: " + created.id() + ", Roles: " + role + ")" + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String changePassword(String username, String newPassword) {
        securityService.changePassword(username, newPassword);
        String msg = IO.GREEN + "[SUCCESS] Contraseña actualizada exitosamente para usuario '" + username + "'." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String setUserStatus(String username, boolean active) {
        securityService.setUserStatus(username, active);
        String statusStr = active ? "ACTIVO" : "INACTIVO";
        String msg = IO.GREEN + "[SUCCESS] Estado del usuario '" + username + "' cambiado a " + statusStr + "." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String addRoleToUser(String username, String role) {
        securityService.addRoleToUser(username, role);
        String msg = IO.GREEN + "[SUCCESS] Rol '" + role.toUpperCase() + "' asignado al usuario '" + username + "'." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String removeRoleFromUser(String username, String role) {
        securityService.removeRoleFromUser(username, role);
        String msg = IO.GREEN + "[SUCCESS] Rol '" + role.toUpperCase() + "' removido del usuario '" + username + "'." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String assignDatabase(String username, String database) {
        securityService.assignDatabase(username, database);
        String msg = IO.GREEN + "[SUCCESS] Base de datos '" + database + "' asignada al usuario '" + username + "'." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String revokeDatabase(String username, String database) {
        securityService.revokeDatabase(username, database);
        String msg = IO.GREEN + "[SUCCESS] Base de datos '" + database + "' revocada del usuario '" + username + "'." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String deleteUser(String username) {
        if ("admin".equalsIgnoreCase(username)) {
            return error("El usuario administrador principal ('admin') no puede ser eliminado.");
        }
        securityService.deleteUser(username);
        String msg = IO.GREEN + "[SUCCESS] Usuario '" + username + "' eliminado de JettraSecurityDB." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String listRoles() {
        List<JRole> roles = securityService.listRoles();
        StringBuilder sb = new StringBuilder();
        sb.append(IO.BOLD).append(IO.CYAN).append(String.format("%-20s %-12s %-38s\n", "ROL", "ESTADO", "UUID")).append(IO.RESET);
        sb.append("-".repeat(72)).append("\n");
        for (JRole r : roles) {
            String status = (r.active() != null && r.active()) ? IO.GREEN + "ACTIVO" + IO.RESET : IO.RED + "INACTIVO" + IO.RESET;
            sb.append(String.format("%-20s %-21s %-38s\n", r.name(), status, r.id()));
        }
        String res = sb.toString();
        System.out.println(res);
        return res;
    }

    private String createRole(String roleName) {
        JRole r = securityService.createRole(roleName);
        String msg = IO.GREEN + "[SUCCESS] Rol '" + r.name() + "' creado o verificado (UUID: " + r.id() + ")." + IO.RESET;
        System.out.println(msg);
        return msg;
    }

    private String doLogin(String username, String password) {
        Optional<String> tokenOpt = securityService.authenticate(username, password);
        if (tokenOpt.isEmpty()) {
            return error("Autenticación fallida: credenciales inválidas o usuario inactivo.");
        }
        String token = tokenOpt.get();
        Optional<JUser> userOpt = securityService.findUser(username);
        String rolesStr = userOpt.map(u -> u.jRoles().stream().map(JRole::name).collect(Collectors.joining(", "))).orElse("Ninguno");

        StringBuilder sb = new StringBuilder();
        sb.append(IO.GREEN).append(IO.BOLD).append("[SUCCESS] Autenticación Exitosa para: ").append(username).append(IO.RESET).append("\n");
        sb.append(" • Roles:        ").append(rolesStr).append("\n");
        sb.append(" • Expiración:   24 Horas\n");
        sb.append(" • JWT Token:\n");
        sb.append(IO.CYAN).append(token).append(IO.RESET).append("\n\n");
        sb.append(IO.YELLOW).append("💡 Para usar en Swagger UI (http://localhost:8080/q/swagger-ui):\n");
        sb.append("   1. Clic en botón 'Authorize 🔓'\n");
        sb.append("   2. Pegar este token en 'BearerAuth' y hacer clic en 'Authorize'.").append(IO.RESET);
        String res = sb.toString();
        System.out.println(res);
        return res;
    }

    private String verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return error("Token vacío.");
        }
        String raw = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
        try {
            String[] parts = raw.split("\\.");
            if (parts.length != 3) {
                return error("Estructura de JWT inválida (se esperaban 3 partes separadas por puntos).");
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JettraJWT jwt = new JettraJWT(securityService.getJwtSecret(), securityService.getJwtExpirationMs());
            Map<String, Object> claims = jwt.getPayload(raw);
            if (claims == null) {
                claims = Collections.emptyMap();
            }

            String user = (String) claims.get("sub");
            Object expObj = claims.get("exp");
            long expSec = expObj instanceof Number ? ((Number) expObj).longValue() : 0L;
            boolean expired = (expSec * 1000) < System.currentTimeMillis();

            boolean signatureValid = jwt.isTokenValid(raw, user != null ? user : "");

            StringBuilder sb = new StringBuilder();
            sb.append(IO.BOLD).append(IO.CYAN).append("Resultado de Verificación JWT:").append(IO.RESET).append("\n");
            sb.append(" • Usuario (sub):  ").append(user).append("\n");
            sb.append(" • Roles (roles):  ").append(claims.get("roles")).append("\n");
            sb.append(" • Firma Válida:   ").append(signatureValid ? IO.GREEN + "SÍ (Válida)" + IO.RESET : IO.RED + "NO (Inválida)" + IO.RESET).append("\n");
            sb.append(" • Expiración:     ").append(new Date(expSec * 1000)).append(expired ? IO.RED + " [EXPIRADO]" + IO.RESET : IO.GREEN + " [VIGENTE]" + IO.RESET).append("\n");
            sb.append(" • Payload JSON:   ").append(payloadJson);
            String res = sb.toString();
            System.out.println(res);
            return res;
        } catch (Exception e) {
            return error("Error al verificar token: " + e.getMessage());
        }
    }

    private String printStatus() {
        File dbDir = new File("db/securitydb");
        int usersCount = securityService.listUsers().size();
        int rolesCount = securityService.listRoles().size();
        int credsCount = securityService.getCredentialRepository().findAll().size();

        StringBuilder sb = new StringBuilder();
        sb.append(IO.BOLD).append(IO.PURPLE).append("Estado del Almacén JettraSecurityDB:").append(IO.RESET).append("\n");
        sb.append(" • Ruta de Almacenamiento: ").append(dbDir.getAbsolutePath()).append("\n");
        sb.append(" • Directorio existe:      ").append(dbDir.exists() ? "SÍ" : "NO").append("\n");
        sb.append(" • Total Usuarios:         ").append(usersCount).append("\n");
        sb.append(" • Total Credenciales:     ").append(credsCount).append("\n");
        sb.append(" • Total Roles:            ").append(rolesCount).append("\n");
        String res = sb.toString();
        System.out.println(res);
        return res;
    }

    private String error(String message) {
        String err = IO.RED + "[ERROR] " + message + IO.RESET;
        System.out.println(err);
        return err;
    }

    private void printBanner() {
        System.out.println(IO.CYAN + IO.BOLD);
        System.out.println("================================================================================");
        System.out.println("                   JettraEE SecurityDB Shell (CLI / REPL)                       ");
        System.out.println("         Gestión de Usuarios, Credenciales, Roles y Generador JWT               ");
        System.out.println("================================================================================");
        System.out.println(IO.RESET);
        System.out.println(IO.GREEN + ">> Conectado a JettraSecurityDB local." + IO.RESET);
        System.out.println("Escriba " + IO.YELLOW + "'help'" + IO.RESET + " para ver comandos disponibles o " + IO.YELLOW + "'exit'" + IO.RESET + " para salir.\n");
    }

    private void printHelp() {
        System.out.println(IO.BOLD + IO.CYAN + "Comandos Disponibles en JettraEE Security Shell:" + IO.RESET);
        System.out.println(IO.BOLD + "\n--- Gestión de Usuarios ---" + IO.RESET);
        System.out.println("  users, user list                                        Listar todos los usuarios");
        System.out.println("  user show <username>                                    Mostrar detalles de un usuario");
        System.out.println("  user create <user> <pass> [email] [role] [databases]    Crear nuevo usuario");
        System.out.println("  user passwd <user> <newPassword>                        Cambiar contraseña de usuario");
        System.out.println("  user status <user> <active|inactive>                    Activar o desactivar cuenta");
        System.out.println("  user role add <user> <role>                             Asignar rol a usuario");
        System.out.println("  user role remove <user> <role>                          Remover rol a usuario");
        System.out.println("  user db assign <user> <database>                        Asignar acceso a base de datos");
        System.out.println("  user db revoke <user> <database>                        Revocar acceso a base de datos");
        System.out.println("  user delete <user>                                      Eliminar usuario de SecurityDB");

        System.out.println(IO.BOLD + "\n--- Gestión de Roles ---" + IO.RESET);
        System.out.println("  roles, role list                                        Listar roles definidos");
        System.out.println("  role create <roleName>                                  Crear nuevo rol en el sistema");

        System.out.println(IO.BOLD + "\n--- Autenticación y Tokens JWT ---" + IO.RESET);
        System.out.println("  login <username> <password>                             Autenticar y generar Bearer JWT Token");
        System.out.println("  token verify <jwtToken>                                 Inspeccionar y verificar firma de JWT");

        System.out.println(IO.BOLD + "\n--- Diagnóstico y Sistema ---" + IO.RESET);
        System.out.println("  info, status                                            Ver estado y métricas de SecurityDB");
        System.out.println("  init, seed                                              Verificar/sembrar datos maestros");
        System.out.println("  clear, cls                                              Limpiar pantalla");
        System.out.println("  exit, quit, q                                           Salir del shell\n");
    }
}
