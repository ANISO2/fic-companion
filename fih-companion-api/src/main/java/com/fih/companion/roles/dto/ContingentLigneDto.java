package com.fih.companion.roles.dto;

import java.time.LocalDateTime;

/** Une invitation d'un contingent : nommée ou non, par qui, quand. */
public record ContingentLigneDto(
        String numeroserie,
        String codebarre,
        String affecteeA,
        String updatedBy,
        LocalDateTime updatedAt,
        LocalDateTime printedAt
) {
}
