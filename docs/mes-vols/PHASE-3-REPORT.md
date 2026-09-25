# Rapport de livraison — phase 3

Date : 19 septembre 2026

## Résultat

La phase 3 transforme la maquette **Mes vols** en archive locale fonctionnelle. L'application autonome peut sélectionner un fichier IGC, le valider, le copier dans son espace privé, calculer son résumé, l'indexer dans une base Room et afficher sa trace et son profil d'altitude.

Le projet reste indépendant de GLIDY et ne dépend d'aucun code de pilotage, d'OGN ou de cartographie appartenant au travail de Claude.

## Fonctions livrées

- import d'un fichier IGC avec le sélecteur de documents Android ;
- copie privée dans le dossier d'archive de l'application ;
- limite d'import fixée à 64 Mo ;
- validation et calcul des statistiques par le parseur IGC de la phase 2 ;
- empreinte SHA-256 et identifiant stable pour détecter les doublons ;
- index Room avec les données du vol, l'état du fichier et un aperçu simplifié ;
- reconstruction de l'index Room à partir des fichiers encore présents ;
- détection d'un fichier manquant ou devenu invalide ;
- liste **Mes vols** alimentée par l'archive réelle ;
- page de détail avec tracé, profil d'altitude et métadonnées du fichier.

## Trace synthétique de validation

Le fichier `samples/saint-martin-de-londres-vol-synthetique.igc` simule un vol autour de Saint-Martin-de-Londres. Il contient 6 781 positions à une seconde d'intervalle, un décollage, trois ascendances en spirale, plusieurs transitions et un retour au terrain.

Résultat calculé par l'application :

- durée : 1 h 53 min ;
- distance : 128 km ;
- altitude maximale : 2 133 m ;
- gain positif cumulé : 3 745 m.

Cette trace est déterministe, générée par `tools/generate_test_igc.py`, non certifiée et ne correspond à aucun vol réel. Elle sert uniquement au développement et aux tests.

## Vérifications

- 16 tests Kotlin du domaine et du parseur : réussis ;
- 1 test de l'adaptateur cloud inactif : réussi ;
- 6 tests Android de l'archive Room : réussis sur Pixel Fold API 35 ;
- second balayage sans création de doublon : vérifié ;
- suppression de l'index puis reconstruction depuis le fichier : vérifiée ;
- fichier manquant et fichier invalide : vérifiés ;
- import complet dans l'interface Android : vérifié ;
- compilation de l'APK de débogage : réussie ;
- contrôles Android Lint : réussis ;
- vérification des frontières entre modules : 22 fichiers contrôlés, aucune violation ;
- contrôle des différences Git : aucune erreur d'espacement.

## Livrables

- APK : `dist/glidy-mes-vols-phase3-debug.apk`
- SHA-256 de l'APK : `27e7395f2a9686dcc2111503c313f68ce348950b0beebb3f85ecd36ebd1eac0d`
- trace IGC : `samples/saint-martin-de-londres-vol-synthetique.igc`
- SHA-256 de la trace : `a446870c626fc4c2a9a499a6c33f15d591e4c315eba64af9d0105eed38b2b727`
- capture de la liste : `docs/ui-phase3-imported-flight.png`
- capture du détail : `docs/ui-phase3-flight-detail.png`

## Limites conservées pour les phases suivantes

La synchronisation Supabase, le compte utilisateur et le rejeu 3D ne sont pas activés. Le contrat cloud reste prévu dans l'architecture, sans réseau ni clé dans l'APK. La suppression existe au niveau de l'archive locale, mais son action dans l'interface sera ajoutée avec les commandes utilisateur de la prochaine phase.

