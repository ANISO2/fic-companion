package com.fih.companion.roles.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Un compte, tel que renvoyé à l'administrateur. Le hachage n'y figure jamais. */
public record AppUserDto(
        Long id,
        String username,
        String displayName,
        boolean enabled,
        LocalDateTime createdAt,
        String createdBy,
        /** Les types de badge/invitation que ce compte a le droit de voir. */
        List<Integer> modelIds,
        /** Nombre de contingents actifs — bloque la suppression. */
        long contingentsActifs
) {
}
