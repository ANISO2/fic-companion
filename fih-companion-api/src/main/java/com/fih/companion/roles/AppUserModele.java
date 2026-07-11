package com.fih.companion.roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;
import java.util.Objects;

/**
 * Un interrupteur « à la Discord » : « cet utilisateur a le droit de VOIR ce
 * type de badge / d'invitation ». Une ligne = un type autorisé.
 *
 * <p>Absence de ligne = type invisible, côté API comme côté UI (fail-closed).</p>
 */
@Entity
@Table(name = "app_user_modele", schema = "companion")
@Getter
public class AppUserModele {

    @EmbeddedId
    private Id id;

    protected AppUserModele() {
    }

    public AppUserModele(Long appUserId, Integer modelebilletId) {
        this.id = new Id(appUserId, modelebilletId);
    }

    @Embeddable
    @Getter
    public static class Id implements Serializable {

        @Column(name = "app_user_id", nullable = false)
        private Long appUserId;

        @Column(name = "modelebillet_id", nullable = false)
        private Integer modelebilletId;

        protected Id() {
        }

        public Id(Long appUserId, Integer modelebilletId) {
            this.appUserId = appUserId;
            this.modelebilletId = modelebilletId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(appUserId, other.appUserId)
                    && Objects.equals(modelebilletId, other.modelebilletId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(appUserId, modelebilletId);
        }
    }
}
