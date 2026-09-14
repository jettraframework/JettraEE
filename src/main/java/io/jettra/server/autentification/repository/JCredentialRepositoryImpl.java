package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JCredential;
import io.jettra.server.db.security.JettraSecurityDB;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JCredentialRepositoryImpl implements JCredentialRepository {
    
    private final JettraSecurityDB db = new JettraSecurityDB();

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
    public Optional<JCredential> findByUsernamePassword(String username, String password) {
        String hashedPassword = io.jettra.server.autentification.repository.JettraSecurityDBInitializer.hashPassword(password);
        List<JCredential> creds = db.search(JCredential.class, "username = '" + username + "' AND passwordHash = '" + hashedPassword + "'");
        return creds.isEmpty() ? Optional.empty() : Optional.of(creds.get(0));
    }

    @Override
    public Optional<JCredential> findByUsername(String username) {
        List<JCredential> creds = db.search(JCredential.class, "username = '" + username + "'");
        return creds.isEmpty() ? Optional.empty() : Optional.of(creds.get(0));
    }
}
