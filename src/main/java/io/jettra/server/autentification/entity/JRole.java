package io.jettra.server.autentification.entity;

import io.jettra.rules.validations.NotNull;
import io.jettra.rules.validations.Size;
import java.io.Serializable;
import java.util.UUID;

public record JRole(
        @NotNull
        UUID id,
        @NotNull
        @Size(min = 3)
        String name,
        @NotNull
        Boolean active
        ) implements Serializable {
}
