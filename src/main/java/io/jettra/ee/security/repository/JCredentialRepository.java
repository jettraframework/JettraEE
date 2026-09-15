package io.jettra.ee.security.repository;

import io.jettra.ee.security.entity.JCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JCredentialRepository {
    void save(JCredential credential);
    Optional<JCredential> findById(UUID id);
    List<JCredential> findAll();
    void delete(UUID id);
    List<JCredential> search(String query);
    Optional<JCredential> findByUsername(String username);
    Optional<JCredential> findByUsernamePassword(String username, String plainPassword);
}
