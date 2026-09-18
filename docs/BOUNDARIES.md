# Frontières et fichiers protégés

## Interdictions

Le projet ne doit pas dépendre des packages ou modules suivants du projet GLIDY :

- `feature:flight`
- `data:ogn`
- `data:carto`
- `FlightEngine`
- `FlightService`
- `SafetyEngine`
- `OgnLiveRepository`

Le projet ne modifie aucun dossier de session, APK ou fichier source appartenant au travail de Claude.

## Dépendances internes autorisées

- `core:flightarchive` ne dépend d'aucun module du projet.
- les modules `data` et `feature` peuvent dépendre de `core:flightarchive` ;
- `feature:myflights` ne dépend pas de `data:flightcloud` ;
- `feature:replay3d` ne dépend pas de `feature:myflights` ;
- seul `app` assemble tous les modules.

Le script `tools/verify_module_boundaries.py` fait échouer la vérification s'il trouve une dépendance interdite.

