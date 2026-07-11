package com.fih.companion.roles;

import com.fih.companion.badge.ModelClassificationService;
import com.fih.companion.diagnostics.ConsoleLog;
import com.fih.companion.domain.ModeleBillet;
import com.fih.companion.repository.ModeleBilletRepository;
import com.fih.companion.roles.dto.*;
import com.fih.companion.roles.projection.ContingentLigneProjection;
import com.fih.companion.roles.projection.ContingentRowProjection;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Toute la logique « Gestion des rôles », réservée à ROLE_ADMIN.
 *
 * <p>Aucune écriture hors du schéma {@code companion}. L'audit n'a pas de table :
 * il se déduit de {@code public.badge_affectation}, déjà write-once.</p>
 */
@Service
public class RolesAdminService {

    private static final String TAG = "ROLES";

    private final AppUserRepository userRepo;
    private final AppUserModeleRepository modeleRepo;
    private final ContingentRepository contingentRepo;
    private final ContingentLigneRepository ligneRepo;
    private final ModeleBilletRepository modeleBilletRepo;
    private final ModelClassificationService classification;
    private final PasswordEncoder encoder;

    public RolesAdminService(AppUserRepository userRepo,
                             AppUserModeleRepository modeleRepo,
                             ContingentRepository contingentRepo,
                             ContingentLigneRepository ligneRepo,
                             ModeleBilletRepository modeleBilletRepo,
                             ModelClassificationService classification,
                             PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.modeleRepo = modeleRepo;
        this.contingentRepo = contingentRepo;
        this.ligneRepo = ligneRepo;
        this.modeleBilletRepo = modeleBilletRepo;
        this.classification = classification;
        this.encoder = encoder;
    }

    // ============================================================ utilisateurs

