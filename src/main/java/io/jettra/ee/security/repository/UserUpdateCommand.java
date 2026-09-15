package io.jettra.ee.security.repository;

import io.jettra.ee.security.entity.JRole;
import java.util.Set;

public record UserUpdateCommand(
        String email,
        String phone,
        Boolean active,
        Set<JRole> roles,
        Set<String> assignedDatabases
) {}
