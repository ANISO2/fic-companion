package com.fih.companion.badge.dto;

import java.time.LocalDateTime;


/**
 * Feature 2 — {@code updatedBy} / {@code updatedAt} are the audit trail of who
 * assigned this invitation and when. They are populated ONLY for ROLE_ADMIN
 * callers; for ROLE_INVITATIONS they are always null (see BadgeQueryService).
 *
 * <p>« Confié à » — {@code deliveredTo} / {@code deliveredToUsername} name the
 * internal user whose lot (contingent) owns this serial, and
 * {@code deliveredActive} says whether that lot is still active. All three are
 * null when the serial belongs to no lot. This is DELIVERY OWNERSHIP, distinct
 * from {@code affecteeA} (« affecté à », the final recipient printed on the
 * ticket).</p>
 */
public record BadgeItemDto(
        String type, String numeroserie, String codebarre,
        String holderName, String affecteeA, LocalDateTime printedAt,
        String updatedBy, LocalDateTime updatedAt,
        String deliveredTo, String deliveredToUsername, Boolean deliveredActive
) {
}
