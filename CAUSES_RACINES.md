# Causes racines

Chiffres obtenus avec `java analyser-journal/AnalyseJournal.java journal_production.log`. Heures en heure du Bénin.

## En résumé

Trois défauts expliquent presque tout :

1. Le client HTTP vers le service d'identification est en config par défaut, donc sans timeout (INC-206).
2. L'inscription fait « je lis, je contrôle, j'appelle le service, j'écris » sans verrou ni contrainte
   d'unicité. Deux requêtes simultanées font les mêmes constats (INC-201, 202, 203).
3. Les dates : clôture UTC comparée à l'heure locale (INC-204), âge calculé en jours / 365 (INC-205).

Le lien entre les deux premiers : la lenteur du service ne crée pas le bug de concurrence, elle l'agrandit.
Normalement la fenêtre entre contrôle et écriture fait 0,2 s ; le 19/11 elle a fait jusqu'à 60 s. Le bug
existe quand même hors période de lenteur : N-0574 a été attribué deux fois le 18/11 à 16h42, sur deux
requêtes de 258 et 279 ms (double clic rapide).

```
pas de timeout ──> INC-206 (30 min de blocage)
      │                 └──> les candidats recliquent ──> INC-203
      └──> fenêtre de concurrence de 60 s
                ├──> INC-201 numéro = MAX+1 lu en même temps
                ├──> INC-202 COUNT < capacité lu en même temps
                └──> INC-203 « déjà inscrit ? » lu en même temps
```

## INC-206 (Exploitation)

> « Le 19 novembre au matin, pendant une demi-heure, plus personne ne pouvait s’inscrire ni consulter sa convocation : les pages tournaient sans fin. »

Entre 09:09:48 et 09:39:49, 60 appels `GET /identite/...` ont duré de 5,2 à 61,7 s. Ils concernent 41
inscriptions et 19 convocations. Pendant ce temps `/api/centres` et `/api/concours` répondaient en moins de
40 ms : la base et le serveur allaient bien, c'est uniquement l'attente du service externe.

Cause : pas de timeout sur le client HTTP, et la règle « au-delà de 5 s on enregistre en attente de
vérification » n'est pas codée. D'ailleurs les 6400 inscriptions de la base sont toutes `validee`, aucune en
attente.

Aggravant : la convocation rappelle le service d'identification juste pour afficher le nom, alors qu'il est
déjà dans `candidats`.

## INC-201 (Chef du centre de Parakou)

> « Deux candidats ont reçu le même numéro de table, P-0464. »

10 numéros attribués 32 fois au total : P-0464 (x2), C-3305, C-3306, C-3308, C-3311, C-3313 (x3), C-3309
(x2), L-0297 (x5), L-0298 (x6), N-0574 (x2). Tous pendant la lenteur sauf N-0574.

P-0464 : NPI 3076016910 à 09:21:39 et NPI 7984581920 à 09:21:47, deux requêtes de 22 et 33 s.

Cause : étape 6, « plus grand numéro du centre + 1 », calculé avant l'appel au service et écrit après, sans
verrou et sans contrainte unique.

## INC-202 (Direction des concours)

> « Le centre de Lokossa compte 307 inscrits pour 300 places. »

À 09:11 Lokossa a 296 inscrits. Entre 09:11:23 et 09:12:43, 11 inscriptions IMPOTS sont acceptées, toutes
lancées pendant la lenteur (20 à 62 s chacune). Les refus « centre complet » n'arrivent qu'à partir de
09:11:57. Ces 11 inscriptions ont aussi les numéros L-0297 et L-0298 : c'est le même bug que INC-201.

Cause : étape 5, même problème de lecture concurrente. 4 inscriptions tenaient dans la capacité, 7 sont en
trop.

## INC-203 (Candidat)

> « La page ne répondait pas : j’ai cliqué une seconde fois sur “Valider”. J’ai maintenant deux convocations. »

7 candidats inscrits deux fois au même concours, à chaque fois depuis la même IP à quelques secondes
d'écart. 6 cas le 19/11 entre 09:13 et 09:34, un le 18/11 (N-0574). Le doublon reçoit le même numéro que
l'original, donc il alimente aussi INC-201.

Cause : étape 4 non atomique, pas de `UNIQUE (npi, concours_id)`, et le bouton Valider reste cliquable.

## INC-204 (Support)

> « Le 20 novembre, jour de la clôture du concours du Trésor fixée à 23 h 59, les candidats ont reçu “Inscriptions closes” dès 22 h 59. »

Premier refus « Inscriptions closes » le 20/11 à 22:59:05 au lieu de 23:59. 147 refus à tort pour 100
candidats différents, puis 80 refus normaux après 23:59.

Cause : `concours.cloture` vaut `2026-11-20 22:59:00` en UTC, soit 23:59 au Bénin. Le code la compare à
l'heure du serveur, qui est réglé sur l'heure du Bénin, sans conversion : une heure d'avance. DOUANES et
IMPOTS fermeront aussi à 22:59 le 30/11 si on ne corrige pas.

## INC-205 (Candidat)

> « Je suis né le 20 juin 1991 : j’aurai 35 ans au 31 décembre 2026. L’inscription m’a répondu que je dépassais l’âge limite. »

78 refus pour l'âge dont 36 à tort : 34 personnes nées en 1991 (35 ans au 31/12/2026) et 2 nées en janvier
1992 (34 ans). Le candidat qui a signalé (né le 20/06/1991, NPI 9269739419) a été refusé le 19/11 à 14:22.

Cause : jours / 365 > 35.
- Le résultat est un âge à virgule : 35 ans et demi donne 35,55, refusé, alors que la règle porte sur l'âge
  révolu.
- Diviser par 365 ignore les 9 années bissextiles de 1992 à 2024 : quelqu'un né le 01/01/1992 sort à 35,02.

Correction : âge au 31/12 = année du concours - année de naissance, éligible si <= 35.

## INC-207 (Direction des concours)

> « La liste des inscrits du centre de Cotonou met plus de 40 secondes à s’afficher. »

Cotonou : 25,4 s en moyenne, 46,5 s au max (2408 lignes), et ça augmente avec le nombre d'inscrits. Les
autres centres dépassent aussi les 2 s (Porto-Novo jusqu'à 7,9 s pour 408 lignes).

Cause : une requête par inscrit pour lire `candidats` (N+1), et pas d'index sur `candidats.npi` (19 303
lignes, anciennes campagnes comprises) ni sur `inscriptions (centre_id, concours_id)`. Correction : une
jointure et des index (créés par `sql/reparation.sql`).

## INC-208 (Candidat)

> « Je suis inscrit au concours des douanes. Je ne peux pas m’inscrire au concours des impôts, dont l’épreuve a lieu le même jour. »

DOUANES et IMPOTS ont leur épreuve le même jour (12/12/2026). La règle interdit de s'inscrire aux deux. Rien
à corriger.

## Non signalé : injection SQL

Le 20/11 vers 04:13, l'IP externe 102.64.217.33 a exploité une injection SQL sur la recherche par nom et
récupéré 18 535 lignes de `candidats`. Détails dans `BONUS_SECURITE.md`.
