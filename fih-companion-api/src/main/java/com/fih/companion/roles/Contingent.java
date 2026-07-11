package com.fih.companion.roles;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un CONTINGENT = un sous-ensemble d'invitations d'UN type ({@code modelebillet}),
 * dans UN événement, affecté à UN utilisateur.
 *
 * <p><b>Pourquoi « contingent » et pas « lot » ?</b> Le mot {@code lot} est déjà
 * pris, dans ce projet, par {@code AffecteeService.assignLot()} : le nommage
 * d'une plage de numéros de série avec un nom de base. Ce sont deux concepts
 * distincts ; l'ancien n'est pas touché.</p>
 *
 * <p>Les lignes réservées vivent dans {@link ContingentLigne}. L'audit
 * (« qui a nommé quoi, quand ») n'a PAS de table dédiée : il se déduit par
 * jointure sur {@code public.badge_affectation}, qui est déjà write-once.</p>
 */
@Entity
@Table(name = "contingent", schema = "companion")
@Getter
@Setter
public class Contingent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "evenement_id", nullable = false)
    private Integer evenementId;

    @Column(name = "modelebillet_id", nullable = false)
    private Integer modelebilletId;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    /** Nombre d'invitations réservées à la création. */
    @Column(name = "taille", nullable = false)
    private int taille;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    /** Non nul = contingent révoqué : l'utilisateur ne voit plus ses lignes. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by", length = 255)
    private String revokedBy;

    protected Contingent() {
    }

    public Contingent(Integer evenementId, Integer modelebilletId, Long appUserId,
                      int taille, String createdBy) {
        this.evenementId = evenementId;
        this.modelebilletId = modelebilletId;
        this.appUserId = appUserId;
        this.taille = taille;
        this.createdAt = LocalDateTime.now();
        this.createdBy = createdBy;
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
