package com.fih.companion.roles.projection;

import java.sql.Date;
import java.sql.Timestamp;

/** Une ligne du tableau d'audit des contingents (vue administrateur). */
public interface ContingentRowProjection {
    Long getId();
    Integer getEventId();
    String getEventTitle();
    Date getEventDate();
    Integer getModelId();
    String getModelName();
    Long getUserId();
    String getUsername();
    String getDisplayName();
    Integer getTaille();
    Long getNommees();
    Timestamp getCreatedAt();
    String getCreatedBy();
    Timestamp getRevokedAt();
    String getRevokedBy();
}
