package io.jettra.ee.security.repository;

import io.jettra.ee.security.db.JettraSecurityDB;
import io.jettra.ee.security.entity.JCredential;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JCredentialRepositoryImpl implements JCredentialRepository {

    private final JettraSecurityDB db;

    public JCredentialRepositoryImpl() {
        this(new JettraSecurityDB());
    }

    public JCredentialRepositoryImpl(JettraSecurityDB db) {
        this.db = db;
    }

    @Override
    public void save(JCredential credential) {
        if (credential.id() == null) {
            credential = new JCredential(
                    UUID.randomUUID(),
                    credential.jUser(),
                    credential.username(),
                    credential.passwordHash(),
                    credential.active(),
                    credential.lastLogin()
            );
        }
        db.save(credential.id().toString(), credential);
    }

    @Override
    public Optional<JCredential> findById(UUID id) {
        if (id == null) return Optional.empty();
        return db.findById(JCredential.class, id.toString());
    }

    @Override
    public List<JCredential> findAll() {
        return db.findAll(JCredential.class);
    }

    @Override
    public void delete(UUID id) {
        if (id != null) {
            db.delete(JCredential.class, id.toString());
        }
    }

    @Override
    public List<JCredential> search(String query) {
        return db.search(JCredential.class, query);
    }

    @Override
    public Optional<JCredential> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return findAll().stream()
                .filter(c -> username.equalsIgnoreCase(c.username()))
                .findFirst();
    }

    @Override
    public Optional<JCredential> findByUsernamePassword(String username, String plainPassword) {
        if (username == null || plainPassword == null) return Optional.empty();
        String hashedPassword = JettraSecurityDBInitializer.hashPassword(plainPassword);
        return findAll().stream()
                .filter(c -> username.equalsIgnoreCase(c.username()) && hashedPassword.equals(c.passwordHash()))
                .findFirst();
    }
}
