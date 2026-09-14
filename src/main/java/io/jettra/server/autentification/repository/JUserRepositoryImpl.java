package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JUser;
import io.jettra.server.db.security.JettraSecurityDB;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JUserRepositoryImpl implements JUserRepository {
    
    private final JettraSecurityDB db = new JettraSecurityDB();

    @Override
    public void save(JUser user) {
        if (user.id() == null) {
            user = new JUser(
                UUID.randomUUID(), 
                user.firstName(), 
                user.lastName(), 
                user.email(), 
                user.phone(), 
                user.active(), 
                user.jRoles(),
                user.assignedDatabases()
            );
        }
        db.save(user.id().toString(), user);
    }

    @Override
    public Optional<JUser> findById(UUID id) {
        if (id == null) return Optional.empty();
        return db.findById(JUser.class, id.toString());
    }

    @Override
    public List<JUser> findAll() {
   
        return db.findAll(JUser.class);
    }

    @Override
    public void delete(UUID id) {
        if (id != null) {
            Optional<JUser> target = findById(id);
            if (target.isPresent() && "admin".equalsIgnoreCase(target.get().firstName())) {
                throw new io.jettra.server.autentification.exception.ImmutableAccountException("El usuario admin no puede ser revocado.");
            }
            db.delete(JUser.class, id.toString());
        }
    }

    @Override
    public List<JUser> search(String query) {
        return db.search(JUser.class, query);
    }

    @Override
    public Optional<JUser> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return findAll().stream()
                .filter(u -> username.equalsIgnoreCase(u.firstName()))
                .findFirst();
    }

    @Override
    public Optional<JUser> updateUser(String username, UserUpdateCommand command) {
        if (username == null || username.isBlank() || command == null) {
            return Optional.empty();
        }
        Optional<JUser> existingOpt = findByUsername(username);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        JUser existing = existingOpt.get();
        String email = command.email() != null ? command.email() : existing.email();
        String phone = command.phone() != null ? command.phone() : existing.phone();
        Boolean active = command.active() != null ? command.active() : existing.active();
        java.util.Set<io.jettra.server.autentification.entity.JRole> roles = 
            command.roles() != null ? command.roles() : existing.jRoles();
        java.util.Set<String> assignedDbs = 
            command.assignedDatabases() != null ? command.assignedDatabases() : existing.assignedDatabases();

        String dbScope = (assignedDbs != null && !assignedDbs.isEmpty()) ? String.join(", ", assignedDbs) : existing.lastName();

        JUser updatedUser = new JUser(
            existing.id(),
            existing.firstName(), // Immutable primary identity username
            dbScope,
            email,
            phone,
            active,
            roles,
            assignedDbs
        );

        db.save(updatedUser.id().toString(), updatedUser);
        return Optional.of(updatedUser);
    }
}

