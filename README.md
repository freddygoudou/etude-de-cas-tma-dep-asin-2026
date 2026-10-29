# Étude de cas TMA DEP/ASIN 2026 - inscriptions aux concours

Java 21 / Spring Boot 3.5 (Spring Data JPA), PostgreSQL.

## Contenu du dépôt

| Question | Fichier |
|---|---|
| 1. Priorisation | `INTERVENTION.md` |
| 2. Analyse du journal | `analyser-journal/AnalyseJournal.java` |
| 3. Causes racines et liens entre incidents | `CAUSES_RACINES.md` |
| 4. Correction et tests | `correction/` |
| 5. Reprise des données | `sql/reparation.sql` |
| 6. Rapport d'incident | `RAPPORT_INC-206.md` |
| Bonus (incident non signalé) | dernière section de `CAUSES_RACINES.md` |

Les fichiers fournis (`journal_production.log` et `base_inscriptions.sql`) ne sont pas dans le dépôt : ils
contiennent des données personnelles. Les commandes ci-dessous prennent leur chemin en paramètre.

Prérequis : Java 21 ou plus, Maven 3.9 ou plus, PostgreSQL (`psql`). Docker est facultatif (voir les tests).

## 2. Analyse du journal

```bash
java analyser-journal/AnalyseJournal.java <chemin>/journal_production.log
```

Un seul fichier, sans dépendance, lancé directement par `java`. Les heures sont affichées en heure du Bénin.
Le programme sort :
- les numéros de table attribués plusieurs fois ;
- les inscriptions en double ;
- la période où le service d'identification a mis 5 s ou plus, et le nombre de requêtes touchées ;
- les refus à tort le soir de la clôture du Trésor ;
- les candidats refusés à tort pour l'âge ;
- la durée moyenne et maximale de la liste des inscrits par centre ;
- en plus, les recherches suspectes (bonus).

## 4. Correction et tests

```bash
cd correction
mvn test
```

30 tests. Ils n'ont besoin d'aucune base : chaque lancement crée une base H2 en mémoire
(`src/test/resources/schema.sql`). Si Docker tourne, les tests de concurrence sont aussi joués sur un vrai
PostgreSQL 16 (Testcontainers) ; sinon ils sont ignorés.

| Test | Ce qu'il vérifie |
|---|---|
| `controller/InscriptionControllerConcurrenceTest` | Requêtes HTTP simultanées : 40 sur un centre de 10 places, 10 clics identiques, même NPI sur deux centres et deux concours du même jour, 100 sur 3 centres. |
| `controller/InscriptionControllerConcurrencePostgresTest` | Les mêmes, sur PostgreSQL. |
| `controller/InscriptionControllerTest` | Parcours complet : service d'identification à 8 s (réponse « en attente » en moins de 5,8 s), NPI inconnu, âge, clôture, même jour, NPI mal formé. |
| `service/impl/IdentificationServiceImplTest` | Délai de 5 s du client d'identification. |
| `regle/RegleAgeTest`, `regle/RegleClotureTest` | Règles d'âge et de clôture corrigées. |

Principe de la correction (`service/impl/InscriptionServiceImpl`) :
- l'appel au service d'identification est fait avant la transaction et limité à 5 s ; au-delà, l'inscription
  est enregistrée `en_attente_verification` ;
- les étapes 4, 5, 6 et 8 sont faites dans une seule transaction, avec un verrou sur le centre
  (`@Lock(PESSIMISTIC_WRITE)` dans `CentreRepository`) ;
- des contraintes uniques en base servent de filet de sécurité ;
- âge : année du concours - année de naissance <= 35 ; clôture : comparaison en UTC.

Lancer l'application sur une base PostgreSQL déjà réparée (question 5) :

```bash
cd correction
DB_URL=jdbc:postgresql://localhost:5432/inscriptions DB_USER=postgres DB_PASSWORD=... IDENTITE_URL=http://... mvn spring-boot:run
```

## 5. Reprise des données

Le script est écrit pour PostgreSQL et se lance avec `psql`. Pour le tester sans toucher à une base
existante :

```bash
createdb -h localhost -U postgres inscriptions_test
psql -h localhost -U postgres -d inscriptions_test -f <chemin>/base_inscriptions.sql
psql -h localhost -U postgres -d inscriptions_test -f sql/reparation.sql
psql -h localhost -U postgres -d inscriptions_test -f sql/reparation.sql
dropdb -h localhost -U postgres inscriptions_test
```

Le second passage doit afficher les mêmes chiffres que le premier (script rejouable). À la fin, le script
affiche les contrôles (plus aucun numéro ni inscription en double), la liste des inscriptions en surnombre à
Lokossa et les candidats à prévenir. La décision à demander pour Lokossa et la liste des personnes à prévenir
sont en tête du script.

Sur une base de production : faire un `pg_dump` avant.
