package com.fih.companion.repository;

import com.fih.companion.domain.ModeleBillet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ModeleBilletRepository extends JpaRepository<ModeleBillet, Integer> {

    @Query(value = "SELECT reference FROM modelebillet WHERE vente = true", nativeQuery = true)
    List<Integer> findPaidModelReferences();

    /**
     * Les modèles qui sont de VRAIES invitations : leur libellé contient
     * « invitation » (insensible à la casse).
     *
     * <p>C'est la SEULE source de vérité pour distinguer une invitation d'un
     * Kit. Aucune colonne de la base ne le fait : {@code utilitaire} est vrai
     * sur 25 des 35 modèles (badges ET kits), {@code kit} et {@code ventekit}
     * sont faux partout. Seul le nom discrimine.</p>
     *
     * <p>Sur le dump FIC 2026, renvoie exactement : 9, 10, 11, 12, 40, 42.</p>
     *
     * <p>Cet ensemble pilote deux choses distinctes :</p>
     * <ul>
     *   <li>la <b>visibilité par lot</b> (contingent) — les autres types sont
     *       visibles en totalité dès que la permission est cochée ;</li>
     *   <li>l'<b>éligibilité à un lot</b> (RolesAdminService).</li>
     * </ul>
     *
     * <p>L'imprimabilité, elle, reste pilotée par la configuration
     * ({@code fih.badge.model-categories.INVITATION}), qui doit coïncider —
     * ModelClassificationService journalise un avertissement sinon.</p>
     */
    @Query(value = "SELECT reference FROM modelebillet WHERE lower(modele) LIKE '%invitation%'",
            nativeQuery = true)
    List<Integer> findInvitationModelReferences();
}
