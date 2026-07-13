package com.fih.companion.badge;

import com.fih.companion.badge.dto.AvailabilityDto;
import com.fih.companion.badge.dto.BadgeItemDto;
import com.fih.companion.badge.dto.BatchRequest;
import com.fih.companion.badge.dto.MissingPosterDto;
import com.fih.companion.badge.dto.PageDto;
import com.fih.companion.invitation.AffecteeService;
import com.fih.companion.security.Roles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeQueryService query;
    private final BadgePdfService pdf;
    private final AffecteeService affectee;

    public BadgeController(BadgeQueryService query, BadgePdfService pdf, AffecteeService affectee) {
        this.query = query;
        this.pdf = pdf;
        this.affectee = affectee;
    }

    // Chantier 3 — l'Authentication est passée au service : c'est LUI qui applique
    // les droits (types cochés + lots). Les URL et les formats de réponse ne
    // changent pas : le front existant continue de fonctionner à l'identique.
    @GetMapping("/availability")
    public List<AvailabilityDto> availability(@RequestParam(required = false) Integer eventId,
                                              Authentication auth) {
        return query.availability(eventId, auth);
    }

    @GetMapping("/posters/missing")
    public List<MissingPosterDto> missingPosters(Authentication auth) {
        return query.missingPosters(auth);
    }

    @GetMapping("/items")
    public PageDto<BadgeItemDto> items(@RequestParam int eventId,
                                       @RequestParam int modelId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size,
                                       @RequestParam(required = false) String search,
                                       // Feature 2 — default shows only NOT-yet-affected entries.
                                       @RequestParam(defaultValue = "pending") String status,
                                       // « Confié à » — 'undelivered' ne garde que les invitations hors lot
                                       // (pas encore mises en contingent). 'all' par défaut.
                                       @RequestParam(defaultValue = "all") String delivery,
                                       Authentication auth) {
        // Feature 2 — the route stays open to ADMIN + INVITATIONS (both need the
        // grid); only the audit fields are role-scoped, at the DTO mapping point.
        return query.items(eventId, modelId, page, size, search, status, delivery, Roles.hasFullDataAccess(auth), auth);
    }

    @GetMapping("/counts")
    public com.fih.companion.badge.dto.CountsDto counts(@RequestParam int eventId,
                                                        @RequestParam int modelId,
                                                        Authentication auth) {
        return query.counts(eventId, modelId, auth);
    }

    @GetMapping("/single")
    public ResponseEntity<byte[]> single(@RequestParam String type, @RequestParam String code,
                                         Authentication auth) {
        BadgeRecord rec = query.single(type, code, auth);

        // Change D — block a single PDF for an invitation that has no name yet.
        if (isUnaffected(rec)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cette invitation doit d'abord \u00eatre affect\u00e9e \u00e0 un nom avant de g\u00e9n\u00e9rer le PDF.");
        }

        byte[] body = pdf.single(rec);
        affectee.markPrinted(List.of(rec.numeroserie()));
        // The single PDF is now named after the « Affecté à » value (same rule as
        // the ZIP entries), not after the code-barres.
        return pdfResponse(body, pdf.fileName(rec), MediaType.APPLICATION_PDF, null);
    }

    @PostMapping("/batch")
    public ResponseEntity<byte[]> batch(@RequestBody BatchRequest req, Authentication auth) {
        if (req.eventId() == null || req.modelId() == null) {
            return ResponseEntity.badRequest().build();
        }
        // One PDF per invitation, bundled into a single ZIP, each named after that
        // invitation's "Affect\u00e9 \u00e0" value.
        List<BadgeRecord> all = query.batch(req.eventId(), req.modelId(), req.codes(), auth);
        // query.batch(...) already throws 404 when the selection resolves to no
        // records, so `all` is non-empty here.

        // « Tout g\u00e9n\u00e9rer » must only produce badges for AFFECTED invitations:
        // an unaffected serial has no name to print, so it was landing in the ZIP
        // as a useless "SANS-NOM_<code>.pdf". Same gate as the single endpoint.
        List<BadgeRecord> printable = all.stream().filter(r -> !isUnaffected(r)).toList();
        List<String> skipped = all.stream().filter(this::isUnaffected)
                .map(BadgeRecord::numeroserie).toList();
        if (printable.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Aucune invitation affect\u00e9e dans cette s\u00e9lection : affectez d'abord un nom "
                            + "avant de g\u00e9n\u00e9rer les PDF.");
        }

        String base = "badges_" + sanitize(printable.get(0).eventTitle())
                + "_" + sanitize(printable.get(0).modelName());

        // Buffered build (small ZIP now that posters are downscaled) so we can
        // send a real Content-Length — that's what makes the browser reliably
        // show the download; the earlier streaming response had no length and
        // the large blob never triggered the client-side save.
        byte[] zip = pdf.batchZipPerAffectee(printable);
        affectee.markPrinted(printable.stream().map(BadgeRecord::numeroserie).toList());

        ResponseEntity.BodyBuilder b = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + base + ".zip\"")
                .contentLength(zip.length);
        if (!skipped.isEmpty()) {
            List<String> sample = skipped.size() > 50 ? skipped.subList(0, 50) : skipped;
            b.header("X-Skipped-Count", String.valueOf(skipped.size()));
            b.header("X-Skipped-Serials", String.join(",", sample));
        }
        return b.body(zip);
    }

    private boolean isUnaffected(BadgeRecord rec) {
        return rec.affecteeA() == null || rec.affecteeA().isBlank();
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] body, String filename, MediaType type, List<String> skipped) {
        ResponseEntity.BodyBuilder b = ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        if (skipped != null && !skipped.isEmpty()) {
            List<String> sample = skipped.size() > 50 ? skipped.subList(0, 50) : skipped;
            b.header("X-Skipped-Count", String.valueOf(skipped.size()));
            b.header("X-Skipped-Serials", String.join(",", sample));
        }
        return b.body(body);
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "badge";
        return s.replaceAll("[^a-zA-Z0-9._-]+", "_").replaceAll("_+", "_");
    }
}