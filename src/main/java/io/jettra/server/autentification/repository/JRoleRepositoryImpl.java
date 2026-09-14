package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JRole;
import io.jettra.server.db.security.JettraSecurityDB;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JRoleRepositoryImpl implements JRoleRepository {
    
    private final JettraSecurityDB db = new JettraSecurityDB();

    @Override
    public void save(JRole role) {
        if (role.id() == null) {
            role = new JRole(UUID.randomUUID(), role.name(), role.active());
        }
        db.save(role.id().toString(), role);
    }

    @Override
    public Optional<JRole> findById(UUID id) {
        if (id == null) return Optional.empty();
        return db.findById(JRole.class, id.toString());
    }

    @Override
    public List<JRole> findAll() {
        return db.findAll(JRole.class);
    }

    @Override
    public void delete(UUID id) {
        if (id != null) {
            db.delete(JRole.class, id.toString());
        }
    }

    @Override
    public List<JRole> search(String query) {
        return db.search(JRole.class, query);
    }
}
