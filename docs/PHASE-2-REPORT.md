# Rapport de phase 2

## Domaine IGC livré

- parseur Kotlin pur parcourant le fichier ligne par ligne ;
- prise en charge des en-têtes date, pilote, type et immatriculation du planeur ;
- lecture des enregistrements `B`, coordonnées, validité et altitudes ;
- passage de minuit UTC ;
- distance haversine, durée, emprise, altitudes minimale et maximale et dénivelé positif ;
- erreurs typées pour fichier vide, date absente ou invalide et absence de point valide ;
- avertissements conservés pour ligne tronquée, coordonnées incorrectes, point `V` et heure non monotone.

Le fichier original est seulement lu. Le parseur ne réécrit jamais l'IGC.

## Proposition d'interface

- page Mes vols blanche, grise et noire ;
- synthèse du carnet et cartes de vols récentes ;
- détail navigable avec aperçu de trace, indicateurs et profil d'altitude ;
- boutons des phases futures visibles et explicitement désactivés ;
- données de démonstration clairement séparées de la future archive locale.

## Validation du 18 septembre 2026

- 15 tests de domaine réussis, dont 12 consacrés au parseur ;
- fichier `vol-demo-rejeu-emulateur.igc` lu en entier et uniquement en lecture ;
- Lint Android : 0 erreur ;
- contrôle des frontières : 19 fichiers Kotlin/Gradle, aucune dépendance interdite ;
- installation et contrôle visuel sur l'émulateur Pixel Fold API 35 ;
- APK autonome `0.2.0-phase2` compilé ;
- SHA-256 : `d83d558b4a900ca793bf8d86d875abe925c0a1f91a98054659690f9e06f3107d`.

## Limites conservées

- l'écran utilise encore des vols de démonstration ;
- aucune base locale ni import via le sélecteur Android ;
- aucun appel Supabase ;
- aucun moteur de rejeu 3D.

Ces éléments relèvent des phases suivantes.
