package com.fih.companion.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir au moins 8 caractères.")
        String password
) {
}
