package io.jettra.ee.security.entity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record JUser(
        @NotNull
        UUID id,
        @NotNull
        @Size(min = 3)
        String firstName, // Usado como username primario
        String lastName,
        String email,
        String phone,
        Boolean active,
        Set<JRole> jRoles,
        Set<String> assignedDatabases
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public JUser {
        if (lastName == null || lastName.isBlank()) {
            if (assignedDatabases != null && !assignedDatabases.isEmpty()) {
                lastName = String.join(", ", assignedDatabases);
            } else {
                lastName = "*";
            }
        }

        if (assignedDatabases == null) {
            if (lastName != null && !lastName.isBlank() && !lastName.equalsIgnoreCase("None")) {
                Set<String> dbs = new TreeSet<>();
                for (String db : lastName.split(",")) {
                    String trimmed = db.trim();
                    if (!trimmed.isEmpty()) {
                        dbs.add(trimmed);
                    }
                }
                assignedDatabases = Collections.unmodifiableSet(dbs);
            } else {
                assignedDatabases = Collections.emptySet();
            }
        } else {
            Set<String> dbs = new TreeSet<>();
            for (String db : assignedDatabases) {
                if (db != null && !db.trim().isEmpty()) {
                    dbs.add(db.trim());
                }
            }
            assignedDatabases = Collections.unmodifiableSet(dbs);
        }

        if (jRoles == null) {
            jRoles = Collections.emptySet();
        }
    }

    public JUser(UUID id, String firstName, String lastName, String email, String phone, Boolean active, Set<JRole> jRoles) {
        this(id, firstName, lastName, email, phone, active, jRoles, null);
    }

    public String username() {
        return firstName;
    }
}
