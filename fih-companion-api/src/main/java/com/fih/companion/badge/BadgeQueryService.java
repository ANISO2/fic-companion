package com.fih.companion.badge;

import com.fih.companion.access.AccessZoneResolver;
import com.fih.companion.badge.dto.*;
import com.fih.companion.diagnostics.ConsoleLog;
import com.fih.companion.badge.projection.AvailabilityProjection;
import com.fih.companion.badge.projection.BadgeItemProjection;
import com.fih.companion.badge.projection.CountsProjection;
import com.fih.companion.domain.BadgeAffectation;
import com.fih.companion.domain.Billet;
import com.fih.companion.domain.ModeleBillet;
import com.fih.companion.domain.Voucher;
import com.fih.companion.evenement.Evenement;
import com.fih.companion.evenement.EvenementRepository;
import com.fih.companion.repository.BadgeAffectationRepository;
import com.fih.companion.repository.BilletRepository;
import com.fih.companion.repository.HolderRepository;
import com.fih.companion.repository.ModeleBilletRepository;
import com.fih.companion.repository.VoucherRepository;
import com.fih.companion.roles.VisibilityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;


@Service
@Transactional(readOnly = true)
public class BadgeQueryService {

    private static final Set<String> STATUSES = Set.of("pending", "affected", "all");
    // « Confié à » — filtre de LIVRAISON (appartenance a un lot), orthogonal au
    // statut d'affectation. 'undelivered' = dans aucun lot (comme pickFreeSerials).
    private static final Set<String> DELIVERIES = Set.of("all", "undelivered");

    private final BadgeRepository badgeRepo;
    private final BilletRepository billetRepo;
    private final VoucherRepository voucherRepo;
    private final ModeleBilletRepository modeleRepo;
    private final EvenementRepository eventRepo;
    private final HolderRepository holderRepo;
    private final BadgeAffectationRepository affectationRepo;
    private final AccessZoneResolver zones;
    private final PosterResolver posters;
    private final BadgeProperties props;
    private final ModelClassificationService classification;
    private final VisibilityService visibility;

    public BadgeQueryService(BadgeRepository badgeRepo, BilletRepository billetRepo, VoucherRepository voucherRepo,
                             ModeleBilletRepository modeleRepo, EvenementRepository eventRepo,
                             HolderRepository holderRepo, BadgeAffectationRepository affectationRepo,
                             AccessZoneResolver zones, PosterResolver posters,
                             BadgeProperties props, ModelClassificationService classification,
                             VisibilityService visibility) {
        this.badgeRepo = badgeRepo;
        this.billetRepo = billetRepo;
        this.voucherRepo = voucherRepo;
        this.modeleRepo = modeleRepo;
        this.eventRepo = eventRepo;
        this.holderRepo = holderRepo;
        this.affectationRepo = affectationRepo;
        this.zones = zones;
        this.posters = posters;
        this.props = props;
        this.classification = classification;
        this.visibility = visibility;
    }

