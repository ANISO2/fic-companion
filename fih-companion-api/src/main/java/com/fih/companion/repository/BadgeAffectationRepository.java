package com.fih.companion.repository;

import com.fih.companion.domain.BadgeAffectation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;


public interface BadgeAffectationRepository extends JpaRepository<BadgeAffectation, String> {


    @Query("""
            SELECT count(b) > 0 FROM BadgeAffectation b
            WHERE lower(b.affecteeA) = lower(:base)
               OR lower(b.affecteeA) LIKE lower(concat(:base, '-%'))
            """)
    boolean baseNameUsed(@Param("base") String base);


    @Query("""
            SELECT b.affecteeA FROM BadgeAffectation b
            WHERE lower(b.affecteeA) = lower(:base)
               OR lower(b.affecteeA) LIKE lower(concat(:base, '-%'))
            """)
    List<String> findNamesForBase(@Param("base") String base);


    /**
     * Comme {@link #findNamesForBase(String)}, mais RESTREINT au couple
     * (événement, type d'invitation) du billet — via une jointure sur la table
     * {@code billet} (numeroserie → evenement / modelebillet). Utilisé par
     * l'affectation « Affecté à » unitaire pour que le suffixe « -NN » reparte à
     * 01 dès qu'on change d'événement ou de type. N'affecte PAS l'affectation
     * par lot, qui continue d'utiliser {@link #findNamesForBase(String)}.
     */
    @Query("""
            SELECT b.affecteeA FROM BadgeAffectation b, Billet t
            WHERE b.numeroserie = t.numeroserie
              AND t.evenement = :eventId
              AND t.modelebillet = :modelId
              AND (lower(b.affecteeA) = lower(:base)
                   OR lower(b.affecteeA) LIKE lower(concat(:base, '-%')))
            """)
    List<String> findNamesForBaseScoped(@Param("base") String base,
                                        @Param("eventId") Integer eventId,
                                        @Param("modelId") Integer modelId);


    @Modifying
    @Query("UPDATE BadgeAffectation b SET b.printedAt = :ts WHERE b.numeroserie IN :serials")
    int markPrinted(@Param("serials") Collection<String> serials, @Param("ts") LocalDateTime ts);
}
