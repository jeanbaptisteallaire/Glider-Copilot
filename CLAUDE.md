# Glider Copilot — consignes pour les sessions Claude

- Lire d'abord `docs/HANDOFF.md` (état, décisions, prochaine session) et le plan dans le projet claude.ai « Planneur App ».
- MVP : deux écrans, **Prévol** (météo + planeur + club) et **Vol**. Pas d'Annexes ni de Debrief.
- Sécurité : aucune fonction de sécurité ne dépend du réseau ; toute valeur affichée porte sa source et son hypothèse.
- Charte : tout est dans `core/designsystem/Theme.kt` (jetons). Ne jamais coder une couleur ou une police dans un écran.
- Données precog : ne jamais coder une unité en dur, lire `units`/`semantics` (voir `data/precog/Envelope.kt`).
- OGN (à venir) : ODbL, respect des choix DDB, pas de redistribution > 24 h.
- Modules JVM purs (`core:domain`, `data:precog`) : sans dépendance tierce, testables hors ligne.
- Secrets : jamais dans le dépôt (dépôt public). Clé openAIP = secret GitHub `OPENAIP_KEY`.
- CI : `.github/workflows/android.yml` publie journal, résultats de tests, APK et captures sur la branche `ci-data`.
