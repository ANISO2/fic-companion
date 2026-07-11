package com.fih.companion.badge.projection;

import java.sql.Timestamp;

public interface BadgeItemProjection {
    String getType();
    String getNumeroserie();
    String getCodebarre();
    String getHolderName();
    /** Name from badge_affectation (null when none assigned). */
    String getAffecteeA();
    /**
     * §6 — when the badge was last printed (null when never printed).
     * Returned as java.sql.Timestamp (the raw native type, like
     * AvailabilityProjection.getEventDate()); the service converts to
     * LocalDateTime. This keeps native-projection mapping reliable.
     */
    Timestamp getPrintedAt();

    /**
     * Feature 2 — audit trail: which account set the name (badge_affectation.updated_by).
     * Selected for every caller, but only exposed to ROLE_ADMIN (see BadgeQueryService).
     */
    String getUpdatedBy();

    /** Feature 2 — when the name was set (badge_affectation.updated_at). Timestamp, as above. */
    Timestamp getUpdatedAt();

    /**
     * « Confié à » — display name of the internal user whose lot (contingent)
     * owns this serial. null when the serial is in no lot. This is DELIVERY
     * ownership (who names/delivers the ticket), NOT « affecté à » (the final
     * recipient printed on it).
     */
    String getDeliveredTo();

    /** Username of that internal user (paired with getDeliveredTo). null = no lot. */
    String getDeliveredToUsername();

    /**
     * Whether the owning lot is still active. false = revoked (the serial stays
     * reserved), null = no lot. Boolean (not boolean) so null survives mapping.
     */
    Boolean getDeliveredActive();
}
