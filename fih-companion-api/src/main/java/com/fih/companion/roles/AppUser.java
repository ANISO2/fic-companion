package com.fih.companion.roles;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Compte applicatif créé depuis le backoffice (schéma `companion`).
 *
 * <p>Remplace à terme les comptes en dur de {@code fih.security.invitations-accounts},
 * qui restent supportés en repli. Contrairement à {@code public.utilisateur}
 * (base légataire, mots de passe en clair), le mot de passe est ici HACHÉ.</p>
 */
@Entity
@Table(name = "app_user", schema = "companion")
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    /** BCrypt. Jamais renvoyé au client. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String displayName, String createdBy) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
        this.createdBy = createdBy;
    }
}
