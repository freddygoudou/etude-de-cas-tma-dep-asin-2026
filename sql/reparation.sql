-- Reprise des données - inscriptions aux concours (export du 21/11/2026 8h)
-- PostgreSQL. Tout est dans une transaction et le script peut être relancé sans risque.
-- Rien n'est perdu : les lignes supprimées sont archivées, les changements notés dans reprise_journal.
-- Je traite les doublons avant les numéros : le doublon a le même numéro que l'original, pas la peine
-- de lui en donner un nouveau pour le supprimer juste après.
-- Lancement : psql -d inscriptions -f sql/reparation.sql (faire un pg_dump avant en prod)
--
-- Lokossa (INC-202) : 307 inscrits pour 300 places, aucune suppression.
--   Les 11 inscriptions IMPOTS du 19/11 entre 9h11 et 9h12 ont été acceptées pendant la lenteur
--   du service d'identification. Par ordre d'enregistrement, 4 tiennent dans la capacité, 7 sont
--   en trop (liste : vue reprise_lokossa_decision, affichée en fin de script).
--   Attention : cree_le est l'heure de fin de traitement, elle dépend surtout du temps de réponse
--   du service d'identification. Les 11 ont validé à peu près en même temps et ont tous reçu
--   une convocation.
--   Décision à demander à la Direction des concours, dans l'ordre de préférence :
--     1. ajouter 7 places à Lokossa (une salle de plus) : UPDATE centres SET capacite = 307 WHERE code = 'L';
--     2. sinon proposer aux 7 un transfert vers un centre qui a de la place (Abomey, Porto-Novo),
--        avec nouvelle convocation ;
--     3. annuler 7 inscriptions : déconseillé, les candidats ont été confirmés et l'erreur vient de la plateforme.
--
-- Qui prévenir :
--   - Direction des concours : valider la reprise, décider pour Lokossa, valider les messages aux candidats ;
--   - chef de centre de Lokossa : 7 candidats en surnombre, numéros L-0299 à L-0307 changés ;
--   - chef de centre de Parakou (INC-201) : P-0464 n'est plus en double, le second candidat passe en P-0614 ;
--   - chefs de centre de Cotonou et Porto-Novo : numéros changés et convocations en double annulées ;
--   - les candidats renumérotés (nouvelle convocation) et les candidats inscrits en double
--     (une seule convocation reste valable) : vue reprise_candidats_a_prevenir, affichée en fin de script ;
--   - le support, pour répondre aux appels ;
--   - l'exploitation : contraintes posées, l'ancienne appli recevra des erreurs au lieu de créer des doublons.

\set ON_ERROR_STOP on
BEGIN;

SELECT pg_advisory_xact_lock(hashtext('reprise_inscriptions_2026_11'));

