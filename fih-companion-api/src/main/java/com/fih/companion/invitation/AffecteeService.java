package com.fih.companion.invitation;

import com.fih.companion.badge.ModelClassificationService;
import com.fih.companion.domain.BadgeAffectation;
import com.fih.companion.domain.Billet;
import com.fih.companion.invitation.dto.AffecteeDto;
import com.fih.companion.invitation.dto.LotItemDto;
import com.fih.companion.invitation.dto.LotPreviewDto;
import com.fih.companion.invitation.dto.LotRequest;
import com.fih.companion.invitation.dto.LotResultDto;
import com.fih.companion.invitation.projection.LotRowProjection;
import com.fih.companion.repository.BadgeAffectationRepository;
import com.fih.companion.repository.BilletRepository;
import com.fih.companion.roles.VisibilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class AffecteeService {

    private static final int NUMBERING_RETRIES = 5;

    private final BadgeAffectationRepository affectationRepository;
    private final BilletRepository billetRepository;
    private final ModelClassificationService classification;
    private final VisibilityService visibility;


    @Autowired
    @Lazy
    private AffecteeService self;

    public AffecteeService(BadgeAffectationRepository affectationRepository,
                           BilletRepository billetRepository,
                           ModelClassificationService classification,
                           VisibilityService visibility) {
        this.affectationRepository = affectationRepository;
        this.billetRepository = billetRepository;
        this.classification = classification;
        this.visibility = visibility;
    }

    /**
     * Feature 2 — {@code admin} gates the audit fields (updatedBy / updatedAt).
     * An INVITATIONS-role caller must never learn who assigned a given badge.
     */
    @Transactional(readOnly = true)
    public Optional<AffecteeDto> get(String numeroserie, boolean admin, Authentication auth) {
        // Chantier 3 — un non-admin ne lit que ses propres invitations.
        visibility.requireSerialOwned(auth, numeroserie);
        return affectationRepository.findById(numeroserie).map(e -> toDto(e, admin));
    }

    // -------------------------------------------------------------- single (A)


    public AffecteeDto set(String numeroserie, String name, String updatedBy, boolean admin,
                           Authentication auth) {
        // Chantier 3 — la garde de propriété passe AVANT le contrôle write-once :
        // un utilisateur ne doit pas pouvoir déduire l'existence d'une invitation
        // hors de son lot en observant un 409 plutôt qu'un 403.
        visibility.requireSerialOwned(auth, numeroserie);
        for (int attempt = 0; attempt < NUMBERING_RETRIES; attempt++) {
            try {
                return self.setOnce(numeroserie, name, updatedBy, admin);
            } catch (DataIntegrityViolationException collision) {
                // Another write grabbed our number first — recompute and retry.
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Conflit de numérotation, veuillez réessayer.");
    }

    @Transactional
    public AffecteeDto setOnce(String numeroserie, String name, String updatedBy, boolean admin) {
        Billet billet = billetRepository.findByNumeroserie(numeroserie)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucun billet trouvé pour le numéro de série : " + numeroserie));

        if (!classification.isAffectable(billet.getModelebillet())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ce numéro de série correspond à un billet payant et ne peut pas être affecté ici.");
        }

        String base = name == null ? "" : name.trim();
        if (base.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom est obligatoire.");
        }

        // One-time guard: a row already here means the invitation was delivered.
        if (affectationRepository.existsById(numeroserie)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cette invitation est déjà affectée et ne peut plus être modifiée.");
        }

        // Numérotation SCOPÉE au couple (événement, type d'invitation) du billet :
        // « Anis » repart à -01 dès qu'on change d'événement OU de type, au lieu
        // de suivre un compteur global. L'affectation par lot reste inchangée.
        int next = nextNumberForBaseScoped(base, billet.getEvenement(), billet.getModelebillet());
        String unique = formatName(base, next, widthFor(next));

        BadgeAffectation entity = new BadgeAffectation(numeroserie, unique, updatedBy);
        return toDto(affectationRepository.save(entity), admin);
    }


    /**
     * Le « lot » historique : nommage d'une PLAGE de numéros de série avec un nom
     * de base. Rien à voir avec les CONTINGENTS du chantier 3. Inchangé pour
     * l'administrateur ; pour un non-admin, la plage est intersectée avec ses
     * propres numéros de série — il ne peut pas nommer au-delà de ses lots.
     */
    @Transactional(readOnly = true)
    public LotPreviewDto previewLot(LotRequest req, Authentication auth) {
        Lot lot = buildLot(req, auth);

        List<LotItemDto> items = new ArrayList<>(lot.eligible.size());
        List<String> assignedSerials = new ArrayList<>();
        int assignedCount = 0;
        for (Assignment a : lot.eligible) {
            boolean assigned = a.existingName != null;
            if (assigned) {
                assignedCount++;
                assignedSerials.add(a.row.getNumeroserie());
            }
            items.add(new LotItemDto(
                    a.row.getNumeroserie(), a.row.getCodebarre(),
                    a.row.getEventId(), a.row.getEventTitle(),
                    a.row.getModelId(), a.row.getModelName(),
                    a.proposedName, assigned, a.existingName));
        }

        boolean baseUsed = affectationRepository.baseNameUsed(lot.baseName);
        boolean canAssign = !lot.eligible.isEmpty() && assignedCount == 0;

        return new LotPreviewDto(
                lot.eligible.size(), assignedCount, lot.nonInvitationCount,
                baseUsed, canAssign, items, assignedSerials);
    }


    public LotResultDto assignLot(LotRequest req, String updatedBy, boolean admin, Authentication auth) {
        for (int attempt = 0; attempt < NUMBERING_RETRIES; attempt++) {
            try {
                return self.assignLotOnce(req, updatedBy, admin, auth);
            } catch (DataIntegrityViolationException collision) {
                // A name we picked was taken in parallel — recompute and retry.
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Conflit de numérotation sur le lot, veuillez réessayer.");
    }

    @Transactional
    public LotResultDto assignLotOnce(LotRequest req, String updatedBy, boolean admin, Authentication auth) {
        Lot lot = buildLot(req, auth);

        if (lot.eligible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Aucune invitation éligible dans cette plage de numéros de série.");
        }

        List<String> already = lot.eligible.stream()
                .filter(a -> a.existingName != null)
                .map(a -> a.row.getNumeroserie())
                .toList();
        if (!already.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lot bloqué : " + already.size() + " invitation(s) déjà affectée(s) dans cette plage ("
                            + String.join(", ", already) + "). Ajustez la plage.");
        }

        List<BadgeAffectation> toSave = new ArrayList<>(lot.eligible.size());
        for (Assignment a : lot.eligible) {
            toSave.add(new BadgeAffectation(a.row.getNumeroserie(), a.proposedName, updatedBy));
        }
        List<AffecteeDto> assigned = affectationRepository.saveAll(toSave).stream()
                .map(e -> toDto(e, admin)).toList();
        return new LotResultDto(assigned.size(), assigned);
    }

    @Transactional(readOnly = true)
    public String manifestCsv(String startSerie, String endSerie, Authentication auth) {
        String start = trimOr400(startSerie, "Le numéro de série de début est obligatoire.");
        String end = trimOr400(endSerie, "Le numéro de série de fin est obligatoire.");
        boolean admin = visibility.isAdmin(auth);
        StringBuilder sb = new StringBuilder("nom,numeroserie,codebarre,evenement\n");
        for (LotRowProjection r : billetRepository.findRange(start, end)) {
            if (!classification.isAffectable(r.getModelId())) continue;
            // Chantier 3 — le manifeste d'un non-admin ne contient que ses lignes.
            if (!admin && visibility.filterSerials(auth, List.of(r.getNumeroserie())).isEmpty()) continue;
            if (r.getAffecteeA() == null) continue;
            sb.append(csv(r.getAffecteeA())).append(',')
                    .append(csv(r.getNumeroserie())).append(',')
                    .append(csv(r.getCodebarre())).append(',')
                    .append(csv(r.getEventTitle())).append('\n');
        }
        return sb.toString();
    }

    @Transactional
    public void markPrinted(Collection<String> serials) {
        if (serials == null || serials.isEmpty()) return;
        affectationRepository.markPrinted(serials, LocalDateTime.now());
    }


    private Lot buildLot(LotRequest req, Authentication auth) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requête de lot manquante.");
        }
        String start = trimOr400(req.startSerie(), "Le numéro de série de début est obligatoire.");
        String end = trimOr400(req.endSerie(), "Le numéro de série de fin est obligatoire.");
        String baseName = trimOr400(req.baseName(), "Le nom de base est obligatoire.");
        if (start.compareTo(end) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le numéro de série de début doit être inférieur ou égal à celui de fin.");
        }

        List<LotRowProjection> rows = billetRepository.findRange(start, end);

        // Chantier 3 — intersection avec les numéros de série de l'appelant.
        // Pour un ADMIN, filterSerials() renvoie la liste telle quelle.
        List<String> visibles = visibility.filterSerials(
                auth, rows.stream().map(LotRowProjection::getNumeroserie).toList());
        java.util.Set<String> visibleSet = visibles == null ? null : new java.util.HashSet<>(visibles);

        List<LotRowProjection> eligibleRows = new ArrayList<>();
        int nonInvitation = 0;
        for (LotRowProjection r : rows) {
            if (visibleSet != null && !visibleSet.contains(r.getNumeroserie())) continue;
            if (classification.isAffectable(r.getModelId())) eligibleRows.add(r);
            else nonInvitation++;
        }


        int total = eligibleRows.size();
        int startNum = nextNumberForBase(baseName);
        int lastNum = startNum + Math.max(total, 1) - 1;
        int width = widthFor(lastNum);

        List<Assignment> eligible = new ArrayList<>(total);
        int i = startNum;
        for (LotRowProjection r : eligibleRows) {
            String proposed = formatName(baseName, i++, width);
            eligible.add(new Assignment(r, proposed, r.getAffecteeA()));
        }

        Lot lot = new Lot();
        lot.baseName = baseName;
        lot.eligible = eligible;
        lot.nonInvitationCount = nonInvitation;
        return lot;
    }


    private int nextNumberForBase(String base) {
        Pattern shape = Pattern.compile("^" + Pattern.quote(base) + "-(\\d+)$", Pattern.CASE_INSENSITIVE);
        int max = 0;
        for (String stored : affectationRepository.findNamesForBase(base)) {
            if (stored == null) continue;
            Matcher m = shape.matcher(stored.trim());
            if (m.matches()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return max + 1;
    }

    /**
     * Prochain numéro pour {@code base}, mais UNIQUEMENT parmi les affectations du
     * même couple (événement, type d'invitation). Deux couples différents ont donc
     * chacun leur propre séquence : « Anis » recommence à 01 à chaque nouveau
     * couple. Réservé à l'affectation « Affecté à » unitaire ; l'affectation par
     * lot passe toujours par {@link #nextNumberForBase(String)} (global, inchangé).
     */
    private int nextNumberForBaseScoped(String base, Integer eventId, Integer modelId) {
        Pattern shape = Pattern.compile("^" + Pattern.quote(base) + "-(\\d+)$", Pattern.CASE_INSENSITIVE);
        int max = 0;
        for (String stored : affectationRepository.findNamesForBaseScoped(base, eventId, modelId)) {
            if (stored == null) continue;
            Matcher m = shape.matcher(stored.trim());
            if (m.matches()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return max + 1;
    }

    private String formatName(String base, int number, int width) {
        return String.format("%s-%0" + width + "d", base, number);
    }

    private int widthFor(int number) {
        return Math.max(2, Integer.toString(Math.max(number, 1)).length());
    }

    private String trimOr400(String s, String message) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return t;
    }

    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /**
     * Feature 2 — the ONLY place an AffecteeDto is built. updatedAt/updatedBy are
     * emitted for ROLE_ADMIN and nulled for everyone else, so GET/PUT
     * /api/invitations/{numeroserie}/affectee and POST .../affectation/lot all
     * inherit the same rule without a second mechanism.
     */
    private AffecteeDto toDto(BadgeAffectation e, boolean admin) {
        return new AffecteeDto(e.getNumeroserie(), e.getAffecteeA(),
                admin ? e.getUpdatedAt() : null,
                admin ? e.getUpdatedBy() : null,
                e.getPrintedAt());
    }

    private static final class Lot {
        String baseName;
        List<Assignment> eligible;
        int nonInvitationCount;
    }

    private static final class Assignment {
        final LotRowProjection row;
        final String proposedName;
        final String existingName;
        Assignment(LotRowProjection row, String proposedName, String existingName) {
            this.row = row;
            this.proposedName = proposedName;
            this.existingName = existingName;
        }
    }
}
