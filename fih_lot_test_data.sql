-- =====================================================================
--  FIH Companion — jeu de donnees de TEST pour la numerotation par LOT
--  scopee au couple (evenement, type d'invitation).
--
--  Cree 3 groupes de billets d'invitation :
--     Evenement 1 x Type A : 200 billets  FIHT-E1-A-001..200
--     Evenement 1 x Type B : 200 billets  FIHT-E1-B-001..200
--     Evenement 2 x Type A : 100 billets  FIHT-E2-A-001..100
--
--  AUCUNE table n'est creee. On REUTILISE tes evenements et tes modeles
--  "invitation" reels (vente=false), et on CLONE un vrai billet comme
--  gabarit (billet%ROWTYPE) : toutes les colonnes NOT NULL heritent donc
--  de valeurs valides, meme celles non mappees par les entites JPA.
--
--  Reutiliser des modeles EXISTANTS evite aussi de devoir redemarrer l'API
--  (ModelClassificationService met la classification en cache au 1er appel).
--
--  Lancement :  psql "postgresql://user:pass@host:5432/tabase" -f fih_lot_test_data.sql
-- =====================================================================

-- ============================ 1) SEED ================================
DO $$
DECLARE
    -- ---- Volumes (modifiables) --------------------------------------
    c_n1 constant integer := 200;   -- Evenement 1 x Type A
    c_n2 constant integer := 200;   -- Evenement 1 x Type B
    c_n3 constant integer := 100;   -- Evenement 2 x Type A

    -- ---- IDs reels : auto-selectionnes. ECRASE-LES si tu veux cibler
    --      des couples precis (ex: v_event1 := 3;) ----------------------
    v_event1  integer;
    v_event2  integer;
    v_model_a integer;
    v_model_b integer;

    v_tmpl    billet%ROWTYPE;
    i         integer;
BEGIN
    -- 2 evenements distincts (les 2 plus petits references)
    SELECT reference INTO v_event1 FROM evenement ORDER BY reference LIMIT 1;
    SELECT reference INTO v_event2 FROM evenement
        WHERE reference <> v_event1 ORDER BY reference LIMIT 1;

    -- 2 modeles "invitation" NON payants (vente=false) => affectables + visibles
    -- comme "type invitation" dans le backoffice (libelle ~ 'invitation').
    SELECT reference INTO v_model_a FROM modelebillet
        WHERE vente = false AND lower(modele) LIKE '%invitation%'
        ORDER BY reference LIMIT 1;
    SELECT reference INTO v_model_b FROM modelebillet
        WHERE vente = false AND lower(modele) LIKE '%invitation%'
          AND reference <> v_model_a
        ORDER BY reference LIMIT 1;

    IF v_event1 IS NULL OR v_event2 IS NULL THEN
        RAISE EXCEPTION 'Il faut au moins 2 evenements (trouves: e1=%, e2=%). '
            'Renseigne v_event1 / v_event2 manuellement.', v_event1, v_event2;
    END IF;
    IF v_model_a IS NULL OR v_model_b IS NULL THEN
        RAISE EXCEPTION 'Il faut au moins 2 modeles invitation vente=false '
            '(trouves: a=%, b=%). Renseigne v_model_a / v_model_b manuellement.',
            v_model_a, v_model_b;
    END IF;

    -- Gabarit : n'importe quel billet reel (copie TOUTES les colonnes).
    SELECT * INTO v_tmpl FROM billet LIMIT 1;
    IF v_tmpl.numeroserie IS NULL THEN
        RAISE EXCEPTION 'Aucun billet gabarit dans la table billet.';
    END IF;

    RAISE NOTICE 'Couples de test -> E1=% , E2=% | Type A (modele)=% , Type B (modele)=%',
        v_event1, v_event2, v_model_a, v_model_b;

    -- Etat "invitation neuve, non vendue, non livree".
    -- NB: si l'une de ces colonnes est NOT NULL et refuse NULL chez toi,
    --     supprime simplement la ligne concernee (le gabarit fournira sa valeur).
    v_tmpl.activation    := false;
    v_tmpl.reservation   := false;
    v_tmpl.utilisation   := false;
    v_tmpl.vendu         := false;
    v_tmpl.etatlivraison := false;
    v_tmpl.livraison     := NULL;
    v_tmpl.vente         := NULL;

    -- ---- Groupe 1 : Evenement 1 x Type A -----------------------------
    v_tmpl.evenement    := v_event1;
    v_tmpl.modelebillet := v_model_a;
    FOR i IN 1..c_n1 LOOP
        v_tmpl.numeroserie := 'FIHT-E1-A-'    || lpad(i::text, 3, '0');
        v_tmpl.codebarre   := 'FIHT-BC-E1-A-' || lpad(i::text, 3, '0');
        INSERT INTO billet VALUES (v_tmpl.*) ON CONFLICT (numeroserie) DO NOTHING;
    END LOOP;

    -- ---- Groupe 2 : Evenement 1 x Type B -----------------------------
    v_tmpl.evenement    := v_event1;
    v_tmpl.modelebillet := v_model_b;
    FOR i IN 1..c_n2 LOOP
        v_tmpl.numeroserie := 'FIHT-E1-B-'    || lpad(i::text, 3, '0');
        v_tmpl.codebarre   := 'FIHT-BC-E1-B-' || lpad(i::text, 3, '0');
        INSERT INTO billet VALUES (v_tmpl.*) ON CONFLICT (numeroserie) DO NOTHING;
    END LOOP;

    -- ---- Groupe 3 : Evenement 2 x Type A -----------------------------
    v_tmpl.evenement    := v_event2;
    v_tmpl.modelebillet := v_model_a;
    FOR i IN 1..c_n3 LOOP
        v_tmpl.numeroserie := 'FIHT-E2-A-'    || lpad(i::text, 3, '0');
        v_tmpl.codebarre   := 'FIHT-BC-E2-A-' || lpad(i::text, 3, '0');
        INSERT INTO billet VALUES (v_tmpl.*) ON CONFLICT (numeroserie) DO NOTHING;
    END LOOP;

    RAISE NOTICE 'OK -> % + % + % billets de test inseres.', c_n1, c_n2, c_n3;
END $$;

-- ========================= 2) VERIFICATION ===========================
-- Combien de billets de test par couple (evenement, modele) ?
SELECT b.evenement AS event_id,
       b.modelebillet AS model_id,
       count(*) AS nb_billets
FROM billet b
WHERE b.numeroserie LIKE 'FIHT-%'
GROUP BY b.evenement, b.modelebillet
ORDER BY 1, 2;

-- A lancer APRES avoir affecte les lots dans le backoffice :
-- doit montrer min='...-001' pour CHAQUE couple => reset par couple confirme.
SELECT b.evenement AS event_id,
       b.modelebillet AS model_id,
       min(ba.affectee_a) AS premier_nom,
       max(ba.affectee_a) AS dernier_nom,
       count(*) AS nb_affectes
FROM badge_affectation ba
JOIN billet b ON b.numeroserie = ba.numeroserie
WHERE ba.numeroserie LIKE 'FIHT-%'
GROUP BY b.evenement, b.modelebillet
ORDER BY 1, 2;

-- =========================== 3) NETTOYAGE ============================
-- Decommente et relance ce fichier (ou copie ces 2 lignes) pour tout retirer.
-- Ordre : affectations d'abord, billets ensuite.
--
-- DELETE FROM badge_affectation WHERE numeroserie LIKE 'FIHT-%';
-- DELETE FROM billet            WHERE numeroserie LIKE 'FIHT-%';