    @Transactional(readOnly = true)
    public List<AppUserDto> listUsers() {
        return userRepo.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public AppUserDto createUser(CreateUserRequest req, String createdBy) {
        String username = req.username().trim();
        if (userRepo.findByUsernameIgnoreCase(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce nom d'utilisateur existe déjà.");
        }
        AppUser user = new AppUser(username, encoder.encode(req.password()),
                req.displayName().trim(), createdBy);
        AppUser saved = userRepo.save(user);
        ConsoleLog.log(TAG, "utilisateur créé — username=" + username + ", par=" + createdBy);
        return toDto(saved);
    }

    @Transactional
    public AppUserDto updateUser(Long id, UpdateUserRequest req) {
        AppUser user = require(id);
        user.setDisplayName(req.displayName().trim());
        user.setEnabled(req.enabled());
        ConsoleLog.log(TAG, "utilisateur mis à jour — id=" + id + ", actif=" + req.enabled());
        return toDto(userRepo.save(user));
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest req) {
        AppUser user = require(id);
        user.setPasswordHash(encoder.encode(req.password()));
        userRepo.save(user);
        ConsoleLog.log(TAG, "mot de passe réinitialisé — id=" + id);
    }

    /**
     * Suppression d'un compte. REFUSÉE tant qu'il détient un contingent actif :
     * ses invitations resteraient réservées à un fantôme, invisibles de tous.
     * Révoquez ses contingents d'abord, ou désactivez simplement le compte.
     */
    @Transactional
    public void deleteUser(Long id) {
        AppUser user = require(id);
        long actifs = contingentRepo.countActiveByUser(id);
        if (actifs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer ce compte : " + actifs + " lot(s) actif(s). "
                            + "Révoquez-les d'abord, ou désactivez le compte.");
        }
        modeleRepo.deleteByUserId(id);
        userRepo.delete(user);
        ConsoleLog.log(TAG, "utilisateur supprimé — id=" + id + ", username=" + user.getUsername());
    }

    // ============================================================= permissions

    /** Les 35 types, pour la grille d'interrupteurs. Payants inclus mais signalés. */
    @Transactional(readOnly = true)
    public List<ModelOptionDto> modelOptions() {
        List<ModelOptionDto> out = new ArrayList<>();
        for (ModeleBillet m : modeleBilletRepo.findAll()) {
            out.add(new ModelOptionDto(m.getReference(), m.getModele(),
                    classification.category(m.getReference()),
                    classification.isPaid(m.getReference()),
                    classification.isInvitationType(m.getReference())));
        }
        return out.stream()
                .sorted((a, b) -> Integer.compare(a.modelId(), b.modelId()))
                .toList();
    }

    /**
     * Remplace intégralement la liste des types visibles par ce compte.
     * Sémantique de REMPLACEMENT, pas d'ajout : une liste vide retire tout.
     */
    @Transactional
    public AppUserDto setModelPermissions(Long id, ModelPermissionsRequest req) {
        AppUser user = require(id);
        modeleRepo.deleteByUserId(id);
        modeleRepo.flush();

        Set<Integer> wanted = new LinkedHashSet<>(req.modelIds() == null ? List.of() : req.modelIds());
        Set<Integer> known = new LinkedHashSet<>();
        for (ModeleBillet m : modeleBilletRepo.findAll()) known.add(m.getReference());

        List<AppUserModele> rows = new ArrayList<>();
        for (Integer modelId : wanted) {
            if (!known.contains(modelId)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Type de badge inconnu : " + modelId);
            }
            rows.add(new AppUserModele(id, modelId));
        }
        modeleRepo.saveAll(rows);
        ConsoleLog.log(TAG, "permissions mises à jour — id=" + id + ", types=" + wanted);
        return toDto(user);
    }

    // ============================================================= contingents

    /** Combien d'invitations restent libres pour ce (événement, modèle). */
    @Transactional(readOnly = true)
    public DisponibiliteDto disponibilite(Integer eventId, Integer modelId) {
        return new DisponibiliteDto(eventId, modelId, ligneRepo.countFreeSerials(eventId, modelId));
    }

    /**
     * Crée un contingent : réserve les {@code taille} premiers numéros de série
     * libres de ce (événement, modèle) au profit de cet utilisateur.
     *
     * <p>La clé primaire de {@code contingent_ligne} sur {@code numeroserie}
     * garantit qu'une invitation n'atterrit jamais dans deux contingents, même
     * si deux administrateurs cliquent en même temps.</p>
     */
    @Transactional
    public ContingentDto createContingent(CreateContingentRequest req, String createdBy) {
        AppUser user = require(req.userId());
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ce compte est désactivé : impossible de lui affecter un lot.");
        }
        if (classification.isPaid(req.modelId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ce type est payant : il ne peut pas faire l'objet d'un lot d'invitations.");
        }
        requireTrueInvitation(req.modelId());

        // AUTO : les N premiers libres. PLAGE : exactement [start..end], chaque
        // numero verifie (existe, bon type, bon evenement, non affecte, hors lot).
        List<String> serials = req.isManualRange()
                ? resolveManualRange(req)
                : pickAuto(req);

        int taille = serials.size();
        Contingent c = contingentRepo.save(new Contingent(
                req.eventId(), req.modelId(), user.getId(), taille, createdBy));

        List<ContingentLigne> lignes = serials.stream()
                .map(s -> new ContingentLigne(s, c.getId()))
                .toList();
        ligneRepo.saveAll(lignes);
        // La vue d'audit ci-dessous est une requête NATIVE : on force l'écriture
        // avant de la lire, sinon elle ne verrait pas les lignes de ce lot.
        contingentRepo.flush();
        ligneRepo.flush();

        ConsoleLog.log(TAG, "lot créé — id=" + c.getId() + ", événement=" + req.eventId()
                + ", type=" + req.modelId() + ", volume=" + taille
                + (req.isManualRange() ? " (plage " + serials.get(0) + ".." + serials.get(serials.size() - 1) + ")" : " (auto)")
                + ", bénéficiaire=" + user.getUsername() + ", par=" + createdBy);

        return audit(user.getId()).stream()
                .filter(d -> d.id().equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Lot créé mais introuvable."));
    }

    /**
     * Révoque un contingent : ses lignes redeviennent invisibles pour
     * l'utilisateur. Les invitations déjà nommées le RESTENT — {@code
     * badge_affectation} est write-once, on ne réécrit rien. Les lignes non
     * nommées restent réservées à ce contingent (donc indisponibles) : c'est
     * volontaire, une révocation n'est pas une suppression.
     */
    @Transactional
    public ContingentDto revoke(Long contingentId, String revokedBy) {
        Contingent c = contingentRepo.findById(contingentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lot introuvable."));
        if (!c.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce lot est déjà révoqué.");
        }
        c.setRevokedAt(LocalDateTime.now());
        c.setRevokedBy(revokedBy);
        contingentRepo.save(c);
        contingentRepo.flush();
        ConsoleLog.log(TAG, "lot révoqué — id=" + contingentId + ", par=" + revokedBy);
        return audit(null).stream().filter(d -> d.id().equals(contingentId)).findFirst().orElseThrow();
    }

    /**
     * Supprime définitivement un contingent et libère ses lignes NON NOMMÉES.
     * Refusé si au moins une invitation a déjà été nommée : ces lignes sont un
     * enregistrement d'audit immuable et doivent rester rattachées.
     */
    @Transactional
    public void deleteContingent(Long contingentId) {
        Contingent c = contingentRepo.findById(contingentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lot introuvable."));
        long nommees = ligneRepo.linesOf(contingentId).stream()
                .filter(l -> l.getAffecteeA() != null)
                .count();
        if (nommees > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer ce lot : " + nommees + " invitation(s) déjà nommée(s). "
                            + "Révoquez-le à la place.");
        }
        contingentRepo.delete(c);   // ON DELETE CASCADE libère contingent_ligne
        ConsoleLog.log(TAG, "lot supprimé — id=" + contingentId);
    }

    // =================================================================== audit

    /** Tous les lots (ou ceux d'un utilisateur), avec leur avancement. */
    @Transactional(readOnly = true)
    public List<ContingentDto> audit(Long userId) {
        List<ContingentDto> out = new ArrayList<>();
        for (ContingentRowProjection r : contingentRepo.audit(userId)) {
            long nommees = r.getNommees() == null ? 0 : r.getNommees();
            out.add(new ContingentDto(
                    r.getId(), r.getEventId(), r.getEventTitle(),
                    r.getEventDate() == null ? null : r.getEventDate().toLocalDate(),
                    r.getModelId(), r.getModelName(),
                    r.getUserId(), r.getUsername(), r.getDisplayName(),
                    r.getTaille(), nommees, r.getTaille() - nommees,
                    ts(r.getCreatedAt()), r.getCreatedBy(),
                    ts(r.getRevokedAt()), r.getRevokedBy(),
                    r.getRevokedAt() == null));
        }
        return out;
    }

    /** Le détail d'un lot : chaque invitation, nommée ou non, par qui, quand. */
    @Transactional(readOnly = true)
    public List<ContingentLigneDto> lines(Long contingentId) {
        List<ContingentLigneDto> out = new ArrayList<>();
        for (ContingentLigneProjection l : ligneRepo.linesOf(contingentId)) {
            out.add(new ContingentLigneDto(l.getNumeroserie(), l.getCodebarre(), l.getAffecteeA(),
                    l.getUpdatedBy(), ts(l.getUpdatedAt()), ts(l.getPrintedAt())));
        }
        return out;
    }

    // ================================================================= helpers

    /** Mode AUTO : les {@code taille} premiers numeros libres du (evenement, type). */
    private List<String> pickAuto(CreateContingentRequest req) {
        if (req.taille() == null || req.taille() < 1) {
            throw unprocessable("Le volume doit être d'au moins 1 invitation.");
        }
        List<String> serials = ligneRepo.pickFreeSerials(req.eventId(), req.modelId(), req.taille());
        if (serials.size() < req.taille()) {
            throw unprocessable("Seulement " + serials.size() + " invitation(s) libre(s) pour ce type "
                    + "et cet événement, or " + req.taille() + " sont demandées.");
        }
        return serials;
    }

    /**
     * Mode PLAGE MANUELLE : resout et VERIFIE [startSerie..endSerie].
     *
     * <p>Les erreurs sont TYPEES : chaque cas d'echec renvoie un message qui dit
     * precisement ce qui bloque (numero absent, mauvais type, mauvais evenement,
     * deja affecte, deja dans un lot), avec les numeros concernes.</p>
     */
    private List<String> resolveManualRange(CreateContingentRequest req) {
        String start = normalizeSerial(req.startSerie());
        String end = req.endSerie() != null && !req.endSerie().isBlank()
                ? normalizeSerial(req.endSerie())
                : computeEnd(start, req.taille());

        if (end.compareTo(start) < 0) {
            throw unprocessable("Le numéro de fin (" + end + ") est inférieur au numéro de début ("
                    + start + ").");
        }

        long span = serialToLong(end) - serialToLong(start) + 1;
        if (span > 5000) {
            throw unprocessable("La plage demandée est trop large (" + span + " numéros). "
                    + "Limitez-vous à 5000 par lot.");
        }

        List<ContingentLigneRepository.RangeRowProjection> rows = ligneRepo.inspectRange(start, end);

        // 1. Numeros absents de la base (existent dans la plage mais pas en billet).
        Set<String> present = new HashSet<>();
        for (var r : rows) present.add(r.getNumeroserie());
        List<String> missing = new ArrayList<>();
        for (long n = serialToLong(start); n <= serialToLong(end); n++) {
            String s = padSerial(n, start.length());
            if (!present.contains(s)) missing.add(s);
        }
        if (!missing.isEmpty()) {
            throw unprocessable("Ces numéros de série n'existent pas : " + preview(missing) + ".");
        }

        // 2. Mauvais type.
        List<String> wrongType = new ArrayList<>();
        List<String> wrongEvent = new ArrayList<>();
        List<String> alreadyAffected = new ArrayList<>();
        List<String> alreadyInLot = new ArrayList<>();
        for (var r : rows) {
            if (!req.modelId().equals(r.getModelId())) wrongType.add(r.getNumeroserie());
            else if (!req.eventId().equals(r.getEventId())) wrongEvent.add(r.getNumeroserie());
            else if (r.getAffecteeA() != null && !r.getAffecteeA().isBlank())
                alreadyAffected.add(r.getNumeroserie());
            else if (r.getContingentId() != null) alreadyInLot.add(r.getNumeroserie());
        }

        if (!wrongType.isEmpty()) {
            throw unprocessable("Ces numéros ne sont pas du type demandé : " + preview(wrongType) + ".");
        }
        if (!wrongEvent.isEmpty()) {
            throw unprocessable("Ces numéros n'appartiennent pas à l'événement choisi : "
                    + preview(wrongEvent) + ".");
        }
        if (!alreadyAffected.isEmpty()) {
            throw unprocessable("Ces invitations sont déjà affectées à un destinataire "
                    + "(« affecté à ») : " + preview(alreadyAffected) + ". Une invitation nommée "
                    + "ne peut pas être remise dans un lot.");
        }
        if (!alreadyInLot.isEmpty()) {
            throw unprocessable("Ces invitations font déjà partie d'un autre lot : "
                    + preview(alreadyInLot) + ".");
        }

        return rows.stream().map(ContingentLigneRepository.RangeRowProjection::getNumeroserie)
                .sorted().toList();
    }

    // --- utilitaires plage ---

    private static String normalizeSerial(String s) {
        String t = s == null ? "" : s.trim();
        if (!t.matches("\\d{1,10}")) {
            throw unprocessable("Numéro de série invalide : « " + s + " ». "
                    + "Attendu : jusqu'à 10 chiffres.");
        }
        return padSerial(Long.parseLong(t), 10);
    }

    private static String computeEnd(String start, Integer taille) {
        if (taille == null || taille < 1) {
            throw unprocessable("Indiquez un numéro de fin, ou un volume pour le calculer.");
        }
        return padSerial(serialToLong(start) + taille - 1, start.length());
    }

    private static long serialToLong(String s) {
        return Long.parseLong(s);
    }

    private static String padSerial(long n, int len) {
        String s = Long.toString(n);
        return s.length() >= len ? s : "0".repeat(len - s.length()) + s;
    }

    /** Apercu court d'une liste de numeros pour un message d'erreur. */
    private static String preview(List<String> serials) {
        if (serials.size() <= 6) return String.join(", ", serials);
        return String.join(", ", serials.subList(0, 6)) + " … (+" + (serials.size() - 6) + ")";
    }

    private static ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    /**
     * Un lot ne peut être créé que pour un type INVITATION.
     *
     * <p>Les autres types (Badge, Kit, Accès) n'ont pas de lot du tout : leur
     * visibilité est binaire, pilotée par le seul interrupteur de permission.
     * Créer un lot pour eux n'aurait aucun sens — il ne serait jamais lu.</p>
     *
     * <p>La règle vient de {@link ModelClassificationService#isInvitationType},
     * source unique lue en base sur le libellé du modèle.</p>
     */
    private void requireTrueInvitation(Integer modelId) {
        if (!classification.isInvitationType(modelId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Les lots ne concernent que les invitations. Pour un badge, un kit ou "
                            + "un accès, il suffit d'activer le type dans les droits de "
                            + "l'utilisateur : il verra alors la totalité de ce type.");
        }
    }

    private AppUser require(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
    }

    private AppUserDto toDto(AppUser u) {
        return new AppUserDto(u.getId(), u.getUsername(), u.getDisplayName(), u.isEnabled(),
                u.getCreatedAt(), u.getCreatedBy(),
                modeleRepo.findModelIdsByUserId(u.getId()),
                contingentRepo.countActiveByUser(u.getId()));
    }

    private static LocalDateTime ts(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}