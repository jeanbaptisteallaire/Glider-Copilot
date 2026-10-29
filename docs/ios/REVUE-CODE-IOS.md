# Revue de code iOS — Wind Glider 1.0 (build 9, branche `ios-v4-i18n`)

Revue d'un développeur iOS senior, 26/10/2026. Périmètre : `ios/WindGlider/Sources` (≈ 4 300 lignes Swift, 23 fichiers) et `ios/project.yml`.

## Verdict

Le code n'est pas « aberrant » : l'architecture de fond est saine (métier en Kotlin commun, SwiftUI qui dessine, rangement par onglet, pas de `as!`, pas de crash volontaire hors `init(coder:)`). Mais il porte les marques d'un code écrit vite, par itérations successives : fichiers trop gros, un objet central qui fait tout, des réglages éparpillés, des couleurs codées en dur malgré la règle du projet, et **aucun test iOS**. Rien de bloquant pour la publication, mais la dette va ralentir la 1.1 (refonte PFD) si elle n'est pas traitée maintenant.

---

## 1. À corriger en priorité

| # | Problème | Où | Pourquoi c'est un problème | Correction |
|---|---|---|---|---|
| 1 | **Données d'essai embarquées dans l'app publiée** | `project.yml` (ressource `data/precog/src/jvmTest/resources/fixtures`) | Les réponses synthétiques des tests partent sur l'App Store ; seul l'argument `-testData` les active, mais elles alourdissent l'app et un argument de lancement suffit à afficher de fausses données météo. | Ne les copier qu'en configuration Debug (script de build conditionnel ou cible séparée). |
| 2 | **Commentaire et logique d'arrière-plan périmés** | `AppModel.enterBackground()` | Le commentaire promet « localisation + audio écran éteint » ; depuis le build 9, l'audio d'arrière-plan n'existe plus. Le moteur et le `VarioTone` continuent de tourner pour rien en arrière-plan. | Couper le son en arrière-plan dans tous les cas, garder seulement GPS + enregistrement IGC pendant un vol ; mettre le commentaire à jour. |
| 3 | **Couleurs et polices codées dans les écrans** | 15 × `Color(red:…)`, 20 × `Font.custom("Inter"…)`, 6 × `.system(size:)` (Pilotage, Welcome, Notices, MyFlights) | Contraire à la règle du projet (« jamais de couleur ni de police codée dans une vue ») ; la refonte Godspeed devra les retrouver une par une. | Tout passer par `WG` / `PI` dans `Theme.swift` (ajouter les jetons manquants : ombres, dégradés, vert/violet fluo du mode sombre). |
| 4 | **`AppModel` fourre-tout (« god object »)** | `App/AppModel.swift` (282 l., 14 `@Published`) | Météo, club, moteur de vol, capteurs, trafic OGN, packs, son, voix, démo, réglages et minuterie dans une seule classe : chaque changement republie tout et rafraîchit toutes les vues abonnées. | Découper en modèles ciblés : `FlightModel` (moteur, minuterie, capteurs), `WeatherModel`, `TrafficModel`, `Settings`. Les injecter séparément (`@Environment` / `@Observable`). |
| 5 | **Rafraîchissement de tout Pilotage 4 fois par seconde** | `Timer` 0,25 s qui réassigne `pilot`, `sensorsUi`, `status`, `trafficSnap` | Toute la hiérarchie `PilotView` est recalculée à chaque tic, même si rien ne change ; coûteux en batterie sur un vol de 5 h. | Passer à l'Observation (`@Observable`, iOS 17 minimum déjà en place) pour ne redessiner que ce qui change ; comparer avant d'assigner ; `TimelineView` ou `CADisplayLink` pour la carte seulement. |
| 6 | **Aucun test iOS** | pas de cible de test dans `project.yml` | Le correctif « Se connecter avec Apple » (perte du droit à la signature) aurait été vu par un test d'intégration ou un simple script de vérification. | Ajouter une cible `WindGliderTests` (logique Swift : `Nonce`, `trDyn`, `PackStore`) et un script CI qui vérifie les droits et `UIBackgroundModes` de l'archive. |

## 2. Organisation du code

