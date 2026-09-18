# GLIDY Mes vols

Application Android Kotlin modulaire consacrée à l'archivage et au rejeu de traces IGC.

Le projet est autonome. Il pourra produire son propre APK ou fournir ses modules à GLIDY. Il ne dépend pas du moteur de pilotage, d'OGN, des calculs de sécurité ni de la carte de vol de GLIDY.

## Phase actuelle

La phase 2 ajoute un parseur IGC Kotlin pur, les calculs de résumé et une proposition d'interface navigable en Compose. L'archive locale fonctionnelle arrivera en phase 3. Supabase et le moteur de rejeu 3D restent désactivés.

## Modules

- `app` : coque APK autonome et futur point d'intégration.
- `core:flightarchive` : modèles, contrats, parseur IGC et calculs Kotlin purs.
- `data:flightarchive` : futur stockage local et accès aux fichiers IGC.
- `data:flightcloud` : futur adaptateur Supabase, désactivé par défaut.
- `feature:myflights` : proposition navigable de la page Mes vols et du détail.
- `feature:replay3d` : emplacement réservé à la future expérimentation 3D.

## Vérification

```bash
./gradlew test lintDebug assembleDebug
python3 tools/verify_module_boundaries.py
```

Les règles détaillées figurent dans `docs/ARCHITECTURE.md` et `docs/BOUNDARIES.md`.
