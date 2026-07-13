package com.fih.companion.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "fih.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private String deviceToken = "dev-device-token";

    /**
     * Legacy single account (fih.security.invitations-account.*). Kept only for
     * backward compatibility: if invitations-accounts is empty, this one is used.
     */
    private InvitationsAccount invitationsAccount = new InvitationsAccount();

    /**
     * Feature 2 — named invitations accounts (fih.security.invitations-accounts).
     * Each entry logs in with its OWN username, so the JWT subject — and therefore
     * badge_affectation.updated_by — stays individually attributable.
     */
    private List<InvitationsAccount> invitationsAccounts = new ArrayList<>();

    /**
     * « Gestion » — comptes en dur (fih.security.gestion-accounts) donnant accès
     * aux sections Invitations, Badges, Utilisateurs et Lots d'invitations
     * UNIQUEMENT. Même forme que {@link InvitationsAccount}. Le sujet du JWT reste
     * le nom d'utilisateur réel, donc l'audit (updated_by) reste attribuable.
     */
    private List<InvitationsAccount> gestionAccounts = new ArrayList<>();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public InvitationsAccount getInvitationsAccount() {
        return invitationsAccount;
    }

    public void setInvitationsAccount(InvitationsAccount invitationsAccount) {
        this.invitationsAccount = invitationsAccount;
    }

    public List<InvitationsAccount> getInvitationsAccounts() {
        return invitationsAccounts;
    }

    public void setInvitationsAccounts(List<InvitationsAccount> invitationsAccounts) {
        this.invitationsAccounts = invitationsAccounts == null ? new ArrayList<>() : invitationsAccounts;
    }

    public List<InvitationsAccount> getGestionAccounts() {
        return gestionAccounts;
    }

    public void setGestionAccounts(List<InvitationsAccount> gestionAccounts) {
        this.gestionAccounts = gestionAccounts == null ? new ArrayList<>() : gestionAccounts;
    }

    /** Les comptes « Gestion » réellement exploitables (username + password renseignés). */
    public List<InvitationsAccount> resolvedGestionAccounts() {
        List<InvitationsAccount> out = new ArrayList<>();
        for (InvitationsAccount a : gestionAccounts) {
            if (a != null && a.isConfigured()) out.add(a);
        }
        return out;
    }

    /**
     * The accounts AuthService actually checks: the configured list, or — when the
     * list is absent/empty — the single legacy account, if it is configured.
     */
    public List<InvitationsAccount> resolvedInvitationsAccounts() {
        List<InvitationsAccount> out = new ArrayList<>();
        for (InvitationsAccount a : invitationsAccounts) {
            if (a != null && a.isConfigured()) out.add(a);
        }
        if (out.isEmpty() && invitationsAccount != null && invitationsAccount.isConfigured()) {
            out.add(invitationsAccount);
        }
        return out;
    }

    /** Credentials + display name for one restricted invitations-only account. */
    public static class InvitationsAccount {
        /** Login username (may be an e-mail). Blank/unset disables the account. */
        private String username = "";
        /** Plaintext password, matching the rest of this legacy system. */
        private String password = "";
        /** Name shown in the backoffice header for this account. */
        private String displayName = "Invitations & Badges";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isConfigured() {
            return username != null && !username.isBlank()
                    && password != null && !password.isBlank();
        }
    }

    public static class Jwt {
        /** HS256 secret; must be at least 32 characters. */
        private String secret = "change-me-to-a-long-random-secret-of-32+chars!!";
        private long expirationMinutes = 480;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }
}