    // -------------------------------------------------------------- availability
    /**
     * Feature 3 — show EVERY non-paid model (invitations, VIP cards, press,
     * staff/sponsor badges …), not just the printable invitation models.
     * Paid models (Billet Gradins) stay out of this section entirely.
     *
     * <p>Chantier 3 — pour un compte NON-ADMIN, la liste est réduite deux fois :
     * aux types cochés dans ses permissions, ET aux (événement, type) sur
     * lesquels il détient un lot actif. `injectedCount` vaut alors la taille de
     * SON lot, jamais le total de l'événement : il ne peut pas déduire combien
     * d'invitations existent au-delà des siennes.</p>
     */
    public List<AvailabilityDto> availability(Integer eventId, Authentication auth) {
        List<AvailabilityProjection> rows = badgeRepo.availability(eventId).stream()
                .filter(r -> classification.isAffectable(r.getModelId()))
                .toList();

        Optional<Set<Integer>> allowedModels = visibility.allowedModels(auth);
        Optional<Map<VisibilityService.ScopeKey, Long>> scopes = visibility.scopes(auth);

        Map<String, Boolean> posterCache = new HashMap<>();

        List<AvailabilityDto> out = new ArrayList<>(rows.size());
        for (AvailabilityProjection r : rows) {
            int injected = (int) r.getInjectedCount();
            int billets = (int) r.getBilletCount();
            int vouchers = (int) r.getVoucherCount();

            if (allowedModels.isPresent()) {
                // Non-admin : type non coché -> invisible, quel que soit le type.
                if (!allowedModels.get().contains(r.getModelId())) continue;

                if (visibility.isInvitationType(r.getModelId())) {
                    // INVITATION -> visibilité par LOT. Sans lot sur ce couple
                    // (événement, type), rien à voir. Les compteurs affichés
                    // sont ceux de SON lot, jamais le total de l'événement :
                    // il ne peut pas déduire combien d'invitations existent
                    // au-delà des siennes.
                    Long taille = scopes.map(m -> m.get(
                            new VisibilityService.ScopeKey(r.getEventId(), r.getModelId()))).orElse(null);
                    if (taille == null || taille == 0) continue;
                    injected = taille.intValue();
                    billets = taille.intValue();
                    vouchers = 0;
                }
                // BADGE / KIT / ACCÈS -> permission binaire. Le type est coché,
                // donc il voit la TOTALITÉ : on garde les compteurs réels tels
                // qu'ils sortent de la requête. Aucun lot n'est consulté.
            }

            boolean hasPoster = posterCache.computeIfAbsent(
                    r.getEventTitle() == null ? "" : r.getEventTitle(), posters::exists);
            out.add(new AvailabilityDto(
                    r.getEventId(), r.getEventTitle(), r.getEventDate().toLocalDate(),
                    r.getModelId(), r.getModelName(), zones.resolve(r.getModelId()),
                    injected, billets, vouchers,
                    hasPoster,
                    // Feature 3 — printable = configured invitation model (keeps Imprimer);
                    // everything else non-paid is assign-only.
                    classification.isPrintable(r.getModelId()),
                    // Chantier 2 — la catégorie, unique source du découpage UI.
                    classification.category(r.getModelId()),
                    // « Confié à » — vrai type invitation (seul porteur de lots).
                    classification.isInvitationType(r.getModelId())));
        }
        return out;
    }

    // ------------------------------------------------------------- missing posters
    public List<MissingPosterDto> missingPosters(Authentication auth) {
        // Chantier 3 — le bandeau « affiches manquantes » est un inventaire global :
        // il révélerait des événements hors lot. Réservé à l'administrateur.
        if (!visibility.isAdmin(auth)) return List.of();
        Map<Integer, MissingPosterDto> byEvent = new LinkedHashMap<>();
        for (AvailabilityProjection r : badgeRepo.availability(null)) {
            // Posters are only used by the printable invitation-PDF layout, so a
            // "missing poster" only matters for printable models.
            if (!classification.isPrintable(r.getModelId())) continue;
            if (posters.exists(r.getEventTitle())) continue;
            int ev = r.getEventId();
            int add = (int) r.getInjectedCount();
            MissingPosterDto cur = byEvent.get(ev);
            if (cur == null) {
                byEvent.put(ev, new MissingPosterDto(ev, r.getEventTitle(), r.getEventDate().toLocalDate(), add));
            } else {
                byEvent.put(ev, new MissingPosterDto(ev, cur.eventTitle(), cur.eventDate(), cur.invitationCount() + add));
            }
        }
        return new ArrayList<>(byEvent.values());
    }

