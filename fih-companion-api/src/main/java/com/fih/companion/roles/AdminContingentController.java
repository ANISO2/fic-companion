package com.fih.companion.roles;

import com.fih.companion.roles.dto.ContingentDto;
import com.fih.companion.roles.dto.ContingentLigneDto;
import com.fih.companion.roles.dto.CreateContingentRequest;
import com.fih.companion.roles.dto.DisponibiliteDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Les LOTS (« contingents ») : un sous-ensemble d'invitations d'un type, dans un
 * événement, affecté à un utilisateur. ROLE_ADMIN uniquement.
 *
 * <p>Le mot « contingent » évite la collision avec le « lot » existant
 * (/api/invitations/affectation/lot), qui désigne le nommage d'une plage de
 * numéros de série et reste inchangé.</p>
 */
@RestController
@RequestMapping("/api/admin/contingents")
public class AdminContingentController {

    private final RolesAdminService service;

    public AdminContingentController(RolesAdminService service) {
        this.service = service;
    }

    /** Audit complet. `userId` facultatif pour filtrer sur un bénéficiaire. */
    @GetMapping
    public List<ContingentDto> list(@RequestParam(required = false) Long userId) {
        return service.audit(userId);
    }

    /** Le détail d'un lot : qui a nommé quelle invitation, et quand. */
    @GetMapping("/{id}/lignes")
    public List<ContingentLigneDto> lines(@PathVariable Long id) {
        return service.lines(id);
    }

    /** Combien d'invitations restent libres pour ce (événement, type). */
    @GetMapping("/disponibilite")
    public DisponibiliteDto disponibilite(@RequestParam Integer eventId, @RequestParam Integer modelId) {
        return service.disponibilite(eventId, modelId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContingentDto create(@Valid @RequestBody CreateContingentRequest request, Authentication auth) {
        return service.createContingent(request, auth == null ? null : auth.getName());
    }

    /** Le bénéficiaire ne voit plus ses lignes. Les noms déjà posés restent. */
    @PostMapping("/{id}/revocation")
    public ContingentDto revoke(@PathVariable Long id, Authentication auth) {
        return service.revoke(id, auth == null ? null : auth.getName());
    }

    /** Suppression définitive. Refusée (409) si une invitation est déjà nommée. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteContingent(id);
    }
}
