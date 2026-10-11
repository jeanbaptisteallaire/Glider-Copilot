# Wind Glider (ex-spiral, ex-GLIDY, ex-Glider Copilot) — consignes pour les sessions Claude

- V20 : l'app s'appelle **Wind Glider** (titre en Inter capitales « WIND GLIDER », univers illustré, jetons `brandInk`/`brandPaper`/`brandSky`, `WgArt.kt`). V18.3 : ex-**spiral**. `applicationId` et noms de modules inchangés (GLIDY dans le code).

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
- V19 : textes légaux dans `site/` (GitHub Pages, `pages.yml`), adresses dans `GcLegal` ; conformité Play : `docs/play-store/CONFORMITE-GOOGLE-PLAY.md`. Toute nouvelle donnée collectée = mettre à jour la politique et le formulaire Sécurité des données.
- CI : `.github/workflows/android.yml` publie journal, résultats de tests, APK et captures sur la branche `ci-data`.

## iOS / KMP

- Décision (JB, 11/10/2026) : version iOS de Wind Glider. Métier partagé en **Kotlin Multiplatform**, interface iOS en **SwiftUI**, **édition Lite d'abord** (Prévol météo + cartes, Pilotage, Mes vols).
- Ordre de migration KMP : ✅ `core:domain`, ✅ `data:precog` (étape 1), ✅ `data:carto` (lecture des packs : PMTiles, PNG Terrarium, openAIP ; téléchargement et style MapLibre restent en `jvmMain`) ; puis `data:ogn`, `core:flightarchive`. Les modules `feature:*` et `app` restent Android (Compose).
- Disposition : `src/commonMain` (métier), `src/jvmMain` (Android), `src/iosMain` (iOS), `src/commonTest` (lancé sur JVM **et** simulateur iOS), `src/jvmTest` (tests historiques JUnit). Cible `jvm()` consommée telle quelle par Android, cibles `iosArm64` + `iosSimulatorArm64`.
- Règle d'or : **comportement Android inchangé**. Tout remplaçant d'une API Java rend exactement la même valeur, prouvé par un `JavaParityTest` (jvmTest) ; formats de fichier conservés (cache precog, IGC à l'octet près).
- Dans `commonMain`, jamais de `java.*` :
  - temps : `kotlinx.datetime.Instant/LocalDate/TimeZone/Clock`, `kotlin.time.Duration` ; `Duration.between(a, b).seconds` → `secondsBetween(a, b)` (arrondi vers −∞ comme java.time), `Instant.EPOCH` → `EPOCH` ;
  - maths : `toRadians`/`toDegrees`/`roundHalfUp` (mêmes constantes et arrondis que `java.lang.Math`), `kotlin.math.*` ;
  - texte : `formatFixed(v, décimales, decimalComma)` pour `String.format("%.nf")`, `Int.zeroPad(n)` pour `%0nd`, `normalizeNfd` ;
  - fichiers : **Okio** (`FileSystem`, `Path`, `okio.IOException` = `java.io.IOException` sur JVM) ;
  - réseau : interface commune `HttpClient` ; JVM = `UrlConnectionHttpClient` (inchangé, gzip compris), iOS = `KtorHttpClient` (Ktor, moteur Darwin).
  - tout le reste passe par `expect`/`actual` dans `Platform.kt` (domain) ou `Http.kt` (precog).
- Côté Android, les écrans (prevol, app) convertissent aux frontières (`toJavaInstant()`, `toJavaLocalDate()`) : l'affichage java.time ne change pas.
- `:shared` : framework iOS statique **WindGliderShared** (exporte core:domain, data:precog, data:carto) et façades Swift : `WindGliderWeather` (météo), `IosFlightEngine` (équivalent de FlightEngine.kt : cœur de vol, sécurité, démo, IGC), `ClubDirectory`, `FlightLibrary` (carnet), `IosPacks` (cartes hors ligne). Pas de cible Android. Toute fonction appelée depuis Swift ne doit jamais lever d'exception Kotlin (sinon arrêt de l'app) : `runCatching` ou `@Throws`.
- Altitude du terrain sur iOS : openAIP du pack, sinon relief Copernicus, sinon sol du modèle ARPEGE (affiché comme hypothèse) ; inconnue = pas de marge. iOS ne télécharge que `relief` + `aero` du pack (le fond vectoriel attend MapLibre iOS ; carte = MapKit en attendant).
- Build de développement : argument de lancement `-testData` = météo d'essai (réponses synthétiques des tests precog, bandeau rouge « DONNÉES D'ESSAI »), jamais en version publiée.
- `ios/` : app SwiftUI. Projet généré par **XcodeGen** (`cd ios && xcodegen`), `.xcodeproj` non versionné ; la phase de build appelle `./gradlew :shared:embedAndSignAppleFrameworkForXcode`. Jetons de charte dans `ios/WindGlider/Sources/Theme.swift`, miroir de `Theme.kt` : jamais de couleur ni de police codée dans une vue. Clé PRECOG dans `ios/WindGlider/Secrets.xcconfig` (ignoré par git, modèle `Secrets.example.xcconfig`).
- **Parité Android / iOS (plan : `docs/ios/PLAN-IOS.md`)** : toute règle métier, tout calcul et tout texte affiché vit en Kotlin commun (`core:*`, `data:*`, puis `logic:*` = état d'écran + actions). Compose et SwiftUI ne font que dessiner l'état ; une évolution = un seul changement, testé une fois en `commonTest`. Restent par plateforme : capteurs, son, voix, carte, connexion Google/Apple, Keychain/DataStore.
- `ios/WindGlider/Sources/` est rangé par onglet : `App/` (entrée, AppModel, `Edition`), `Common/` (jetons, composants), `Platform/` (capteurs, audio, connexions), `Welcome/`, `Home/`, `Prevol/`, `Pilotage/`, `Carte/`, `MesVols/`, `Feed/`, `Checklists/`, `Tuto/`, `Compte/`. Deux cibles XcodeGen : `WindGlider` (Lite) et `WindGliderFull` (drapeau Swift `FULL`, équivalent de `-Pglidy.edition=full`), même identifiant `com.neutronstar.glidercopilot`.
- Base de données : même projet Supabase qu'Android (« Glider copilot », eu-west-1 : `profiles`, `flights`, `feedback`, stockage `igc`, RPC `delete_my_account`). URL et clé publique dans `ios/WindGlider/Secrets.xcconfig` (ignoré par git). Aucune modification du projet Supabase (Auth, schéma) sans accord explicite de JB.
- Mêmes règles qu'Android : aucune fonction de sécurité ne dépend du réseau, toute valeur affichée porte sa source et son hypothèse, mention `PRECOG_CREDIT`.
- Poste local : `JAVA_HOME` = JBR d'Android Studio, `ANDROID_HOME=~/Library/Android/sdk`, Xcode sélectionné (`xcode-select -p`). Kotlin 2.1.20 compile avec Xcode 27.
- CI : `.github/workflows/ios.yml` (macOS) : `iosSimulatorArm64Test`, framework, build de l'app sans signature. Le job `android` exécute les `jvmTest` via `test`.
- Pilotage : la migration a modifié 2 lignes de type dans `FlightScreen.kt` (`epochSeconds`, `EPOCH`), avec accord de JB (11/10/2026), garde-fou régénéré.
