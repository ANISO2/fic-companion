package com.fih.companion.roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Une invitation réservée à un contingent.
 *
 * <p>{@code numeroserie} est la CLÉ PRIMAIRE : une invitation appartient à au
 * plus UN contingent. Le SGBD garantit qu'aucun numéro de série ne peut être
 * distribué deux fois — c'est le verrou central de la fonctionnalité.</p>
 */
@Entity
@Table(name = "contingent_ligne", schema = "companion")
@Getter
public class ContingentLigne {

    @Id
    @Column(name = "numeroserie", nullable = false, length = 255)
    private String numeroserie;

    @Column(name = "contingent_id", nullable = false)
    private Long contingentId;

    protected ContingentLigne() {
    }

    public ContingentLigne(String numeroserie, Long contingentId) {
        this.numeroserie = numeroserie;
        this.contingentId = contingentId;
    }
}
