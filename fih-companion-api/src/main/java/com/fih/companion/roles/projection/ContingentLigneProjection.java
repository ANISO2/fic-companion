package com.fih.companion.roles.projection;

import java.sql.Timestamp;

/** Une invitation d'un contingent, avec son état d'affectation. */
public interface ContingentLigneProjection {
    String getNumeroserie();
    String getCodebarre();
    String getAffecteeA();
    String getUpdatedBy();
    Timestamp getUpdatedAt();
    Timestamp getPrintedAt();
}
