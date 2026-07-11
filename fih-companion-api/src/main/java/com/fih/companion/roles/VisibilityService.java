package com.fih.companion.roles;

import com.fih.companion.badge.ModelClassificationService;
import com.fih.companion.repository.BilletRepository;
import com.fih.companion.roles.projection.ScopeProjection;
import com.fih.companion.security.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Le point UNIQUE ou les droits « voir / affecter » sont appliques.
 *
 * <p><b>DEUX mecanismes de visibilite, pas un.</b></p>
 *
 * <ul>
 *   <li><b>Type « Invitation »</b> (nom contenant « invitation » : 9, 10, 11,
 *       12, 40, 42) : visibilite par <b>LOT</b> (contingent). L'utilisateur ne
 *       voit que les N invitations que l'admin lui a explicitement affectees.</li>
 *   <li><b>Tout le reste</b> (Badges, Kits, Acces) : visibilite <b>BINAIRE par
 *       permission</b>. Si l'admin coche le type, l'utilisateur voit la
 *       TOTALITE de ce type pour l'evenement. Aucune quantite, aucun lot,
 *       aucun sous-ensemble.</li>
 * </ul>
 *
 * <p>Le lot n'est consulte QUE pour un type invitation. Pour les autres, la
 * permission suffit : aucune jointure sur {@code contingent_ligne}.</p>
 *
 * <p><b>Regle d'or :</b> l'API ne renvoie jamais un type non autorise, ni une
 * invitation hors lot. Angular ne fait que ne pas l'afficher — il ne protege
 * rien.</p>
 *
 * <p><b>Fail-closed :</b> un compte non-admin inconnu de {@code companion.app_user}
 * n'a aucune permission : il ne voit rien.</p>
 */
@Service
@Transactional(readOnly = true)
public class VisibilityService {

    private final AppUserRepository userRepo;
    private final AppUserModeleRepository modeleRepo;
    private final ContingentLigneRepository ligneRepo;
    private final BilletRepository billetRepo;
    private final ModelClassificationService classification;

    public VisibilityService(AppUserRepository userRepo,
                             AppUserModeleRepository modeleRepo,
                             ContingentLigneRepository ligneRepo,
                             BilletRepository billetRepo,
                             ModelClassificationService classification) {
        this.userRepo = userRepo;
        this.modeleRepo = modeleRepo;
        this.ligneRepo = ligneRepo;
        this.billetRepo = billetRepo;
        this.classification = classification;
    }

    /** Vrai si l'appelant est un administrateur : aucune restriction. */
    public boolean isAdmin(Authentication auth) {
        return Roles.isAdmin(auth);
    }

    private String username(Authentication auth) {
        return auth == null ? "" : auth.getName();
    }

    // ------------------------------------------------------------------ types

    /**
     * Les IDs de modele que ce compte a le droit de voir (les interrupteurs
     * on/off). {@code Optional.empty()} = admin, aucune restriction.
     */
    public Optional<Set<Integer>> allowedModels(Authentication auth) {
        if (isAdmin(auth)) return Optional.empty();
        Optional<AppUser> user = userRepo.findByUsernameIgnoreCase(username(auth));
        if (user.isEmpty()) return Optional.of(Set.of());   // fail-closed
        return Optional.of(new LinkedHashSet<>(modeleRepo.findModelIdsByUserId(user.get().getId())));
    }

    /**
     * Les (evenement, modele) sur lesquels ce compte detient un contingent actif.
     * Ne concerne QUE les types invitation. {@code Optional.empty()} = admin.
     */
    public Optional<Map<ScopeKey, Long>> scopes(Authentication auth) {
        if (isAdmin(auth)) return Optional.empty();
        Map<ScopeKey, Long> out = new HashMap<>();
        for (ScopeProjection s : ligneRepo.scopesForUser(username(auth))) {
            out.put(new ScopeKey(s.getEventId(), s.getModelId()), s.getTaille());
        }
        return Optional.of(out);
    }

    /** Ce type est-il une vraie invitation (donc soumis au mecanisme de lot) ? */
    public boolean isInvitationType(Integer modelId) {
        return classification.isInvitationType(modelId);
    }

    // ---------------------------------------------------------------- numeros

