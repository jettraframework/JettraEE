package io.jettra.server.autentification.entity;

import io.jettra.rules.validations.NotNull;
import io.jettra.rules.validations.Size;
import java.io.Serializable;
import java.util.UUID;
import java.util.Set;

public record JUser(
        @NotNull
        @Size(min = 3)
        UUID id,
        @NotNull
        @Size(min = 3)
        String firstName,
        @NotNull
        @Size(min = 1)
        String lastName,
        String email,
        String phone,
        Boolean active,
        @NotNull
        Set<JRole> jRoles,
        Set<String> assignedDatabases
        ) implements Serializable {

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
                java.util.Set<String> dbs = new java.util.TreeSet<>();
                for (String db : lastName.split(",")) {
                    String trimmed = db.trim();
                    if (!trimmed.isEmpty()) {
                        dbs.add(trimmed);
                    }
                }
                assignedDatabases = java.util.Collections.unmodifiableSet(dbs);
            } else {
                assignedDatabases = java.util.Collections.emptySet();
            }
        } else {
            java.util.Set<String> dbs = new java.util.TreeSet<>();
            for (String db : assignedDatabases) {
                if (db != null && !db.trim().isEmpty()) {
                    dbs.add(db.trim());
                }
            }
            assignedDatabases = java.util.Collections.unmodifiableSet(dbs);
        }
    }

    /**
     * Overloaded constructor for backwards compatibility with legacy 7-parameter callers.
     */
    public JUser(UUID id, String firstName, String lastName, String email, String phone, Boolean active, Set<JRole> jRoles) {
        this(id, firstName, lastName, email, phone, active, jRoles, null);
    }

    /**
     * Checks if this user is authorized to perform operations against the target database.
     */
    public boolean isAuthorizedForDatabase(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            return false;
        }

        // Global admin role check
        if (jRoles != null) {
            for (JRole r : jRoles) {
                if (r != null && r.name() != null) {
                    String rUpper = r.name().toUpperCase();
                    if ("ADMIN".equals(rUpper) || "DB_ADMIN".equals(rUpper) || "SUPERADMIN".equals(rUpper)) {
                        return true;
                    }
                }
            }
        }

        // Wildcard or direct inclusion in assignedDatabases
        if (assignedDatabases != null) {
            if (assignedDatabases.contains("*") || assignedDatabases.contains(dbName)) {
                return true;
            }
            for (String d : assignedDatabases) {
                if (d.equalsIgnoreCase(dbName.trim())) {
                    return true;
                }
            }
        }

        // Fallback: legacy lastName inspection
        if (lastName != null && !lastName.isBlank()) {
            if ("*".equals(lastName) || lastName.equalsIgnoreCase(dbName.trim())) {
                return true;
            }
            for (String d : lastName.split(",")) {
                if (d.trim().equalsIgnoreCase(dbName.trim()) || "*".equals(d.trim())) {
                    return true;
                }
            }
        }

        return false;
    }
}
