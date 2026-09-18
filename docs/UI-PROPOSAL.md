# Proposition d'interface — phase 2

## Direction retenue

L'interface utilise une palette blanche, gris clair, graphite et noir. Elle évite les effets décoratifs lourds : grands titres, surfaces généreuses, cartes arrondies et hiérarchie typographique nette.

## Parcours démontrable

1. La page **Mes vols** présente une synthèse locale et les vols récents.
2. Chaque carte montre la date, le lieu, le planeur, la durée, la distance, l'altitude maximale et le dénivelé.
3. Un appui ouvre le **détail du vol** avec aperçu de trace, profil d'altitude et données du fichier.
4. Les boutons d'import IGC et de rejeu 3D sont visibles mais désactivés. Ils indiquent les étapes futures sans simuler une fonction qui n'existe pas encore.

Les données affichées dans cette phase sont des exemples de démonstration. Le branchement aux véritables résumés IGC arrivera avec l'archive locale.

## Captures validées sur Android

- `ui-phase2-mes-vols.png`
- `ui-phase2-detail-vol.png`

## Principes d'intégration

- aucune dépendance vers Pilotage, OGN ou la carte de vol ;
- interface entièrement contenue dans `feature:myflights` ;
- écran assemblé par l'application autonome ;
- composants graphiques basés uniquement sur Jetpack Compose et Material 3.
