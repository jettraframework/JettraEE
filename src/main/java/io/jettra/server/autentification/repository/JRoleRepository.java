package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JRoleRepository {
    void save(JRole role);
    Optional<JRole> findById(UUID id);
    List<JRole> findAll();
    void delete(UUID id);
    List<JRole> search(String query);
}
