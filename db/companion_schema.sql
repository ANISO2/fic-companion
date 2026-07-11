-- =============================================================================
--  FIC Companion — « Gestion des rôles »
--  Script de création MANUEL. À jouer UNE FOIS, avant le premier démarrage de
--  l'API avec cette version. Il est IDEMPOTENT : le rejouer ne casse rien.
--
--  Ce script NE CRÉE, NE MODIFIE ET NE SUPPRIME AUCUN OBJET DU SCHÉMA `public`.
--  Tout vit dans un schéma dédié `companion`. La base billetterie légataire
--  reste strictement en lecture, à l'exception de `public.badge_affectation`
--  qui était déjà la seule table écrite par l'application.
--
--  Usage :
--    psql -U postgres -d 'billeteire-fic-2026' -f companion_schema.sql
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS companion;

-- -----------------------------------------------------------------------------
-- app_user — les comptes créés depuis le backoffice.
-- Remplace à terme fih.security.invitations-accounts (qui reste en repli).
-- Le mot de passe est HACHÉ (BCrypt). La table `public.utilisateur` légataire,
-- qui stocke en clair, n'est pas touchée.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companion.app_user (
    id            bigserial     PRIMARY KEY,
    username      varchar(255)  NOT NULL,
    password_hash varchar(255)  NOT NULL,
    display_name  varchar(255)  NOT NULL,
    enabled       boolean       NOT NULL DEFAULT true,
    created_at    timestamp     NOT NULL DEFAULT now(),
    created_by    varchar(255)
);

-- Unicité insensible à la casse : AuthService compare en equalsIgnoreCase.
CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_username
    ON companion.app_user (lower(username));

-- -----------------------------------------------------------------------------
-- app_user_modele — les interrupteurs « à la Discord ».
-- Une ligne = « cet utilisateur a le droit de VOIR ce type de badge ».
-- Pas de FK vers public.modelebillet : on ne pose aucune contrainte sur la
-- base légataire. L'ID est validé côté application.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companion.app_user_modele (
    app_user_id     bigint  NOT NULL REFERENCES companion.app_user (id) ON DELETE CASCADE,
    modelebillet_id integer NOT NULL,
    CONSTRAINT pk_app_user_modele PRIMARY KEY (app_user_id, modelebillet_id)
);

-- -----------------------------------------------------------------------------
-- contingent — un sous-ensemble d'invitations d'UN type, dans UN événement,
-- affecté à UN utilisateur. (Le mot « lot » est déjà pris par le nommage par
-- plage de numéros de série : AffecteeService.assignLot().)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companion.contingent (
    id              bigserial    PRIMARY KEY,
    evenement_id    integer      NOT NULL,
    modelebillet_id integer      NOT NULL,
    app_user_id     bigint       NOT NULL REFERENCES companion.app_user (id),
    taille          integer      NOT NULL,
    created_at      timestamp    NOT NULL DEFAULT now(),
    created_by      varchar(255),
    revoked_at      timestamp,
    revoked_by      varchar(255)
);

CREATE INDEX IF NOT EXISTS ix_contingent_user
    ON companion.contingent (app_user_id);
CREATE INDEX IF NOT EXISTS ix_contingent_event_model
    ON companion.contingent (evenement_id, modelebillet_id);

-- -----------------------------------------------------------------------------
-- contingent_ligne — les numéros de série réservés au contingent.
--
-- `numeroserie` est la CLÉ PRIMAIRE, donc unique sur toute la table : une
-- invitation appartient à au plus UN contingent. Deux utilisateurs ne peuvent
-- jamais tomber sur la même ligne.
--
-- Aucune FK vers public.billet : pas de contrainte posée sur la base légataire.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companion.contingent_ligne (
    numeroserie    varchar(255) PRIMARY KEY,
    contingent_id  bigint       NOT NULL REFERENCES companion.contingent (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_contingent_ligne_contingent
    ON companion.contingent_ligne (contingent_id);

-- =============================================================================
--  SEED — reprise des comptes AdminInv1 / AdminInv2 de application.yml.
--
--  Les hachages ci-dessous correspondent aux mots de passe par défaut :
--      AdminInv1 -> Inv$admin+2026
--      AdminInv2 -> KLOP$ain+2026
--
--  Les deux comptes n'ont AUCUN modèle coché au départ : à la première
--  connexion ils ne voient RIEN tant que l'admin ne leur a pas donné des
--  types et des contingents. C'est volontaire (fail-closed).
--
--  Tant qu'ils ne sont pas ici, `fih.security.invitations-accounts` continue
--  de fonctionner en repli : rien ne casse en production.
-- =============================================================================
INSERT INTO companion.app_user (username, password_hash, display_name, enabled, created_by)
VALUES
  ('AdminInv1', '$2a$10$g.q7PnVWPhLAAf7I9qK7QOaTb3UFTmOwdOhbyPGtS6/TTxK9PPq9G', 'Invitations & Badges 1', true, 'seed'),
  ('AdminInv2', '$2a$10$fjDT7zGUJkDmrS.dMhlSeuS7XDHcD2CjqxXCT7ktvcj5N/yWiAnSW', 'Invitations & Badges 2', true, 'seed')
ON CONFLICT DO NOTHING;

-- Ces hachages BCrypt (coût 10) sont RÉELS et correspondent bien aux deux mots
-- de passe ci-dessus. Changez-les depuis le backoffice après la première
-- connexion (Administration → Utilisateurs → Réinitialiser le mot de passe).
--
-- Si vous ne voulez PAS de seed, commentez le bloc INSERT ci-dessus : les
-- comptes de application.yml continueront de fonctionner en repli.

-- =============================================================================
--  (OPTIONNEL, recommandé) Rôle applicatif à droits réduits.
--
--  Aujourd'hui l'API se connecte en `postgres` superutilisateur : rien
--  n'empêche techniquement un UPDATE sur public.billet. Les lignes ci-dessous
--  créent un rôle qui ne PEUT PAS écrire ailleurs que dans badge_affectation
--  et dans le schéma companion. Le verrou est alors au niveau du SGBD, pas
--  d'une annotation Hibernate @Immutable contournable.
--
--  À exécuter en superutilisateur. Ensuite, pointez FIH_DB_USER / FIH_DB_PASSWORD
--  sur ce rôle. Décommentez si vous le souhaitez.
-- =============================================================================
-- CREATE ROLE fic_app LOGIN PASSWORD 'changez-moi';
-- GRANT USAGE ON SCHEMA public TO fic_app;
-- GRANT SELECT ON ALL TABLES IN SCHEMA public TO fic_app;
-- GRANT INSERT, UPDATE ON public.badge_affectation TO fic_app;
-- GRANT USAGE, CREATE ON SCHEMA companion TO fic_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA companion TO fic_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA companion TO fic_app;

-- =============================================================================
--  VÉRIFICATIONS POST-INSTALLATION
-- =============================================================================
-- \dt companion.*
-- SELECT id, username, display_name, enabled FROM companion.app_user;
-- SELECT count(*) FROM companion.contingent;
