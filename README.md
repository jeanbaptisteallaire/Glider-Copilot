# GLIDY Mes vols

Application Android Kotlin modulaire consacrée à l'archivage et au rejeu de traces IGC.

Le projet est autonome. Il pourra produire son propre APK ou fournir ses modules à GLIDY. Il ne dépend pas du moteur de pilotage, d'OGN, des calculs de sécurité ni de la carte de vol de GLIDY.

## Phase actuelle

La phase 4 fournit une page **Mes vols** structurée autour d'un ViewModel testable. Le chargement, la liste, l'état vide, l'erreur récupérable, les fichiers manquants et la navigation vers le détail ont des états explicites. La liste reste paresseuse et a été vérifiée avec 500 vols sans réseau. L'archive locale et l'import IGC de la phase 3 restent actifs. Supabase et le moteur de rejeu 3D restent désactivés.

## Modules

- `app` : coque APK autonome et futur point d'intégration.
- `core:flightarchive` : modèles, contrats, parseur IGC et calculs Kotlin purs.
- `data:flightarchive` : stockage local Room, fichiers IGC privés, déduplication et reconstruction de l'index.
- `data:flightcloud` : futur adaptateur Supabase, désactivé par défaut.
- `feature:myflights` : ViewModel, états UI, import, liste Mes vols, détail, aperçu de trace et profil d'altitude.
- `feature:replay3d` : emplacement réservé à la future expérimentation 3D.

## Vérification

```bash
./gradlew test lintDebug assembleDebug
python3 tools/verify_module_boundaries.py
```

Les règles détaillées figurent dans `docs/ARCHITECTURE.md` et `docs/BOUNDARIES.md`.

Une trace synthétique réaliste autour de Saint-Martin-de-Londres se trouve dans `samples/`. Elle est générée pour les tests et ne constitue pas une preuve de vol.
