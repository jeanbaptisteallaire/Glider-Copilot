# Proposition d'interface — phases 2 et 3

## Direction retenue

L'interface utilise une palette blanche, gris clair, graphite et noir. Elle évite les effets décoratifs lourds : grands titres, surfaces généreuses, cartes arrondies et hiérarchie typographique nette.

## Parcours démontrable

1. La page **Mes vols** présente une synthèse locale et les vols récents.
2. Chaque carte montre la date, le lieu, le planeur, la durée, la distance, l'altitude maximale et le dénivelé.
3. Un appui ouvre le **détail du vol** avec aperçu de trace, profil d'altitude et données du fichier.
4. Le bouton d'import IGC ouvre le sélecteur de documents Android et archive réellement la trace choisie.
5. Le bouton de rejeu 3D reste désactivé jusqu'à la phase dédiée.

Les valeurs, la trace et le profil d'altitude affichés en phase 3 proviennent du fichier IGC importé. Les données de démonstration de la phase 2 ont été retirées.

## Captures validées sur Android

- `ui-phase2-mes-vols.png`
- `ui-phase2-detail-vol.png`
- `ui-phase3-imported-flight.png`
- `ui-phase3-flight-detail.png`

## Principes d'intégration

- aucune dépendance vers Pilotage, OGN ou la carte de vol ;
- interface entièrement contenue dans `feature:myflights` ;
- écran assemblé par l'application autonome ;
- composants graphiques basés uniquement sur Jetpack Compose et Material 3.
