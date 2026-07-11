package com.fih.companion.roles.projection;

/** Un (événement, modèle) sur lequel un utilisateur détient un contingent actif. */
public interface ScopeProjection {
    Integer getEventId();
    Integer getModelId();
    Long getTaille();
}
