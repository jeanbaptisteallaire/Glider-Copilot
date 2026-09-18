# GLIDY Mes vols

Application Android Kotlin modulaire consacrée à l'archivage et au rejeu de traces IGC.

Le projet est autonome. Il pourra produire son propre APK ou fournir ses modules à GLIDY. Il ne dépend pas du moteur de pilotage, d'OGN, des calculs de sécurité ni de la carte de vol de GLIDY.

## Phase actuelle

La phase 1 fixe les frontières d'architecture et les contrats. Elle ne contient encore ni archive locale fonctionnelle, ni connexion Supabase, ni moteur de rejeu 3D.

## Modules

- `app` : coque APK autonome et futur point d'intégration.
- `core:flightarchive` : modèles et contrats Kotlin purs.
- `data:flightarchive` : futur stockage local et accès aux fichiers IGC.
- `data:flightcloud` : futur adaptateur Supabase, désactivé par défaut.
- `feature:myflights` : future page Mes vols.
- `feature:replay3d` : emplacement réservé à la future expérimentation 3D.

## Vérification

```bash
./gradlew testDebugUnitTest assembleDebug
python3 tools/verify_module_boundaries.py
```

Les règles détaillées figurent dans `docs/ARCHITECTURE.md` et `docs/BOUNDARIES.md`.

