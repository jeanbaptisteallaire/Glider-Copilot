# spiral (ex-GLIDY, ex-Glider Copilot) — consignes pour les sessions Claude

- V18.3 : l'app s'appelle **spiral** (minuscules). `applicationId` et noms de modules inchangés (GLIDY dans le code).

- Lire d'abord `docs/HANDOFF.md` (état, décisions, prochaine session) et le plan dans le projet claude.ai « Planneur App ».
- Onglets : **Feed** (S17, premier onglet : fil des pilotes suivis, recherche, suivre — démo jusqu'à Supabase), **Prévol** (météo + planeur FLARM + club), **Check-lists**, **Pilotage**, **Carte** et **Mes vols** (S10, carnet IGC + rejeu 3D ; S16 : page profil du pilote + grille 3 colonnes + partage par vol). Pas d’Annexes.
- **S18 Lite (édition par défaut)** : `BuildConfig.LITE` → onglets Prévol (météo + cartes), Pilotage (+ interrupteur REC, enregistrement manuel), Mes vols (sans partage). Édition complète : `-Pglidy.edition=full`.
- Mes vols (`core/data:flightarchive`, `data:flightcloud`, `feature:myflights`, `feature:replay3d`) ne dépend jamais de `feature:flight`, `data:ogn`, `data:carto` ni du moteur de vol : il ne reçoit que des IGC fermés (`FlightArchiveHost`). Vérifié en CI par `tools/mes-vols/verify_module_boundaries.py`.
- Charte de référence : Pilotage = maquette `planeur-pilotage-prevol-v8` (noir, vert #b7f7a5, orange #ff9f43). V18.1 « New UI » : police **Inter** partout, échelle typographique d'Apple (`Gc.type.largeTitle…caption2`), pages blanches façon Apple Santé (cartes teintées par catégorie, sélection **bleu ciel** #0A84FF), texte au plus court.
- S15 : thème clair « social » (blanc) par défaut sur tous les onglets ; V18.7 : **Pilotage aussi sur fond blanc** (`GlidyFlightTheme`, accord JB), charte sombre v8 conservée en thème sombre (« En vol »), via `GlidyAdaptiveTheme(light)` et `Gc.social`. **Pilotage ne change jamais** sans accord explicite de JB : garde-fou CI `tools/en-vol/check_en_vol.py` (empreintes des fichiers Pilotage et de la charte sombre).
- Sécurité : aucune fonction de sécurité ne dépend du réseau ; toute valeur affichée porte sa source et son hypothèse.
- Charte : tout est dans `core/designsystem/Theme.kt` (jetons). Ne jamais coder une couleur ou une police dans un écran.
- Données precog : ne jamais coder une unité en dur, lire `units`/`semantics` (voir `data/precog/Envelope.kt`).
- OGN (à venir) : ODbL, respect des choix DDB, pas de redistribution > 24 h.
- Modules JVM purs (`core:domain`, `data:precog`) : sans dépendance tierce, testables hors ligne.
- Secrets : jamais dans le dépôt (dépôt public). Clé openAIP = secret GitHub `OPENAIP_KEY`.
- CI : `.github/workflows/android.yml` publie journal, résultats de tests, APK et captures sur la branche `ci-data`.
