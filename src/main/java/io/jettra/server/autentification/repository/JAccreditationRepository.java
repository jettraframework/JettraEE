package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JAccreditation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JAccreditationRepository {
    void save(JAccreditation accreditation);
    Optional<JAccreditation> findById(UUID id);
    List<JAccreditation> findAll();
    List<JAccreditation> search(String query);
    void delete(UUID id);
}
