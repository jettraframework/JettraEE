package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JUserRepository {
    void save(JUser user);
    Optional<JUser> findById(UUID id);
    List<JUser> findAll();
    void delete(UUID id);
    List<JUser> search(String query);

    /**
     * Finds a user by their immutable primary identity username (firstName).
     */
    default Optional<JUser> findByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return findAll().stream()
                .filter(u -> username.equalsIgnoreCase(u.firstName()))
                .findFirst();
    }

    /**
     * Atomically updates a user's role assignments, database access scope, and profile data.
     * Identity username remains immutable.
     */
    Optional<JUser> updateUser(String username, UserUpdateCommand command);
}

