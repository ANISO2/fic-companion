package com.fih.companion.roles;

import com.fih.companion.roles.projection.ContingentLigneProjection;
import com.fih.companion.roles.projection.ScopeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContingentLigneRepository extends JpaRepository<ContingentLigne, String> {

    /**
     * Réservation d'un contingent : les N premiers numéros de série de ce
     * (événement, modèle) qui ne sont dans AUCUN contingent. Ordre stable par
     * numéro de série, comme partout ailleurs dans l'application.
     *
     * <p>Seuls les `billet` sont candidats : dans cette base, tous les vouchers
     * appartiennent à des modèles payants, exclus de la section.</p>
     */
    @Query(value = """
            SELECT b.numeroserie
            FROM billet b
            WHERE b.evenement = CAST(:eventId AS integer)
              AND b.modelebillet = CAST(:modelId AS integer)
              AND NOT EXISTS (SELECT 1 FROM companion.contingent_ligne l
                              WHERE l.numeroserie = b.numeroserie)
            ORDER BY b.numeroserie
            LIMIT CAST(:taille AS integer)
            """, nativeQuery = true)
    List<String> pickFreeSerials(@Param("eventId") Integer eventId,
                                 @Param("modelId") Integer modelId,
                                 @Param("taille") Integer taille);

    /** Combien d'invitations de ce (événement, modèle) sont encore libres. */
    @Query(value = """
            SELECT count(*)
            FROM billet b
            WHERE b.evenement = CAST(:eventId AS integer)
              AND b.modelebillet = CAST(:modelId AS integer)
              AND NOT EXISTS (SELECT 1 FROM companion.contingent_ligne l
                              WHERE l.numeroserie = b.numeroserie)
            """, nativeQuery = true)
    long countFreeSerials(@Param("eventId") Integer eventId, @Param("modelId") Integer modelId);

    /** Les numéros de série visibles par cet utilisateur pour ce (événement, modèle). */
    @Query(value = """
            SELECT l.numeroserie
            FROM companion.contingent_ligne l
            JOIN companion.contingent c ON c.id = l.contingent_id
            JOIN companion.app_user u   ON u.id = c.app_user_id
            WHERE lower(u.username) = lower(:username)
              AND c.revoked_at IS NULL
              AND c.evenement_id = CAST(:eventId AS integer)
              AND c.modelebillet_id = CAST(:modelId AS integer)
            """, nativeQuery = true)
    List<String> serialsForUser(@Param("username") String username,
                                @Param("eventId") Integer eventId,
                                @Param("modelId") Integer modelId);

    /** Ce numéro de série appartient-il à un contingent ACTIF de cet utilisateur ? */
    @Query(value = """
            SELECT count(*) > 0
            FROM companion.contingent_ligne l
            JOIN companion.contingent c ON c.id = l.contingent_id
            JOIN companion.app_user u   ON u.id = c.app_user_id
            WHERE lower(u.username) = lower(:username)
              AND c.revoked_at IS NULL
              AND l.numeroserie = :numeroserie
            """, nativeQuery = true)
    boolean ownedByUser(@Param("username") String username,
                        @Param("numeroserie") String numeroserie);

    /**
     * Les couples (événement, modèle) sur lesquels cet utilisateur a un
     * contingent actif, avec le volume qui lui est réservé. C'est ce qui
     * remplace `injectedCount` dans l'écran de disponibilité pour un
     * non-admin : il ne voit jamais le total de l'événement, seulement le sien.
     */
    @Query(value = """
            SELECT c.evenement_id     AS "eventId",
                   c.modelebillet_id  AS "modelId",
                   count(l.numeroserie) AS "taille"
            FROM companion.contingent c
            JOIN companion.app_user u         ON u.id = c.app_user_id
            JOIN companion.contingent_ligne l ON l.contingent_id = c.id
            WHERE lower(u.username) = lower(:username) AND c.revoked_at IS NULL
            GROUP BY c.evenement_id, c.modelebillet_id
            """, nativeQuery = true)
    List<ScopeProjection> scopesForUser(@Param("username") String username);

    /** Détail d'un contingent, avec l'état d'affectation de chaque ligne (vue admin). */
    @Query(value = """
            SELECT l.numeroserie   AS "numeroserie",
                   b.codebarre     AS "codebarre",
                   ba.affectee_a   AS "affecteeA",
                   ba.updated_by   AS "updatedBy",
                   ba.updated_at   AS "updatedAt",
                   ba.printed_at   AS "printedAt"
            FROM companion.contingent_ligne l
            LEFT JOIN billet b             ON b.numeroserie = l.numeroserie
            LEFT JOIN badge_affectation ba ON ba.numeroserie = l.numeroserie
            WHERE l.contingent_id = :contingentId
            ORDER BY l.numeroserie
            """, nativeQuery = true)
    List<ContingentLigneProjection> linesOf(@Param("contingentId") Long contingentId);

    /**
     * Parmi {@code serials}, ceux qui appartiennent a un contingent ACTIF de cet
     * utilisateur. Une seule requete, au lieu d'un ownedByUser() par numero.
     *
     * <p>Ne jamais appeler avec une liste vide : {@code IN ()} est une erreur SQL.
     * VisibilityService.filterSerials() garantit la non-vacuite.</p>
     */
    @Query(value = """
            SELECT l.numeroserie
            FROM companion.contingent_ligne l
            JOIN companion.contingent c ON c.id = l.contingent_id
            JOIN companion.app_user u   ON u.id = c.app_user_id
            WHERE lower(u.username) = lower(:username)
              AND c.revoked_at IS NULL
              AND l.numeroserie IN (:serials)
            """, nativeQuery = true)
    List<String> ownedSubset(@Param("username") String username,
                             @Param("serials") List<String> serials);

    /**
     * Inspecte une PLAGE de numeros de serie [start, end] pour un lot manuel.
     *
     * <p>Renvoie une ligne par numero de serie EXISTANT dans la plage, avec de
     * quoi decider s'il est affectable :</p>
     * <ul>
     *   <li>{@code modelId} : le type reel du billet (doit correspondre au type
     *       demande) ;</li>
     *   <li>{@code eventId} : l'evenement reel (doit correspondre) ;</li>
     *   <li>{@code affecteeA} : non nul = deja nomme dans badge_affectation ;</li>
     *   <li>{@code contingentId} : non nul = deja dans un lot (le sien ou un
     *       autre).</li>
     * </ul>
     *
     * <p>Les numeros de la plage ABSENTS de la reponse n'existent pas en base :
     * le service le detecte en comparant le compte attendu au compte recu.</p>
     *
     * <p>Comparaison texte : les numeros de serie font tous 10 chiffres, donc
     * l'ordre lexicographique coincide avec l'ordre numerique.</p>
     */
    @Query(value = """
            SELECT b.numeroserie   AS "numeroserie",
                   b.modelebillet  AS "modelId",
                   b.evenement     AS "eventId",
                   ba.affectee_a   AS "affecteeA",
                   l.contingent_id AS "contingentId"
            FROM billet b
            LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
            LEFT JOIN companion.contingent_ligne l ON l.numeroserie = b.numeroserie
            WHERE b.numeroserie BETWEEN :start AND :end
            ORDER BY b.numeroserie
            """, nativeQuery = true)
    List<RangeRowProjection> inspectRange(@Param("start") String start, @Param("end") String end);

    /** Etat d'un numero de serie dans une plage manuelle. */
    interface RangeRowProjection {
        String getNumeroserie();
        Integer getModelId();
        Integer getEventId();
        String getAffecteeA();
        Long getContingentId();
    }
}