package com.fih.companion.badge.dto;

import java.time.LocalDate;
import java.util.List;


public record AvailabilityDto(
        int eventId, String eventTitle, LocalDate eventDate,
        int modelId, String modelName, List<String> accessZones,
        int injectedCount, int billetCount, int voucherCount,
        boolean eventHasPoster,
        boolean printable,
        /**
         * Chantier 2 — INVITATION | BADGE | ACCES. Unique source de vérité du
         * découpage en trois sections, lue depuis fih.badge.model-categories.
         * Pour un compte non-admin, injectedCount vaut la taille de SON lot.
         */
        String category,
        /**
         * « Confié à » — true SEULEMENT pour un vrai type invitation (nom
         * contenant « invitation »), les seuls susceptibles d'un lot. Distinct
         * de {@code category}, qui range aussi des Kits dans INVITATION. Le
         * front s'en sert pour n'afficher la colonne « Confié à » et le filtre
         * « Non livrées » que là où la livraison par lot existe.
         */
        boolean invitation
) {
}
