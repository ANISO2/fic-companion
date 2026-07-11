package com.fih.companion.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;


public final class Roles {

    private Roles() {
    }

    /** Claim value put in the JWT for the restricted "Invitations &amp; Badges only" accounts. */
    public static final String INVITATIONS_CLAIM = "INVITATIONS";

    /** Spring role names (without the {@code ROLE_} prefix, as used by hasRole/hasAnyRole). */
    public static final String ADMIN = "ADMIN";
    public static final String INVITATIONS = "INVITATIONS";

    /** {@code ROLE_}-prefixed authority granted by {@link JwtAuthFilter} to full admins. */
    public static final String ROLE_ADMIN = "ROLE_" + ADMIN;

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
}
