package io.jettra.ee.security.service;

import io.jettra.ee.security.entity.JCredential;
import io.jettra.ee.security.entity.JRole;
import io.jettra.ee.security.entity.JUser;
import io.jettra.ee.security.repository.*;
import io.jettra.jwt.JettraJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Servicio central de seguridad en JettraEE.
 * Gestiona autenticación, emisión de tokens JWT, administración de usuarios y roles
 * respaldados por JettraSecurityDB.
 */
@ApplicationScoped
public class JettraSecurityService {

    @Inject
    private JUserRepository userRepository;

    @Inject
    private JCredentialRepository credentialRepository;

    @Inject
    private JRoleRepository roleRepository;

    private String jwtSecret = "default_secret_key_jettra_ee_2026";
    private long jwtExpirationMs = 86400000L; // 24 horas

    public JettraSecurityService() {
        initReposIfNull();
        loadConfig();
    }

    public JettraSecurityService(JUserRepository userRepository, JCredentialRepository credentialRepository, JRoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.roleRepository = roleRepository;
        loadConfig();
    }

    private void initReposIfNull() {
        if (userRepository == null) userRepository = new JUserRepositoryImpl();
        if (credentialRepository == null) credentialRepository = new JCredentialRepositoryImpl();
        if (roleRepository == null) roleRepository = new JRoleRepositoryImpl();
    }

    private void loadConfig() {
        try {
            var cfg = ConfigProvider.getConfig();
            jwtSecret = cfg.getOptionalValue("server.jwt.secret", String.class)
                    .orElse(cfg.getOptionalValue("mp.jwt.verify.publickey", String.class)
                    .orElse("default_secret_key_jettra_ee_2026"));
            jwtExpirationMs = cfg.getOptionalValue("server.jwt.expiration", Long.class)
                    .orElse(86400000L);
        } catch (Throwable ignored) {}
    }

    /**
     * Autentica credenciales contra JettraSecurityDB y emite un token JWT.
     */
    public Optional<String> authenticate(String username, String plainPassword) {
        initReposIfNull();
        if (username == null || plainPassword == null || username.isBlank() || plainPassword.isBlank()) {
            return Optional.empty();
        }

        Optional<JCredential> credOpt = credentialRepository.findByUsernamePassword(username, plainPassword);
        if (credOpt.isEmpty()) {
            return Optional.empty();
        }

        JCredential cred = credOpt.get();
        if (cred.active() != null && !cred.active()) {
            return Optional.empty();
        }

        // Actualizar último login
        JCredential updatedCred = new JCredential(
                cred.id(),
                cred.jUser(),
                cred.username(),
                cred.passwordHash(),
                cred.active(),
                Instant.now()
        );
        credentialRepository.save(updatedCred);

        // Obtener usuario y roles
        JUser user = cred.jUser();
        if (user == null || user.id() == null) {
            user = userRepository.findByUsername(username).orElse(null);
        }

        List<String> roleNames = new ArrayList<>();
        if (user != null && user.jRoles() != null) {
            for (JRole r : user.jRoles()) {
                if (r != null && r.name() != null) {
                    roleNames.add(r.name());
                }
            }
        }

        JettraJWT jwt = new JettraJWT(jwtSecret, jwtExpirationMs);
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleNames);
        claims.put("groups", roleNames);
        if (user != null && user.email() != null) {
            claims.put("email", user.email());
        }

        String rawToken = jwt.generateToken(claims, username);
        return Optional.of(rawToken);
    }

    /**
     * Valida un token y retorna el usuario autenticado.
     */
    public Optional<JUser> validateTokenAndGetUser(String rawToken) {
        initReposIfNull();
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        if (rawToken.startsWith("Bearer ")) {
            rawToken = rawToken.substring(7);
        }

        try {
            JettraJWT jwt = new JettraJWT(jwtSecret, jwtExpirationMs);
            String username = jwt.extractUsername(rawToken);
            if (username != null && !username.isBlank()) {
                return userRepository.findByUsername(username);
            }
        } catch (Exception ignored) {}

        return Optional.empty();
    }

    /**
     * Crea un nuevo usuario y su credencial asociada.
     */
    public JUser registerUser(String username, String plainPassword, String email, String phone, Set<String> roleNames) {
        initReposIfNull();
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Nombre de usuario y contraseña son requeridos.");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("El usuario '" + username + "' ya se encuentra registrado.");
        }

        Set<JRole> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String rn : roleNames) {
                JRole role = roleRepository.findByName(rn).orElseGet(() -> {
                    JRole nr = new JRole(UUID.nameUUIDFromBytes(rn.getBytes(StandardCharsets.UTF_8)), rn, true);
                    roleRepository.save(nr);
                    return nr;
                });
                roles.add(role);
            }
        } else {
            roleRepository.findByName("USER").ifPresent(roles::add);
        }

        UUID userId = UUID.randomUUID();
        JUser user = new JUser(
                userId,
                username,
                "*",
                email != null ? email : username + "@jettra.local",
                phone != null ? phone : "",
                true,
                roles,
                Set.of("*")
        );
        userRepository.save(user);

        String hash = JettraSecurityDBInitializer.hashPassword(plainPassword);
        JCredential credential = new JCredential(
                UUID.randomUUID(),
                user,
                username,
                hash,
                true,
                Instant.now()
        );
        credentialRepository.save(credential);

        return user;
    }

    public List<JUser> listUsers() {
        initReposIfNull();
        return userRepository.findAll();
    }

    public Optional<JUser> findUser(String username) {
        initReposIfNull();
        return userRepository.findByUsername(username);
    }

    public void deleteUser(String username) {
        initReposIfNull();
        Optional<JUser> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            userRepository.delete(userOpt.get().id());
            Optional<JCredential> credOpt = credentialRepository.findByUsername(username);
            credOpt.ifPresent(jCredential -> credentialRepository.delete(jCredential.id()));
        }
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }
}
