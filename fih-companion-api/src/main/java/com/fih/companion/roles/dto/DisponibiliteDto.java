package com.fih.companion.roles.dto;

/** Combien d'invitations restent libres pour ce (événement, modèle). */
public record DisponibiliteDto(Integer eventId, Integer modelId, long libres) {
}