    // -------------------------------------------------------------- items page
    /**
     * Feature 2 — {@code admin} decides whether the audit fields (updatedBy /
     * updatedAt) are exposed. The columns are always selected; they are dropped
     * here, at the single mapping point, for non-ADMIN callers. The flag is
     * computed by the controller from the request's Authentication.
     */
    public PageDto<BadgeItemDto> items(int eventId, int modelId, int page, int size, String search, String status,
                                       String delivery, boolean admin, Authentication auth) {
        requireAffectable(modelId);
        String st = normalizeStatus(status);
        String dv = normalizeDelivery(delivery);
        String s = (search == null || search.isBlank()) ? null : search.trim();

        // Deux mécanismes. serialRestriction() renvoie :
        //   - vide  -> admin, OU type non-invitation dont la permission vient
        //              d'être validée : aucune restriction par numéro de série.
        //   - liste -> type invitation : uniquement les numéros de ses lots.
        // Le filtre est appliqué DANS la requête SQL, pas après : l'API ne peut
        // pas renvoyer une invitation hors lot, même par erreur de pagination.
        Optional<List<String>> scoped = visibility.serialRestriction(auth, eventId, modelId);
        if (scoped.isPresent()) {
            List<String> serials = scoped.get();
            if (serials.isEmpty()) {
                return new PageDto<>(List.of(), page, size, 0, 0);   // jamais IN ()
            }
            long total = badgeRepo.itemsCountScoped(eventId, modelId, s, st, dv, serials);
            List<BadgeItemDto> content = badgeRepo
                    .itemsScoped(eventId, modelId, s, st, dv, size, page * size, serials).stream()
                    .map(p -> toItemDto(p, admin))
                    .toList();
            int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
            return new PageDto<>(content, page, size, total, totalPages);
        }

        long total = badgeRepo.itemsCount(eventId, modelId, s, st, dv);
        List<BadgeItemDto> content = badgeRepo.items(eventId, modelId, s, st, dv, size, page * size).stream()
                .map(p -> toItemDto(p, admin))
                .toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageDto<>(content, page, size, total, totalPages);
    }

    // ------------------------------------------------------------------ counts
    public CountsDto counts(int eventId, int modelId, Authentication auth) {
        requireAffectable(modelId);
        // Pour une INVITATION, le compteur d'un non-admin porte sur SON lot
        // (X / 20), pas sur les 100 invitations de l'événement. Pour un badge,
        // serialRestriction() est vide : il voit le compteur complet du type.
        Optional<List<String>> scoped = visibility.serialRestriction(auth, eventId, modelId);
        CountsProjection c;
        if (scoped.isPresent()) {
            if (scoped.get().isEmpty()) return new CountsDto(0, 0, 0);
            c = badgeRepo.countsScoped(eventId, modelId, scoped.get());
        } else {
            c = badgeRepo.counts(eventId, modelId);
        }
        if (c == null) return new CountsDto(0, 0, 0);
        return new CountsDto(c.getAffected(), c.getPending(), c.getTotal());
    }

    private String normalizeStatus(String status) {
        if (status == null) return "pending";
        String s = status.trim().toLowerCase();
        return STATUSES.contains(s) ? s : "pending";
    }

    private String normalizeDelivery(String delivery) {
        if (delivery == null) return "all";
        String s = delivery.trim().toLowerCase();
        return DELIVERIES.contains(s) ? s : "all";
    }

    private BadgeItemDto toItemDto(BadgeItemProjection p, boolean admin) {
        java.time.LocalDateTime printedAt = p.getPrintedAt() == null ? null : p.getPrintedAt().toLocalDateTime();
        // Feature 2 — the audit trail (« Affecté par » + horodatage) is ADMIN-only.
        String updatedBy = admin ? p.getUpdatedBy() : null;
        java.time.LocalDateTime updatedAt =
                (admin && p.getUpdatedAt() != null) ? p.getUpdatedAt().toLocalDateTime() : null;
        // « Confié à » n'est pas de l'audit sensible : c'est la propriete de
        // livraison. Un non-admin ne voit de toute facon que ses propres lignes
        // (déjà restreintes par serialRestriction), donc aucune fuite inter-comptes.
        return new BadgeItemDto(p.getType(), p.getNumeroserie(), p.getCodebarre(),
                p.getHolderName(), p.getAffecteeA(), printedAt, updatedBy, updatedAt,
                p.getDeliveredTo(), p.getDeliveredToUsername(), p.getDeliveredActive());
    }

    // -------------------------------------------------------------- build records for PDF
    public BadgeRecord single(String type, String code, Authentication auth) {
        if ("voucher".equalsIgnoreCase(type)) {
            Voucher v = voucherRepo.findByCodebarre(code).or(() -> voucherRepo.findByNumeroserie(code))
                    .orElseThrow(() -> notFound(code));
            requirePrintable(v.getModelebillet());
            // Chantier 3 — un non-admin ne peut imprimer que ses propres invitations.
            visibility.requireSerialOwned(auth, v.getNumeroserie());
            return record("VOUCHER", v.getNumeroserie(), v.getCodebarre(), null, v.getEvenement(), v.getModelebillet());
        }
        Billet b = billetRepo.findByCodebarre(code).or(() -> billetRepo.findByNumeroserie(code))
                .orElseThrow(() -> notFound(code));
        requirePrintable(b.getModelebillet());
        visibility.requireSerialOwned(auth, b.getNumeroserie());
        String holder = holderRepo.findByBillet(b.getNumeroserie()).map(this::name).orElse(null);
        return record("BILLET", b.getNumeroserie(), b.getCodebarre(), holder, b.getEvenement(), b.getModelebillet());
    }

