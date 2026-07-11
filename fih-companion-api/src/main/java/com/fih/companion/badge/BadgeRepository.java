package com.fih.companion.badge;

import com.fih.companion.badge.projection.AvailabilityProjection;
import com.fih.companion.badge.projection.BadgeItemProjection;
import com.fih.companion.badge.projection.CountsProjection;
import com.fih.companion.domain.Tturnstile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface BadgeRepository extends Repository<Tturnstile, Integer> {

    @Query(value = """
            SELECT e.reference AS "eventId", e.titre AS "eventTitle", e.ddate AS "eventDate",
                   m.reference AS "modelId", m.modele AS "modelName",
                   coalesce(b.cnt, 0) AS "billetCount",
                   coalesce(v.cnt, 0) AS "voucherCount",
                   coalesce(b.cnt, 0) + coalesce(v.cnt, 0) AS "injectedCount"
            FROM (SELECT DISTINCT evenement, modelebillet FROM billet
                  UNION SELECT DISTINCT evenement, modelebillet FROM voucher) k
            JOIN evenement e ON e.reference = k.evenement
            JOIN modelebillet m ON m.reference = k.modelebillet
            LEFT JOIN (SELECT evenement, modelebillet, count(*) cnt FROM billet GROUP BY 1, 2) b
                   ON b.evenement = k.evenement AND b.modelebillet = k.modelebillet
            LEFT JOIN (SELECT evenement, modelebillet, count(*) cnt FROM voucher GROUP BY 1, 2) v
                   ON v.evenement = k.evenement AND v.modelebillet = k.modelebillet
            WHERE (CAST(:eventId AS integer) IS NULL OR e.reference = CAST(:eventId AS integer))
            ORDER BY e.ddate, m.modele
            """, nativeQuery = true)
    List<AvailabilityProjection> availability(@Param("eventId") Integer eventId);


    @Query(value = """
            SELECT type AS "type", numeroserie AS "numeroserie", codebarre AS "codebarre",
                   holderName AS "holderName", affecteeA AS "affecteeA", printedAt AS "printedAt",
                   updatedBy AS "updatedBy", updatedAt AS "updatedAt",
                   deliveredTo AS "deliveredTo", deliveredToUsername AS "deliveredToUsername",
                   deliveredActive AS "deliveredActive"
            FROM (
              SELECT 'BILLET' AS type, b.numeroserie, b.codebarre,
                     NULLIF(trim(coalesce(h.firstname, '') || ' ' || coalesce(h.lastname, '')), '') AS holderName,
                     ba.affectee_a AS affecteeA, ba.printed_at AS printedAt,
                     ba.updated_by AS updatedBy, ba.updated_at AS updatedAt,
                     ou.display_name AS deliveredTo, ou.username AS deliveredToUsername,
                     (co.revoked_at IS NULL) AS deliveredActive
              FROM billet b
              LEFT JOIN holder h ON h.billet = b.numeroserie
              LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
              LEFT JOIN companion.contingent_ligne cl ON cl.numeroserie = b.numeroserie
              LEFT JOIN companion.contingent co       ON co.id = cl.contingent_id
              LEFT JOIN companion.app_user ou         ON ou.id = co.app_user_id
              WHERE b.evenement = CAST(:eventId AS integer) AND b.modelebillet = :modelId
              UNION ALL
              SELECT 'VOUCHER', v.numeroserie, v.codebarre, NULL, ba.affectee_a, ba.printed_at,
                     ba.updated_by, ba.updated_at,
                     CAST(NULL AS text), CAST(NULL AS text), CAST(NULL AS boolean)
              FROM voucher v
              LEFT JOIN badge_affectation ba ON ba.numeroserie = v.numeroserie
              WHERE v.evenement = CAST(:eventId AS integer) AND v.modelebillet = :modelId
            ) x
            WHERE (CAST(:search AS text) IS NULL
                   OR x.numeroserie ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.codebarre ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.holderName ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.affecteeA ILIKE concat('%', CAST(:search AS text), '%'))
              AND (:status = 'all'
                   OR (:status = 'affected' AND x.affecteeA IS NOT NULL)
                   OR (:status = 'pending'  AND x.affecteeA IS NULL))
              AND (:delivery = 'all'
                   OR (:delivery = 'undelivered'
                       AND NOT EXISTS (SELECT 1 FROM companion.contingent_ligne dl
                                       WHERE dl.numeroserie = x.numeroserie)))
            ORDER BY x.numeroserie
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<BadgeItemProjection> items(@Param("eventId") int eventId,
                                    @Param("modelId") int modelId,
                                    @Param("search") String search,
                                    @Param("status") String status,
                                    @Param("delivery") String delivery,
                                    @Param("size") int size,
                                    @Param("offset") int offset);

    @Query(value = """
            SELECT count(*) FROM (
              SELECT b.numeroserie, b.codebarre,
                     NULLIF(trim(coalesce(h.firstname, '') || ' ' || coalesce(h.lastname, '')), '') AS holderName,
                     ba.affectee_a AS affecteeA
              FROM billet b
              LEFT JOIN holder h ON h.billet = b.numeroserie
              LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
              WHERE b.evenement = CAST(:eventId AS integer) AND b.modelebillet = :modelId
              UNION ALL
              SELECT v.numeroserie, v.codebarre, NULL, ba.affectee_a
              FROM voucher v
              LEFT JOIN badge_affectation ba ON ba.numeroserie = v.numeroserie
              WHERE v.evenement = CAST(:eventId AS integer) AND v.modelebillet = :modelId
            ) x
            WHERE (CAST(:search AS text) IS NULL
                   OR x.numeroserie ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.codebarre ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.holderName ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.affecteeA ILIKE concat('%', CAST(:search AS text), '%'))
              AND (:status = 'all'
                   OR (:status = 'affected' AND x.affecteeA IS NOT NULL)
                   OR (:status = 'pending'  AND x.affecteeA IS NULL))
              AND (:delivery = 'all'
                   OR (:delivery = 'undelivered'
                       AND NOT EXISTS (SELECT 1 FROM companion.contingent_ligne dl
                                       WHERE dl.numeroserie = x.numeroserie)))
            """, nativeQuery = true)
    long itemsCount(@Param("eventId") int eventId,
                    @Param("modelId") int modelId,
                    @Param("search") String search,
                    @Param("status") String status,
                    @Param("delivery") String delivery);


    @Query(value = """
            SELECT count(*) FILTER (WHERE x.affecteeA IS NOT NULL) AS "affected",
                   count(*) FILTER (WHERE x.affecteeA IS NULL)     AS "pending",
                   count(*)                                        AS "total"
            FROM (
              SELECT ba.affectee_a AS affecteeA
              FROM billet b
              LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
              WHERE b.evenement = CAST(:eventId AS integer) AND b.modelebillet = :modelId
              UNION ALL
              SELECT ba.affectee_a
              FROM voucher v
              LEFT JOIN badge_affectation ba ON ba.numeroserie = v.numeroserie
              WHERE v.evenement = CAST(:eventId AS integer) AND v.modelebillet = :modelId
            ) x
            """, nativeQuery = true)
    CountsProjection counts(@Param("eventId") int eventId, @Param("modelId") int modelId);

    @Query(value = """
            SELECT 'BILLET' AS "type", b.numeroserie AS "numeroserie", b.codebarre AS "codebarre",
                   NULLIF(trim(coalesce(h.firstname, '') || ' ' || coalesce(h.lastname, '')), '') AS "holderName",
                   ba.affectee_a AS "affecteeA", ba.printed_at AS "printedAt",
                   ba.updated_by AS "updatedBy", ba.updated_at AS "updatedAt"
            FROM billet b
            LEFT JOIN holder h ON h.billet = b.numeroserie
            LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
            WHERE b.evenement = CAST(:eventId AS integer) AND b.modelebillet = :modelId
            UNION ALL
            SELECT 'VOUCHER', v.numeroserie, v.codebarre, NULL, ba.affectee_a, ba.printed_at,
                   ba.updated_by, ba.updated_at
            FROM voucher v
            LEFT JOIN badge_affectation ba ON ba.numeroserie = v.numeroserie
            WHERE v.evenement = CAST(:eventId AS integer) AND v.modelebillet = :modelId
            ORDER BY 2
            """, nativeQuery = true)
    List<BadgeItemProjection> allItems(@Param("eventId") int eventId, @Param("modelId") int modelId);

    // =========================================================================
    // Chantier 3 — variantes RESTREINTES À UNE LISTE DE NUMÉROS DE SÉRIE.
    //
    // Elles sont ADDITIVES : les requêtes ci-dessus ne changent pas, et restent
    // celles utilisées par l'administrateur. Un compte non-admin ne peut passer
    // que par celles-ci, avec les numéros de série de SES lots.
    //
    // Ne jamais appeler avec une liste vide : `IN ()` est une erreur SQL. Le
    // service renvoie une page vide sans requête dans ce cas.
    // =========================================================================

    @Query(value = """
            SELECT type AS "type", numeroserie AS "numeroserie", codebarre AS "codebarre",
                   holderName AS "holderName", affecteeA AS "affecteeA", printedAt AS "printedAt",
                   updatedBy AS "updatedBy", updatedAt AS "updatedAt",
                   deliveredTo AS "deliveredTo", deliveredToUsername AS "deliveredToUsername",
                   deliveredActive AS "deliveredActive"
            FROM (
              SELECT 'BILLET' AS type, b.numeroserie, b.codebarre,
                     NULLIF(trim(coalesce(h.firstname, '') || ' ' || coalesce(h.lastname, '')), '') AS holderName,
                     ba.affectee_a AS affecteeA, ba.printed_at AS printedAt,
                     ba.updated_by AS updatedBy, ba.updated_at AS updatedAt,
                     ou.display_name AS deliveredTo, ou.username AS deliveredToUsername,
                     (co.revoked_at IS NULL) AS deliveredActive
              FROM billet b
              LEFT JOIN holder h ON h.billet = b.numeroserie
              LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
              LEFT JOIN companion.contingent_ligne cl ON cl.numeroserie = b.numeroserie
              LEFT JOIN companion.contingent co       ON co.id = cl.contingent_id
              LEFT JOIN companion.app_user ou         ON ou.id = co.app_user_id
              WHERE b.evenement = CAST(:eventId AS integer) AND b.modelebillet = :modelId
                AND b.numeroserie IN (:serials)
            ) x
            WHERE (CAST(:search AS text) IS NULL
                   OR x.numeroserie ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.codebarre ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.holderName ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.affecteeA ILIKE concat('%', CAST(:search AS text), '%'))
              AND (:status = 'all'
                   OR (:status = 'affected' AND x.affecteeA IS NOT NULL)
                   OR (:status = 'pending'  AND x.affecteeA IS NULL))
              AND (:delivery = 'all'
                   OR (:delivery = 'undelivered'
                       AND NOT EXISTS (SELECT 1 FROM companion.contingent_ligne dl
                                       WHERE dl.numeroserie = x.numeroserie)))
            ORDER BY x.numeroserie
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<BadgeItemProjection> itemsScoped(@Param("eventId") int eventId,
                                          @Param("modelId") int modelId,
                                          @Param("search") String search,
                                          @Param("status") String status,
                                          @Param("delivery") String delivery,
                                          @Param("size") int size,
                                          @Param("offset") int offset,
                                          @Param("serials") List<String> serials);

    @Query(value = """
            SELECT count(*) FROM (
              SELECT b.numeroserie, b.codebarre,
                     NULLIF(trim(coalesce(h.firstname, '') || ' ' || coalesce(h.lastname, '')), '') AS holderName,
                     ba.affectee_a AS affecteeA
              FROM billet b
              LEFT JOIN holder h ON h.billet = b.numeroserie
              LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
              WHERE b.evenement = CAST(:eventId AS integer) AND b.modelebillet = :modelId
                AND b.numeroserie IN (:serials)
            ) x
            WHERE (CAST(:search AS text) IS NULL
                   OR x.numeroserie ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.codebarre ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.holderName ILIKE concat('%', CAST(:search AS text), '%')
                   OR x.affecteeA ILIKE concat('%', CAST(:search AS text), '%'))
              AND (:status = 'all'
                   OR (:status = 'affected' AND x.affecteeA IS NOT NULL)
                   OR (:status = 'pending'  AND x.affecteeA IS NULL))
              AND (:delivery = 'all'
                   OR (:delivery = 'undelivered'
                       AND NOT EXISTS (SELECT 1 FROM companion.contingent_ligne dl
                                       WHERE dl.numeroserie = x.numeroserie)))
            """, nativeQuery = true)
    long itemsCountScoped(@Param("eventId") int eventId,
                          @Param("modelId") int modelId,
                          @Param("search") String search,
                          @Param("status") String status,
                          @Param("delivery") String delivery,
                          @Param("serials") List<String> serials);

    @Query(value = """
            SELECT count(*) FILTER (WHERE ba.affectee_a IS NOT NULL) AS "affected",
                   count(*) FILTER (WHERE ba.affectee_a IS NULL)     AS "pending",
                   count(*)                                          AS "total"
            FROM billet b
            LEFT JOIN badge_affectation ba ON ba.numeroserie = b.numeroserie
            WHERE b.evenement = CAST(:eventId AS integer) AND b.modelebillet = :modelId
              AND b.numeroserie IN (:serials)
            """, nativeQuery = true)
    CountsProjection countsScoped(@Param("eventId") int eventId,
                                  @Param("modelId") int modelId,
                                  @Param("serials") List<String> serials);
}
