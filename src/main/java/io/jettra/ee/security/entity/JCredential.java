package io.jettra.ee.security.entity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record JCredential(
        @NotNull
        UUID id,
        @NotNull
        JUser jUser,
        @NotNull
        @Size(min = 3)
        String username,
        @NotNull
        @Size(min = 8)
        String passwordHash,
        Boolean active,
        Instant lastLogin
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