    /**
     * La restriction par numero de serie a appliquer a ce (evenement, modele).
     *
     * <ul>
     *   <li>{@code Optional.empty()} → <b>aucune restriction par numero</b>.
     *       Deux cas : l'appelant est admin, OU le type n'est pas une invitation
     *       et sa permission vient d'etre validee (403 sinon). Il voit alors la
     *       totalite du type.</li>
     *   <li>{@code Optional.of(liste)} → type invitation : seuls ces numeros
     *       sont visibles. Une liste <b>vide</b> signifie « rien a montrer » :
     *       l'appelant doit renvoyer une page vide SANS interroger la base, car
     *       {@code IN ()} est une erreur SQL.</li>
     * </ul>
     */
    public Optional<List<String>> serialRestriction(Authentication auth, int eventId, int modelId) {
        if (isAdmin(auth)) return Optional.empty();

        // Dans les deux cas, la permission sur le type est obligatoire.
        requireModelVisible(auth, modelId);

        if (!classification.isInvitationType(modelId)) {
            // Badge / Kit / Acces : permission binaire. Aucun lot consulte.
            return Optional.empty();
        }
        return Optional.of(ligneRepo.serialsForUser(username(auth), eventId, modelId));
    }

    // ------------------------------------------------------------------ gardes

    /** Leve 403 si ce compte n'a pas coche ce type. */
    public void requireModelVisible(Authentication auth, Integer modelId) {
        Optional<Set<Integer>> allowed = allowedModels(auth);
        if (allowed.isEmpty()) return;                       // admin
        if (modelId == null || !allowed.get().contains(modelId)) {
            throw forbidden("Ce type ne vous est pas accessible.");
        }
    }

    /**
     * Leve 403 si ce compte n'a pas le droit d'agir sur ce numero de serie.
     *
     * <ul>
     *   <li>type invitation → il doit appartenir a un contingent ACTIF de ce
     *       compte ;</li>
     *   <li>autre type → la permission sur le type suffit.</li>
     * </ul>
     *
     * <p>Appele AVANT le controle write-once, pour qu'un utilisateur ne puisse
     * pas deduire l'existence d'une invitation par un 409 plutot qu'un 403.</p>
     */
    public void requireSerialOwned(Authentication auth, String numeroserie) {
        if (isAdmin(auth)) return;
        if (numeroserie == null) throw forbidden("Numero de serie absent.");

        Integer modelId = billetRepo.findModelIdByNumeroserie(numeroserie).orElse(null);
        if (modelId == null) {
            // Numero inconnu : on refuse sans reveler s'il existe.
            throw forbidden("Cette entree ne vous est pas accessible.");
        }

        requireModelVisible(auth, modelId);

        if (classification.isInvitationType(modelId)
                && !ligneRepo.ownedByUser(username(auth), numeroserie)) {
            throw forbidden("Cette invitation ne fait pas partie de vos lots.");
        }
    }

    /**
     * Filtre une liste de numeros de serie sur ce que ce compte peut voir, en
     * appliquant le bon mecanisme type par type. Une requete pour les modeles,
     * une seule pour les lots (et seulement si un type invitation est concerne).
     */
    public List<String> filterSerials(Authentication auth, List<String> serials) {
        if (isAdmin(auth) || serials == null || serials.isEmpty()) return serials;

        Optional<Set<Integer>> allowedOpt = allowedModels(auth);
        if (allowedOpt.isEmpty()) return serials;            // admin (defensif)
        Set<Integer> allowed = allowedOpt.get();
        if (allowed.isEmpty()) return List.of();             // fail-closed

        Map<String, Integer> modelBySerial = new HashMap<>();
        for (BilletRepository.SerialModelProjection p : billetRepo.findModelIdsBySerials(serials)) {
            modelBySerial.put(p.getNumeroserie(), p.getModelId());
        }

        // Charge a la demande : inutile de payer la requete de lot pour une
        // plage qui ne contient que des badges.
        Set<String> ownedInvitations = null;

        List<String> out = new ArrayList<>(serials.size());
        for (String s : serials) {
            Integer modelId = modelBySerial.get(s);
            if (modelId == null || !allowed.contains(modelId)) continue;

            if (classification.isInvitationType(modelId)) {
                if (ownedInvitations == null) {
                    ownedInvitations = new HashSet<>(ligneRepo.ownedSubset(username(auth), serials));
                }
                if (!ownedInvitations.contains(s)) continue;
            }
            out.add(s);
        }
        return out;
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    /** Cle (evenement, modele). */
    public record ScopeKey(Integer eventId, Integer modelId) {
    }
}
