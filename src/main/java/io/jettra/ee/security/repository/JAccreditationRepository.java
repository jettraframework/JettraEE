package io.jettra.ee.security.repository;

import io.jettra.ee.security.entity.JAccreditation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JAccreditationRepository {
    void save(JAccreditation accreditation);
    Optional<JAccreditation> findById(UUID id);
    List<JAccreditation> findAll();
    void delete(UUID id);
    List<JAccreditation> search(String query);
}
