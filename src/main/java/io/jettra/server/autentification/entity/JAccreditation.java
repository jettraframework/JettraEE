package io.jettra.server.autentification.entity;

import io.jettra.rules.validations.NotNull;
import io.jettra.rules.validations.Size;
import java.io.Serializable;
import java.util.UUID;

public record JAccreditation(
        @NotNull
        UUID id,
        @NotNull
        JRole jRole,
        @NotNull
        @Size(min = 1)
        String feature,
        Boolean active
        ) implements Serializable {
}
