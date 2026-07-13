package com.fih.companion.invitation;

import com.fih.companion.invitation.dto.AffecteeDto;
import com.fih.companion.invitation.dto.AffecteeRequest;
import com.fih.companion.invitation.dto.LotPreviewDto;
import com.fih.companion.invitation.dto.LotRequest;
import com.fih.companion.invitation.dto.LotResultDto;
import com.fih.companion.security.Roles;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;


@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final AffecteeService service;

    public InvitationController(AffecteeService service) {
        this.service = service;
    }

    /**
     * Feature 2 — the route stays reachable by ADMIN and INVITATIONS, but the
     * audit fields (updatedBy / updatedAt) are only returned to ADMIN.
     */
    @GetMapping("/{numeroserie}/affectee")
    public AffecteeDto get(@PathVariable String numeroserie, Authentication auth) {
        return service.get(numeroserie, Roles.hasFullDataAccess(auth), auth)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Aucun nom enregistré pour ce billet."));
    }


    @PutMapping("/{numeroserie}/affectee")
    public AffecteeDto set(@PathVariable String numeroserie,
                           @Valid @RequestBody AffecteeRequest request,
                           Authentication auth) {
        // The JWT subject is the specific account username (AdminInv1, AdminInv2,
        // or a real admin) — that is what lands in badge_affectation.updated_by.
        String updatedBy = auth == null ? null : auth.getName();
        return service.set(numeroserie, request.name(), updatedBy, Roles.hasFullDataAccess(auth), auth);
    }


    @PostMapping("/affectation/lot/preview")
    public LotPreviewDto previewLot(@Valid @RequestBody LotRequest request, Authentication auth) {
        return service.previewLot(request, auth);
    }


    @PostMapping("/affectation/lot")
    public LotResultDto assignLot(@Valid @RequestBody LotRequest request, Authentication auth) {
        String updatedBy = auth == null ? null : auth.getName();
        return service.assignLot(request, updatedBy, Roles.hasFullDataAccess(auth), auth);
    }

    /** CSV manifest (nom, numeroserie, codebarre, evenement) for an assigned range. */
    @GetMapping("/affectation/lot/manifest")
    public ResponseEntity<byte[]> manifest(@RequestParam String startSerie,
                                           @RequestParam String endSerie,
                                           Authentication auth) {
        byte[] body = service.manifestCsv(startSerie, endSerie, auth).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"manifest_lot.csv\"")
                .body(body);
    }
}