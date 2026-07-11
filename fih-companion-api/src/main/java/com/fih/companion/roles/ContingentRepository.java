package com.fih.companion.roles;

import com.fih.companion.roles.projection.ContingentRowProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContingentRepository extends JpaRepository<Contingent, Long> {

    @Query("SELECT count(c) FROM Contingent c WHERE c.appUserId = :userId AND c.revokedAt IS NULL")
    long countActiveByUser(@Param("userId") Long userId);

    /**
     * Vue d'audit complète : un contingent, son événement, son type, son
     * porteur, et l'avancement (nombre de lignes déjà nommées dans
     * badge_affectation). Pas de table de log : tout est dérivé.
     */
    @Query(value = """
            SELECT c.id                       AS "id",
                   c.evenement_id             AS "eventId",
                   e.titre                    AS "eventTitle",
                   e.ddate                    AS "eventDate",
                   c.modelebillet_id          AS "modelId",
                   m.modele                   AS "modelName",
                   c.app_user_id              AS "userId",
                   u.username                 AS "username",
                   u.display_name             AS "displayName",
                   c.taille                   AS "taille",
                   coalesce(p.nommees, 0)     AS "nommees",
                   c.created_at               AS "createdAt",
                   c.created_by               AS "createdBy",
                   c.revoked_at               AS "revokedAt",
                   c.revoked_by               AS "revokedBy"
            FROM companion.contingent c
            JOIN companion.app_user u   ON u.id = c.app_user_id
            LEFT JOIN evenement e       ON e.reference = c.evenement_id
            LEFT JOIN modelebillet m    ON m.reference = c.modelebillet_id
            LEFT JOIN (
                SELECT l.contingent_id, count(*) AS nommees
                FROM companion.contingent_ligne l
                JOIN badge_affectation ba ON ba.numeroserie = l.numeroserie
                GROUP BY l.contingent_id
            ) p ON p.contingent_id = c.id
            WHERE (CAST(:userId AS bigint) IS NULL OR c.app_user_id = CAST(:userId AS bigint))
            ORDER BY c.created_at DESC
            """, nativeQuery = true)
    List<ContingentRowProjection> audit(@Param("userId") Long userId);
}
