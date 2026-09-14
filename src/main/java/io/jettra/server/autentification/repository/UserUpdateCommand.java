package io.jettra.server.autentification.repository;

import io.jettra.server.autentification.entity.JRole;
import java.io.Serializable;
import java.util.Set;

/**
 * Command encapsulation for atomic updates to user profiles, roles, and database authorizations.
 * Maintains identity immutability (username cannot be modified) and preserves password hashes
 * unless an explicit new password is provided.
 */
public record UserUpdateCommand(
    String email,
    String phone,
    Boolean active,
    Set<JRole> roles,
    Set<String> assignedDatabases,
    String newPassword
) implements Serializable {

    public UserUpdateCommand(String email, String phone, Boolean active, Set<JRole> roles, Set<String> assignedDatabases) {
        this(email, phone, active, roles, assignedDatabases, null);
    }

    public UserUpdateCommand(String email, Boolean active, Set<JRole> roles, Set<String> assignedDatabases) {
        this(email, null, active, roles, assignedDatabases, null);
    }
}