| # | Constat | Proposition |
|---|---|---|
| 7 | `PilotView.swift` fait **778 lignes** et contient 8 types (PilotView, MapOverlay, ProfileStrip, VarioScale, FieldPicker, PanelToggle, MiniSwitch, DayNightToggle…). `FluidMapView.swift` 559 l., `PrevolView.swift` 480 l. | Un fichier par vue : `Pilotage/Header.swift`, `Pilotage/MapOverlay.swift`, `Pilotage/ProfileStrip.swift`, `Pilotage/VarioBar.swift`, `Common/Controls/MiniSwitch.swift`… Viser < 250 lignes par fichier. |
| 8 | Réglages éparpillés : 16 × `UserDefaults.standard`, 15 × `@AppStorage` avec des clés en chaîne libre (`"pilot.dark"`, `"varioSound"`, `"showAirspaces"`…), et `PI.dark` relit `UserDefaults` à chaque couleur. | Un seul `enum SettingsKey` (ou une struct `Settings` observable) ; plus aucune chaîne de clé dans les vues. Le thème reçoit `dark` par l'environnement SwiftUI (`.environment(\.pilotDark, …)`) au lieu de lire le disque. |
| 9 | Traductions maison : `tr("texte français")` avec un dictionnaire `EN` dans `Strings.swift` ; une phrase modifiée en français casse silencieusement l'anglais. | Passer au **String Catalog** d'Xcode (`Localizable.xcstrings`) avec des clés stables ; ou, mieux pour la parité, générer les textes depuis le Kotlin commun (règle « tout texte affiché vit en Kotlin commun »). |
| 10 | `Task.detached` × 6 dans `AccountModel` pour appeler le Kotlin. | Un seul point d'entrée asynchrone (`actor KotlinBridge` ou fonctions `suspend` exportées par SKIE/KMP-NativeCoroutines) ; plus de `detached` dans les vues. |
| 11 | `try?` × 22 qui avalent les erreurs (audio, écriture des packs, Keychain, profil pilote). | Garder `try?` seulement là où l'échec est vraiment sans conséquence ; ailleurs, journaliser avec `Logger` (OSLog) et remonter un état d'erreur visible. |
| 12 | Délais « magiques » : `DispatchQueue.main.asyncAfter(1.2)` pour la carte, `Task.sleep` dans les essais audio, seuils en dur dans les vues (`+200 m`, `0.92`, `26 pt`…). | Constantes nommées dans `Theme.swift` ou dans le Kotlin commun (`PilotDeclutter.CAUTION_M` montre le bon exemple). |
| 13 | `sensors: Sensors!` (optionnel implicite) dans `AppModel`. | Initialiser dans `init` et déclarer non optionnel. |
| 14 | `traffic: IosTraffic?` commenté « jamais nil ». | Le rendre non optionnel. |
| 15 | Dossiers parasites : `ios/build` (698 Mo, ignoré) et `ios/x` (vide). | Supprimer `ios/x` ; nettoyer `ios/build` (sortie d'Xcode, déjà ignorée). |

## 3. Signes de code écrit vite (à surveiller)

- Commentaires qui racontent l'historique (« V3 : », « V5 (JB, 19/10/2026) », « comme la 2.0.4 Lite ») plutôt que l'intention : utiles au début, ils deviennent du bruit. L'historique est dans git ; garder le *pourquoi*.
- Lignes de 150 à 200 caractères avec plusieurs modificateurs SwiftUI enchaînés : illisibles en revue. Formater (SwiftFormat) et extraire des sous-vues.
- Code de test resté dans le chemin de production (`-testData`, `-mapV2`, `fluidMapOff`) : à isoler derrière `#if DEBUG`.
- Valeurs dupliquées entre Android et iOS (seuils, textes) au lieu de vivre une seule fois en Kotlin commun.

## 4. Bonnes pratiques à adopter

1. **Un fichier = une vue**, < 250 lignes ; une vue SwiftUI < 100 lignes dans `body`.
2. **Aucune couleur, police, taille ou seuil en dur dans une vue** : tout dans `Theme.swift` ou en Kotlin commun.
3. **Modèles petits et observables** (`@Observable`), injectés par l'environnement ; pas d'objet central.
4. **Réglages typés** dans un seul endroit, jamais de clé `UserDefaults` en chaîne dans une vue.
5. **Erreurs visibles** : `Logger` pour journaliser, état d'erreur pour l'utilisateur ; `try?` seulement si l'échec n'a aucune importance.
6. **Code de développement derrière `#if DEBUG`**, ressources de test hors de l'app publiée.
7. **Tests** : une cible `WindGliderTests` + vérification automatique de l'archive (droits, modes d'arrière-plan, numéro de build) avant chaque envoi.
8. **SwiftFormat / SwiftLint** en CI (`ios.yml`) avec une limite de longueur de ligne et de fichier.
9. **Textes localisés par String Catalog** ou générés depuis le Kotlin commun.
10. **Commentaires = pourquoi**, pas l'historique.

## 5. Ordre proposé (à faire sur la branche de la 1.1, pas sur la 1.0 en vérification)

1. Points 1, 2, 13, 14, 15 (rapides, sans risque).
2. Point 3 (jetons de charte) : à faire **avec** la refonte Godspeed, qui touche les mêmes lignes.
3. Points 7 et 8 (découpage des fichiers, réglages typés).
4. Points 4 et 5 (découpage d'`AppModel`, Observation) : le plus gros gain en batterie et en lisibilité.
5. Points 6, 9, 10, 11 (tests, traductions, pont Kotlin, erreurs).
