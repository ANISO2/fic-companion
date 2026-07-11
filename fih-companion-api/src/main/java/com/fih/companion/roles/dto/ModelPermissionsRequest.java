package com.fih.companion.roles.dto;

import java.util.List;

/**
 * La liste COMPLÈTE des types visibles par ce compte. L'API remplace, elle
 * n'ajoute pas : envoyer une liste vide revient à tout retirer.
 */
public record ModelPermissionsRequest(List<Integer> modelIds) {
}
