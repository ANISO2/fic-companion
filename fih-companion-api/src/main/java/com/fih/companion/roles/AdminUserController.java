package com.fih.companion.roles;

import com.fih.companion.roles.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestion des comptes et de leurs droits. ROLE_ADMIN uniquement — la règle est
 * posée dans SecurityConfig sur /api/admin/**, elle n'est pas dupliquée ici.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final RolesAdminService service;

    public AdminUserController(RolesAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<AppUserDto> list() {
        return service.listUsers();
    }

    /** Les 35 types de badge/invitation, pour la grille d'interrupteurs. */
    @GetMapping("/model-options")
    public List<ModelOptionDto> modelOptions() {
        return service.modelOptions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppUserDto create(@Valid @RequestBody CreateUserRequest request, Authentication auth) {
        return service.createUser(request, auth == null ? null : auth.getName());
    }

    /** Nom affiché + activation/désactivation. */
    @PutMapping("/{id}")
    public AppUserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.updateUser(id, request);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request);
    }

    /** Remplace la liste COMPLÈTE des types visibles par ce compte. */
    @PutMapping("/{id}/models")
    public AppUserDto setModels(@PathVariable Long id, @RequestBody ModelPermissionsRequest request) {
        return service.setModelPermissions(id, request);
    }

    /** Refusé (409) tant que le compte détient un lot actif. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteUser(id);
    }
}
