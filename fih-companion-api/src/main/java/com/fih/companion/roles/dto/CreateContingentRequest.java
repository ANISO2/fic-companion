package com.fih.companion.roles.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Creation d'un lot. Deux modes, exclusifs :
 *
 * <ul>
 *   <li><b>AUTO</b> — {@code startSerie} vide : le serveur prend les
 *       {@code taille} premiers numeros LIBRES du (evenement, type).
 *       Comportement historique, inchange.</li>
 *   <li><b>PLAGE MANUELLE</b> — {@code startSerie} renseigne : le lot porte
 *       exactement sur [startSerie .. endSerie]. Si {@code endSerie} est vide,
 *       il est calcule = startSerie + (taille - 1). Chaque numero de la plage
 *       est verifie : il doit exister, etre du bon type et du bon evenement,
 *       ne pas etre deja affecte (« affecte a »), ni deja dans un lot.</li>
 * </ul>
 */
public record CreateContingentRequest(
        @NotNull(message = "L'événement est obligatoire.") Integer eventId,
        @NotNull(message = "Le type est obligatoire.") Integer modelId,
        @NotNull(message = "L'utilisateur est obligatoire.") Long userId,

        // AUTO : volume demande. PLAGE : sert a calculer endSerie si absent, et
        // reste indicatif (la taille reelle = nombre de numeros de la plage).
        @Min(value = 1, message = "Le volume doit être d'au moins 1 invitation.")
        Integer taille,

        // PLAGE MANUELLE (facultatif). Vide => mode AUTO.
        String startSerie,
        String endSerie
) {
        /** Mode plage manuelle des que le numero de debut est renseigne. */
        public boolean isManualRange() {
                return startSerie != null && !startSerie.isBlank();
        }
}