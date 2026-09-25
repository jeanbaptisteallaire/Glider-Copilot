# GLIDY Mes vols

Application Android Kotlin modulaire consacrée à l'archivage et au rejeu de traces IGC.

Le projet est autonome. Il pourra produire son propre APK ou fournir ses modules à GLIDY. Il ne dépend pas du moteur de pilotage, d'OGN, des calculs de sécurité ni de la carte de vol de GLIDY.

## Phase actuelle

La phase 6 ajoute le rejeu 3D interactif d'un vol IGC sur un relief satellite, ainsi qu'une frontière atomique pour recevoir un fichier terminé depuis le Pilotage. La caméra suit le planeur, se tourne avec un doigt, zoome par pincement et propose trois vitesses de lecture. L'archive, le partage, la suppression et l'import manuel restent actifs. Supabase reste désactivé jusqu'à l'arrivée d'un compte utilisateur.

## Modules

- `app` : coque APK autonome et futur point d'intégration.
- `core:flightarchive` : modèles, contrats, parseur IGC et calculs Kotlin purs.
- `data:flightarchive` : stockage local Room, fichiers IGC privés, déduplication, reconstruction de l'index, partage par FileProvider et boîte de dépôt atomique des vols terminés.
- `data:flightcloud` : futur adaptateur Supabase, désactivé par défaut.
- `feature:myflights` : ViewModel, états UI, import, liste, détail, partage, suppression contrôlée, aperçu de trace et profil d'altitude.
- `feature:replay3d` : MapLibre, relief satellite, planeur procédural Three.js, lecture temporelle et gestes tactiles.

## Vérification

```bash
./gradlew test lintDebug assembleDebug
python3 tools/verify_module_boundaries.py
```

Les règles détaillées figurent dans `docs/ARCHITECTURE.md` et `docs/BOUNDARIES.md`.

Le rapport de livraison et le point de raccordement destiné au Pilotage figurent dans `docs/PHASE-6-REPORT.md`.

Une trace synthétique réaliste autour de Saint-Martin-de-Londres se trouve dans `samples/`. Elle est générée pour les tests et ne constitue pas une preuve de vol.
