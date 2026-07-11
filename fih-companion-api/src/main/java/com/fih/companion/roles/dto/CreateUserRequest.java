package com.fih.companion.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
        @Size(max = 255, message = "Le nom d'utilisateur est trop long.")
        String username,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir au moins 8 caractères.")
        String password,

        @NotBlank(message = "Le nom affiché est obligatoire.")
        @Size(max = 255, message = "Le nom affiché est trop long.")
        String displayName
) {
}