-- 0. tables de suivi
CREATE TABLE IF NOT EXISTS reprise_journal (
  id             BIGSERIAL PRIMARY KEY,
  action         VARCHAR(40)  NOT NULL,   -- doublon_supprime | numero_reattribue
  inscription_id INTEGER      NOT NULL,
  npi            VARCHAR(10)  NOT NULL,
  concours_id    INTEGER      NOT NULL,
  centre_id      INTEGER      NOT NULL,
  ancien_numero  VARCHAR(6),
  nouveau_numero VARCHAR(6),
  conserve_id    INTEGER,                 -- inscription conservée (cas d'un doublon)
  fait_le        TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
  CONSTRAINT uq_reprise_journal UNIQUE (action, inscription_id)
);

CREATE TABLE IF NOT EXISTS reprise_inscriptions_supprimees (
  id            INTEGER PRIMARY KEY,
  concours_id   INTEGER      NOT NULL,
  centre_id     INTEGER      NOT NULL,
  npi           VARCHAR(10)  NOT NULL,
  numero_table  VARCHAR(6)   NOT NULL,
  statut        VARCHAR(30)  NOT NULL,
  cree_le       TIMESTAMP    NOT NULL,
  supprimee_le  TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
  motif         VARCHAR(100) NOT NULL
);

-- 1. doubles inscriptions (INC-203) : on garde la plus ancienne
CREATE TEMP TABLE tmp_doublons ON COMMIT DROP AS
SELECT id, conserve_id
FROM (
  SELECT id,
         first_value(id) OVER w AS conserve_id,
         row_number()    OVER w AS rang
  FROM inscriptions
  WINDOW w AS (PARTITION BY npi, concours_id ORDER BY cree_le, id)
) t
WHERE rang > 1;

INSERT INTO reprise_inscriptions_supprimees (id, concours_id, centre_id, npi, numero_table, statut, cree_le, motif)
SELECT i.id, i.concours_id, i.centre_id, i.npi, i.numero_table, i.statut, i.cree_le,
       'Doublon de l''inscription ' || d.conserve_id || ' (INC-203)'
FROM inscriptions i JOIN tmp_doublons d ON d.id = i.id
ON CONFLICT (id) DO NOTHING;

INSERT INTO reprise_journal (action, inscription_id, npi, concours_id, centre_id, ancien_numero, conserve_id)
SELECT 'doublon_supprime', i.id, i.npi, i.concours_id, i.centre_id, i.numero_table, d.conserve_id
FROM inscriptions i JOIN tmp_doublons d ON d.id = i.id
ON CONFLICT (action, inscription_id) DO NOTHING;

DELETE FROM inscriptions i USING tmp_doublons d WHERE i.id = d.id;

-- 2. numéros en double (INC-201) : la plus ancienne garde le numéro, les autres prennent max + 1, max + 2...
WITH rangs AS (
  SELECT id, centre_id, numero_table, cree_le,
         row_number() OVER (PARTITION BY centre_id, numero_table ORDER BY cree_le, id) AS rang
  FROM inscriptions
),
a_renumeroter AS (
  SELECT r.id, r.centre_id, r.numero_table AS ancien,
         row_number() OVER (PARTITION BY r.centre_id ORDER BY r.cree_le, r.id) AS k
  FROM rangs r
  WHERE r.rang > 1
),
maximum AS (
  SELECT centre_id, max(CAST(substring(numero_table FROM 3) AS INTEGER)) AS m
  FROM inscriptions GROUP BY centre_id
),
maj AS (
  UPDATE inscriptions i
  SET numero_table = ce.code || '-' || lpad(CAST(mx.m + a.k AS TEXT), 4, '0')
  FROM a_renumeroter a
  JOIN maximum mx ON mx.centre_id = a.centre_id
  JOIN centres ce ON ce.id = a.centre_id
  WHERE i.id = a.id
  RETURNING i.id, i.npi, i.concours_id, i.centre_id, a.ancien, i.numero_table AS nouveau
)
INSERT INTO reprise_journal (action, inscription_id, npi, concours_id, centre_id, ancien_numero, nouveau_numero)
SELECT 'numero_reattribue', id, npi, concours_id, centre_id, ancien, nouveau FROM maj
ON CONFLICT (action, inscription_id) DO NOTHING;

-- 3. contraintes (protègent aussi tant que l'ancienne appli tourne)

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_inscriptions_centre_numero') THEN
    ALTER TABLE inscriptions ADD CONSTRAINT uq_inscriptions_centre_numero UNIQUE (centre_id, numero_table);
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_inscriptions_npi_concours') THEN
    ALTER TABLE inscriptions ADD CONSTRAINT uq_inscriptions_npi_concours UNIQUE (npi, concours_id);
  END IF;
END $$;

-- même jour : date d'épreuve recopiée par trigger, l'ancienne appli n'a rien à changer
ALTER TABLE inscriptions ADD COLUMN IF NOT EXISTS date_epreuve DATE;

CREATE OR REPLACE FUNCTION inscriptions_date_epreuve() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  SELECT c.date_epreuve INTO NEW.date_epreuve FROM concours c WHERE c.id = NEW.concours_id;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_inscriptions_date_epreuve ON inscriptions;
CREATE TRIGGER trg_inscriptions_date_epreuve
  BEFORE INSERT OR UPDATE OF concours_id ON inscriptions
  FOR EACH ROW EXECUTE FUNCTION inscriptions_date_epreuve();

UPDATE inscriptions i SET date_epreuve = c.date_epreuve
FROM concours c WHERE c.id = i.concours_id AND i.date_epreuve IS DISTINCT FROM c.date_epreuve;
ALTER TABLE inscriptions ALTER COLUMN date_epreuve SET NOT NULL;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_inscriptions_npi_jour') THEN
    ALTER TABLE inscriptions ADD CONSTRAINT uq_inscriptions_npi_jour UNIQUE (npi, date_epreuve);
  END IF;
END $$;

-- capacité (INC-202), sous verrou du centre. Lokossa (307/300) refusera tout : normal
CREATE OR REPLACE FUNCTION inscriptions_capacite() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_capacite INTEGER;
  v_inscrits INTEGER;
BEGIN
  SELECT capacite INTO v_capacite FROM centres WHERE id = NEW.centre_id FOR UPDATE;
  SELECT count(*) INTO v_inscrits FROM inscriptions WHERE centre_id = NEW.centre_id;
  IF v_inscrits >= v_capacite THEN
    RAISE EXCEPTION 'Ce centre d''examen est complet.' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_inscriptions_capacite ON inscriptions;
CREATE TRIGGER trg_inscriptions_capacite
  BEFORE INSERT OR UPDATE OF centre_id ON inscriptions
  FOR EACH ROW EXECUTE FUNCTION inscriptions_capacite();

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inscriptions_numero_format') THEN
    ALTER TABLE inscriptions ADD CONSTRAINT ck_inscriptions_numero_format
      CHECK (numero_table ~ '^[A-Z]-[0-9]{4}$');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inscriptions_statut') THEN
    ALTER TABLE inscriptions ADD CONSTRAINT ck_inscriptions_statut
      CHECK (statut IN ('validee', 'en_attente_verification'));
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_candidats_npi') THEN
    ALTER TABLE candidats ADD CONSTRAINT uq_candidats_npi UNIQUE (npi);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_centres_code') THEN
    ALTER TABLE centres ADD CONSTRAINT uq_centres_code UNIQUE (code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_concours_code') THEN
    ALTER TABLE concours ADD CONSTRAINT uq_concours_code UNIQUE (code);
  END IF;
END $$;

-- liste des inscrits (INC-207)
CREATE INDEX IF NOT EXISTS ix_inscriptions_centre_concours ON inscriptions (centre_id, concours_id);

-- id générés par la base pour les nouvelles lignes (la correction ne fournit pas l'id)
CREATE SEQUENCE IF NOT EXISTS inscriptions_id_seq OWNED BY inscriptions.id;
SELECT setval('inscriptions_id_seq', GREATEST(
  (SELECT COALESCE(max(id), 0) FROM inscriptions),
  (SELECT COALESCE(max(id), 0) FROM reprise_inscriptions_supprimees),
  (SELECT last_value FROM inscriptions_id_seq), 1));
ALTER TABLE inscriptions ALTER COLUMN id SET DEFAULT nextval('inscriptions_id_seq');

CREATE SEQUENCE IF NOT EXISTS candidats_id_seq OWNED BY candidats.id;
SELECT setval('candidats_id_seq', GREATEST(
  (SELECT COALESCE(max(id), 0) FROM candidats),
  (SELECT last_value FROM candidats_id_seq), 1));
ALTER TABLE candidats ALTER COLUMN id SET DEFAULT nextval('candidats_id_seq');

-- 4. Lokossa : liste pour la décision
CREATE OR REPLACE VIEW reprise_lokossa_decision AS
SELECT row_number() OVER (ORDER BY i.cree_le, i.id)                    AS rang_arrivee,
       i.id                                                             AS inscription_id,
       co.code                                                          AS concours,
       i.npi, ca.nom, ca.prenoms,
       i.numero_table,
       i.cree_le AT TIME ZONE 'UTC' AT TIME ZONE 'Africa/Porto-Novo'    AS inscrit_le_heure_benin,
       CASE WHEN row_number() OVER (ORDER BY i.cree_le, i.id) <= ce.capacite
            THEN 'dans la capacité' ELSE 'EN SURNOMBRE : décision à prendre' END AS situation
FROM inscriptions i
JOIN centres ce  ON ce.id = i.centre_id
JOIN concours co ON co.id = i.concours_id
LEFT JOIN candidats ca ON ca.npi = i.npi
WHERE ce.code = 'L';

-- 5. candidats à prévenir
CREATE OR REPLACE VIEW reprise_candidats_a_prevenir AS
SELECT j.action,
       CASE j.action
         WHEN 'numero_reattribue' THEN 'Nouvelle convocation : numéro ' || j.ancien_numero || ' remplacé par ' || j.nouveau_numero
         WHEN 'doublon_supprime'  THEN 'Convocation en double annulée ; seule reste valable celle de l''inscription ' || j.conserve_id
       END AS message,
       j.npi, ca.nom, ca.prenoms, co.code AS concours, ce.nom AS centre,
       j.ancien_numero, j.nouveau_numero, j.inscription_id
FROM reprise_journal j
JOIN concours co ON co.id = j.concours_id
JOIN centres ce  ON ce.id = j.centre_id
LEFT JOIN candidats ca ON ca.npi = j.npi;

COMMIT;

-- contrôles : tout à 0 sauf la surréservation de Lokossa
SELECT 'numeros_en_double' AS controle, count(*) AS nb FROM (
  SELECT 1 FROM inscriptions GROUP BY centre_id, numero_table HAVING count(*) > 1) t
UNION ALL
SELECT 'inscriptions_en_double', count(*) FROM (
  SELECT 1 FROM inscriptions GROUP BY npi, concours_id HAVING count(*) > 1) t
UNION ALL
SELECT 'centres_en_surreservation', count(*) FROM centres ce
  WHERE (SELECT count(*) FROM inscriptions i WHERE i.centre_id = ce.id) > ce.capacite
UNION ALL
SELECT 'journal_numeros_reattribues', count(*) FROM reprise_journal WHERE action = 'numero_reattribue'
UNION ALL
SELECT 'journal_doublons_supprimes', count(*) FROM reprise_journal WHERE action = 'doublon_supprime';

-- Lokossa : inscriptions en surnombre, décision à demander
SELECT rang_arrivee, inscription_id, concours, npi, nom, prenoms, numero_table, inscrit_le_heure_benin, situation
FROM reprise_lokossa_decision
WHERE situation <> 'dans la capacité'
ORDER BY rang_arrivee;

-- candidats à prévenir
SELECT action, message, npi, nom, prenoms, concours, centre
FROM reprise_candidats_a_prevenir
ORDER BY centre, action, ancien_numero;
