package com.fih.companion.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;


public final class Roles {

    private Roles() {
    }

    /** Claim value put in the JWT for the restricted "Invitations &amp; Badges only" accounts. */
    public static final String INVITATIONS_CLAIM = "INVITATIONS";

    /**
     * Claim value pour les comptes « Gestion » : Invitations, Badges,
     * Utilisateurs et Lots d'invitations — mais NI recette NI statistiques.
     */
    public static final String GESTION_CLAIM = "GESTION";

    /** Spring role names (without the {@code ROLE_} prefix, as used by hasRole/hasAnyRole). */
    public static final String ADMIN = "ADMIN";
    public static final String INVITATIONS = "INVITATIONS";
    public static final String GESTION = "GESTION";

    /** {@code ROLE_}-prefixed authority granted by {@link JwtAuthFilter} to full admins. */
    public static final String ROLE_ADMIN = "ROLE_" + ADMIN;

    /** {@code ROLE_}-prefixed authority granted by {@link JwtAuthFilter} to « Gestion » accounts. */
    public static final String ROLE_GESTION = "ROLE_" + GESTION;

    /**
     * Feature 2 — the single source of truth for "may this caller see the audit
     * fields (updated_by / updated_at)?". Controllers read it off the request's
     * Authentication and pass the boolean down; no parallel auth mechanism and
     * no serialization-time magic.
     */
    public static boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (ROLE_ADMIN.equals(a.getAuthority())) return true;
        }
        return false;
    }

    /**
     * « Accès complet aux données » : ADMIN ou GESTION.
     *
     * <p>Un compte GESTION gère globalement les sections Invitations, Badges,
     * Utilisateurs et Lots. Sur ces écrans il n'est soumis à aucun découpage par
     * type ni par lot : il voit TOUT, comme un administrateur. Ceci ne touche
     * PAS l'autorisation des routes ({@link com.fih.companion.security.SecurityConfig}) :
     * GESTION reste bloqué sur la recette, les statistiques et la vérification.</p>
     */
    public static boolean hasFullDataAccess(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String authority = a.getAuthority();
            if (ROLE_ADMIN.equals(authority) || ROLE_GESTION.equals(authority)) return true;
        }
        return false;
    }
}