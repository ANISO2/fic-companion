package com.fih.companion.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Le nom affiché est obligatoire.")
        @Size(max = 255, message = "Le nom affiché est trop long.")
        String displayName,
        boolean enabled
) {
}
