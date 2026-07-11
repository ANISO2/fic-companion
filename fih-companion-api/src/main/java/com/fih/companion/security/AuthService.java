package com.fih.companion.security;

import com.fih.companion.diagnostics.ConsoleLog;
import com.fih.companion.domain.Utilisateur;
import com.fih.companion.repository.UtilisateurRepository;
import com.fih.companion.roles.AppUser;
import com.fih.companion.roles.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;


@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String TAG = "AUTH";

    private final UtilisateurRepository utilisateurRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    public AuthService(UtilisateurRepository utilisateurRepository,
                       AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SecurityProperties securityProperties) {
        this.utilisateurRepository = utilisateurRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    /**
     * Ordre de resolution, du plus recent au plus ancien :
     *
     * <ol>
     *   <li>{@code companion.app_user} — comptes crees depuis le backoffice,
     *       mots de passe HACHES (BCrypt). C'est le systeme cible.</li>
     *   <li>{@code fih.security.invitations-accounts} — comptes en dur de
     *       application.yml. CONSERVES en repli : rien ne casse en production si
     *       le schema companion n'est pas encore installe. Ils n'ont aucun type
     *       ni aucun lot, donc ils ne voient rien (fail-closed).</li>
     *   <li>{@code public.utilisateur} — administrateurs de la base legataire,
     *       mots de passe en CLAIR. Table en lecture seule, comportement
     *       strictement inchange.</li>
     * </ol>
     *
     * <p>Dans les trois cas, le sujet du JWT est le nom d'utilisateur REEL :
     * {@code badge_affectation.updated_by} reste individuellement attribuable.
     * Aucune identite partagee, jamais.</p>
     */
    public LoginResponse login(LoginRequest request) {

        LoginResponse appUserLogin = tryAppUser(request);
        if (appUserLogin != null) {
            return appUserLogin;
        }

        LoginResponse invitationsLogin = tryInvitationsAccount(request);
        if (invitationsLogin != null) {
            return invitationsLogin;
        }

        Utilisateur user = utilisateurRepository.findByUsername(request.username())
                .orElseThrow(AuthService::unauthorized);

        if (!isAdmin(user) || !passwordMatches(request.password(), user.getPassword())) {
            throw unauthorized();
        }

        String displayName = buildDisplayName(user);
        String token = jwtService.generate(user.getUsername(), user.getRole(), displayName);
        return new LoginResponse(token, user.getRole(), displayName);
    }

    /**
     * Chantier 3 — les comptes geres depuis le backoffice. Mot de passe hache
     * BCrypt. Un compte desactive est refuse avec le MEME message qu'un mauvais
     * mot de passe : on ne confirme jamais l'existence d'un compte.
     */
    private LoginResponse tryAppUser(LoginRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            return null;
        }
        Optional<AppUser> found = appUserRepository.findByUsernameIgnoreCase(request.username().trim());
        if (found.isEmpty()) {
            return null;   // pas un compte applicatif : les autres mecanismes essaient
        }
        AppUser user = found.get();
        if (!user.isEnabled()) {
            ConsoleLog.log(TAG, "DECISION=REJECTED — compte desactive : " + user.getUsername());
            throw unauthorized();
        }
        if (request.password() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            ConsoleLog.log(TAG, "DECISION=REJECTED — mot de passe invalide pour " + user.getUsername());
            throw unauthorized();
        }
        ConsoleLog.log(TAG, "DECISION=AUTHENTICATED — compte applicatif " + user.getUsername()
                + " (companion.app_user, BCrypt).");
        String token = jwtService.generate(user.getUsername(), Roles.INVITATIONS_CLAIM, user.getDisplayName());
        return new LoginResponse(token, Roles.INVITATIONS_CLAIM, user.getDisplayName());
    }


    /**
     * Feature 2 — several named invitations accounts (AdminInv1, AdminInv2, ...).
     * The JWT subject is the MATCHED account's own username, never a shared
     * "INVITATIONS" string: that subject is what InvitationController passes as
     * updatedBy into badge_affectation, so each account stays attributable.
     *
     * <p>Chantier 3 — mecanisme CONSERVE tel quel, en repli. Il ne sert plus
     * qu'aux comptes absents de companion.app_user.</p>
     */
    private LoginResponse tryInvitationsAccount(LoginRequest request) {
        if (request == null) return null;
        List<SecurityProperties.InvitationsAccount> accounts =
                securityProperties.resolvedInvitationsAccounts();
        if (accounts.isEmpty()) return null;

        String submittedUser = request.username() == null ? "" : request.username().trim();
        if (submittedUser.isEmpty()) return null;

        for (SecurityProperties.InvitationsAccount acc : accounts) {
            if (!submittedUser.equalsIgnoreCase(acc.getUsername().trim())) {
                continue;
            }
            if (!passwordMatches(request.password(), acc.getPassword())) {
                throw unauthorized();
            }
            String displayName = acc.getDisplayName() == null || acc.getDisplayName().isBlank()
                    ? acc.getUsername() : acc.getDisplayName();
            String token = jwtService.generate(acc.getUsername(), Roles.INVITATIONS_CLAIM, displayName);
            return new LoginResponse(token, Roles.INVITATIONS_CLAIM, displayName);
        }
        return null;
    }

    private boolean isAdmin(Utilisateur user) {
        return "Administrateur".equalsIgnoreCase(user.getRole());
    }

    /** Legacy : la base billetterie stocke les mots de passe en clair. Inchange. */
    private boolean passwordMatches(String submitted, String stored) {
        return stored != null && stored.equals(submitted);
    }

    private String buildDisplayName(Utilisateur user) {
        String first = user.getFirstname() == null ? "" : user.getFirstname().trim();
        String last = user.getLastname() == null ? "" : user.getLastname().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides.");
    }
}