    public List<BadgeRecord> batch(int eventId, int modelId, List<String> codes, Authentication auth) {
        requirePrintable(modelId);
        // Seuls les types INVITATION sont imprimables (requirePrintable ci-dessus),
        // donc serialRestriction() renvoie toujours une liste ici pour un
        // non-admin : « Tout générer » ne porte que sur ses lots. Le PDF, le
        // poster et le ZIP ne sont pas touchés — on restreint la liste d'entrée.
        Optional<List<String>> scoped = visibility.serialRestriction(auth, eventId, modelId);
        Set<String> ownScope = scoped.map(HashSet::new).orElse(null);
        if (ownScope != null && ownScope.isEmpty()) {
            throw notFound("no scoped records for event " + eventId + " / model " + modelId);
        }
        Evenement e = eventRepo.findById(eventId).orElseThrow(() -> notFound("event " + eventId));
        ModeleBillet m = modeleRepo.findById(modelId).orElse(null);
        List<String> zoneList = zones.resolve(modelId);
        Set<String> wanted = (codes == null || codes.isEmpty()) ? null : new HashSet<>(codes);

        List<BadgeRecord> out = new ArrayList<>();
        for (BadgeItemProjection p : badgeRepo.allItems(eventId, modelId)) {
            if (ownScope != null && !ownScope.contains(p.getNumeroserie())) {
                continue;   // hors lot de l'appelant
            }
            if (wanted != null && !wanted.contains(p.getCodebarre()) && !wanted.contains(p.getNumeroserie())) {
                continue;
            }
            out.add(new BadgeRecord(p.getType(), p.getNumeroserie(), p.getCodebarre(), p.getHolderName(),
                    p.getAffecteeA(),
                    e.getTitre(), e.getDdate(), m == null ? null : m.getModele(), zoneList, null));
        }
        if (out.isEmpty()) throw notFound("no records for event " + eventId + " / model " + modelId);
        return out;
    }

    private BadgeRecord record(String type, String numeroserie, String codebarre, String holder,
                               Integer eventId, Integer modelId) {
        Evenement e = eventId == null ? null : eventRepo.findById(eventId).orElse(null);
        ModeleBillet m = modelId == null ? null : modeleRepo.findById(modelId).orElse(null);
        LocalDate date = e == null ? null : e.getDdate();
        String affectee = affecteeName(numeroserie);
        return new BadgeRecord(type, numeroserie, codebarre, holder, affectee,
                e == null ? null : e.getTitre(), date,
                m == null ? null : m.getModele(), zones.resolve(modelId), null);
    }

    private String affecteeName(String numeroserie) {
        return affectationRepo.findById(numeroserie).map(BadgeAffectation::getAffecteeA).orElse(null);
    }

    private String name(com.fih.companion.domain.Holder h) {
        String full = ((h.getFirstname() == null ? "" : h.getFirstname()) + " "
                + (h.getLastname() == null ? "" : h.getLastname())).trim();
        return full.isEmpty() ? null : full;
    }

    /** Print (Imprimer/PDF) is restricted to the configured printable invitation models. */
    private void requirePrintable(Integer modelId) {
        if (!classification.isPrintable(modelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "La génération de PDF est réservée aux modèles d'invitation imprimables.");
        }
    }

    private void requireAffectable(Integer modelId) {
        if (!classification.isAffectable(modelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Cette section ne gère que les modèles non payants.");
        }
    }

    private ResponseStatusException notFound(String what) {
        // Feature 3 — user-facing message stays generic French; the internal
        // detail (event/model/code) is logged, not returned to the client.
        ConsoleLog.log("BADGE", "not found: " + what);
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable.");
    }
}
