package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JAccreditation;
import io.jettra.server.db.security.JettraSecurityDB;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JAccreditationRepositoryImpl implements JAccreditationRepository {

    private final JettraSecurityDB db = new JettraSecurityDB();

    @Override
    public void save(JAccreditation accreditation) {
        if (accreditation.id() == null) {
            accreditation = new JAccreditation(
                UUID.randomUUID(),
                accreditation.jRole(),
                accreditation.feature(),
                accreditation.active()
            );
        }
        db.save(accreditation.id().toString(), accreditation);
    }

    @Override
    public Optional<JAccreditation> findById(UUID id) {
        if (id == null) return Optional.empty();
        return db.findById(JAccreditation.class, id.toString());
    }

    @Override
    public List<JAccreditation> findAll() {
        return db.findAll(JAccreditation.class);
    }

    @Override
    public void delete(UUID id) {
        if (id != null) {
            db.delete(JAccreditation.class, id.toString());
        }
    }

    @Override
    public List<JAccreditation> search(String query) {
        return db.search(JAccreditation.class, query);
    }
}
