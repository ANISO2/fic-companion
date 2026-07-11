package com.fih.companion.roles.dto;

/**
 * Un type de badge/invitation proposé dans la grille d'interrupteurs et dans
 * le formulaire de création de lot.
 *
 * <p>{@code invitation} est calculé côté serveur, sur le nom RÉEL du modèle
 * (contient « invitation », insensible à la casse) — pas sur la catégorie
 * d'affichage {@code category}, qui range aussi des Kits (VIP, Gradins...)
 * dans INVITATION pour les besoins de la page « Invitations ». Un lot ne peut
 * être créé que si {@code invitation = true}.</p>
 */
public record ModelOptionDto(Integer modelId, String modelName, String category, boolean paid,
                             boolean invitation) {
}
