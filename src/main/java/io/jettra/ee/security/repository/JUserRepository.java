package io.jettra.ee.security.repository;

import io.jettra.ee.security.entity.JUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JUserRepository {
    void save(JUser user);
    Optional<JUser> findById(UUID id);
    List<JUser> findAll();
    void delete(UUID id);
    List<JUser> search(String query);
    Optional<JUser> findByUsername(String username);
    Optional<JUser> updateUser(String username, UserUpdateCommand command);
}
