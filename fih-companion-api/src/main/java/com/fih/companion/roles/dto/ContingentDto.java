package com.fih.companion.roles.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Une ligne d'audit : quel lot, pour qui, quel volume, quel avancement. */
public record ContingentDto(
        Long id,
        Integer eventId,
        String eventTitle,
        LocalDate eventDate,
        Integer modelId,
        String modelName,
        Long userId,
        String username,
        String displayName,
        int taille,
        long nommees,
        long restantes,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime revokedAt,
        String revokedBy,
        boolean actif
) {
}
