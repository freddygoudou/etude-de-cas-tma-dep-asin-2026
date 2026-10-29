# Rapport d'incident INC-206

> « Le 19 novembre au matin, pendant une demi-heure, plus personne ne pouvait s’inscrire ni consulter sa convocation : les pages tournaient sans fin. »

- Signalé par : Exploitation
- Période : 19/11/2026 de 09:09:48 à 09:39:49 (heure du Bénin), environ 30 min
- Gravité : critique (inscription et convocation indisponibles, données abîmées)
- Incidents liés : INC-203, INC-201, INC-202
- État : cause trouvée, correction codée et testée, données réparées par script

## Résumé

Le service national d'identification a mis entre 5 et 62 s à répondre au lieu de 0,1 à 0,4 s d'habitude.
Notre application l'appelle sans timeout, donc chaque inscription et chaque convocation attendait la
réponse. Les candidats voyaient la page tourner et recliquaient. Comme les contrôles de l'inscription ne
sont pas protégés contre les requêtes simultanées, ça a produit des doublons, des numéros en double et la
surréservation de Lokossa.

## Chronologie (heure du Bénin, d'après le journal)

- 09:05 - 09:08 : quelques convocations à 2-3 s, premiers signes de ralentissement.
- 09:09:48 : début du premier appel au service de plus de 5 s.
- 09:11 - 09:12:43 : 11 inscriptions IMPOTS à Lokossa démarrent alors qu'il reste 4 places. Elles se
  terminent entre 09:11:23 et 09:12:43, toutes ont vu « 296 < 300 » et le même dernier numéro. Résultat :
  307/300, L-0297 x5 et L-0298 x6.
- 09:11:57 : premiers refus « centre complet » à Lokossa.
- 09:12:24 : requête la plus longue, 61,7 s.
- 09:13 - 09:34 : 6 doubles inscriptions, à chaque fois un second clic 1 à 37 s après le premier, depuis la
  même IP.
- 09:21:39 et 09:21:47 : P-0464 donné à deux candidats (INC-201).
- 09:39:49 : fin du dernier appel lent.
- 09:40 - 09:46 : retour progressif à la normale.

## Cause racine

La lenteur vient du service d'identification, ce n'est pas de notre ressort. Mais elle était prévisible
et les règles disent quoi faire : au-delà de 5 s, enregistrer l'inscription « en attente de vérification ».

Côté application :
- le client HTTP est en configuration par défaut, sans timeout. Aucune inscription en base n'est en attente :
  la règle des 5 s n'a jamais été codée ;
- la convocation rappelle le service juste pour afficher nom et prénoms, ce qui rend une simple consultation
  dépendante du service ;
- l'appel au service se fait entre les contrôles (étapes 4 à 6) et l'écriture (étape 8), sans verrou ni
  contrainte unique. Avec des appels de 60 s, beaucoup de requêtes passent les contrôles en même temps ;
- le bouton Valider n'est pas désactivé après le clic.

## Impact

- 60 appels de 5,2 à 61,7 s, touchant 60 requêtes : 41 inscriptions et 19 convocations.
- 96 requêtes en cours pendant la période, toutes routes confondues.
- Pendant la fenêtre : 6 doubles inscriptions, 9 numéros attribués plusieurs fois (30 attributions), Lokossa
  à 307/300.
- Après la reprise : 20 candidats à recontacter (nouvelle convocation ou convocation annulée), 7 candidats
  en surnombre à Lokossa, 4 chefs de centre à prévenir.
- On ne sait pas combien de candidats ont abandonné : les requêtes coupées côté navigateur ne sont pas
  dans le journal.

## Correction

Code (`correction/`) :
- `IdentificationServiceImpl` : appel au service avec un timeout de connexion, un timeout de réponse et une
  limite globale de 5 s. Au-delà, l'inscription est enregistrée `en_attente_verification`. La config refuse
  un délai supérieur à 5 s ;
- `InscriptionServiceImpl` : l'appel est fait avant la transaction. Les étapes 4, 5, 6 et 8 sont faites dans
  une seule transaction avec un verrou sur le centre (`@Lock(PESSIMISTIC_WRITE)` dans `CentreRepository`,
  soit un `SELECT ... FOR UPDATE`), plus des contraintes uniques en base au cas où ;
- tests : service simulé à 8 s (réponse en moins de 5,8 s avec le statut en attente), et des rafales de
  requêtes HTTP simultanées sur H2 et PostgreSQL. J'ai vérifié qu'en retirant le verrou, les tests tombent :
  les requêtes se marchent dessus et des inscriptions valides sont refusées (9 acceptées au lieu de 10 sur le
  centre de 10 places, 35 au lieu de 77 sur 3 centres).

Données (`sql/reparation.sql`) : doublons supprimés (et archivés), numéros réattribués, contraintes uniques
et trigger de capacité. Pour Lokossa, aucune suppression : liste et décision à demander à la direction en tête du script.

## Actions

| Action | Qui | Quand |
|---|---|---|
| Déployer la correction | TMA | avant le 30/11 |
| Passer `sql/reparation.sql` (les contraintes protègent même avant le déploiement) | TMA + exploitation | tout de suite |
| Traitement planifié qui revérifie les inscriptions en attente et prévient si le NPI est inconnu | TMA | avant le 30/11 |
| Convocation : lire le nom dans `candidats` au lieu d'appeler le service | TMA | avant le 30/11 |
| Désactiver Valider après le clic, clé d'idempotence côté API | TMA | lot suivant |
| Circuit breaker sur le service d'identification | TMA | lot suivant |
| Alerte si le temps de réponse du service dépasse 2 s (p95) | Exploitation | sous 1 semaine |
| Voir avec l'opérateur du service ce qui s'est passé le 19/11 et convenir d'un SLA | Direction / maîtrise d’ouvrage | sous 2 semaines |
| Test de charge avec un service d'identification lent avant chaque campagne | TMA | campagne suivante |
