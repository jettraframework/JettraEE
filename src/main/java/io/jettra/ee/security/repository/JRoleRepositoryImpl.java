package io.jettra.ee.security.repository;

import io.jettra.ee.security.db.JettraSecurityDB;
import io.jettra.ee.security.entity.JRole;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JRoleRepositoryImpl implements JRoleRepository {

    private final JettraSecurityDB db;

    public JRoleRepositoryImpl() {
        this(new JettraSecurityDB());
    }

    public JRoleRepositoryImpl(JettraSecurityDB db) {
        this.db = db;
    }

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

    @Override
    public Optional<JRole> findByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return findAll().stream()
                .filter(r -> name.equalsIgnoreCase(r.name()))
                .findFirst();
    }
}
