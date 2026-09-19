# GLIDY Mes vols

Application Android Kotlin modulaire consacrée à l'archivage et au rejeu de traces IGC.

Le projet est autonome. Il pourra produire son propre APK ou fournir ses modules à GLIDY. Il ne dépend pas du moteur de pilotage, d'OGN, des calculs de sécurité ni de la carte de vol de GLIDY.

## Phase actuelle

La phase 5 complète le détail d'un vol avec ses informations IGC, le partage Android sécurisé du fichier original et la suppression locale après confirmation. Le ViewModel garantit que l'annulation ne change rien et qu'une suppression confirmée ne concerne que le vol choisi. La liste testée avec 500 vols, l'archive locale et l'import IGC restent actifs. Supabase et le moteur de rejeu 3D restent désactivés.

## Modules

- `app` : coque APK autonome et futur point d'intégration.
- `core:flightarchive` : modèles, contrats, parseur IGC et calculs Kotlin purs.
- `data:flightarchive` : stockage local Room, fichiers IGC privés, déduplication, reconstruction de l'index et partage par FileProvider.
- `data:flightcloud` : futur adaptateur Supabase, désactivé par défaut.
- `feature:myflights` : ViewModel, états UI, import, liste, détail, partage, suppression contrôlée, aperçu de trace et profil d'altitude.
- `feature:replay3d` : emplacement réservé à la future expérimentation 3D.

## Vérification

```bash
./gradlew test lintDebug assembleDebug
python3 tools/verify_module_boundaries.py
```

Les règles détaillées figurent dans `docs/ARCHITECTURE.md` et `docs/BOUNDARIES.md`.

Une trace synthétique réaliste autour de Saint-Martin-de-Londres se trouve dans `samples/`. Elle est générée pour les tests et ne constitue pas une preuve de vol.
