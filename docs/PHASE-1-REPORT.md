# Rapport de phase 1

## Réalisé

- projet Android autonome créé dans un dossier distinct ;
- modules et dépendances minimales déclarés ;
- contrats du domaine IGC et de l'archive locale ;
- passerelle cloud désactivée par défaut ;
- emplacement 3D déclaré sans moteur ;
- garde-fou automatisé des dépendances ;
- tests de validation des invariants du domaine ;
- coque APK autonome minimale.

## Validation du 18 septembre 2026

- `./gradlew test assembleDebug --offline --no-parallel --max-workers=1` : réussi ;
- `python3 tools/verify_module_boundaries.py` : 16 fichiers Kotlin/Gradle contrôlés, aucune dépendance interdite ;
- APK autonome produit : `dist/glidy-mes-vols-phase1-debug.apk` ;
- SHA-256 : `95c4027bcfbeca23dfdc7e9345a1fdcc1660e41722892d170f365279e6e9e19b`.

## Non réalisé volontairement

- lecture ou écriture réelle de fichiers IGC ;
- base locale ;
- interface Mes vols fonctionnelle ;
- réseau Supabase ;
- rendu 3D.

Ces éléments appartiennent aux phases suivantes.
