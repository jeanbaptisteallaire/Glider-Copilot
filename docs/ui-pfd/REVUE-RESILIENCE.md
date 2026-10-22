# Revue de résilience du code de vol iOS — Wind Glider (branche `ui-pfd-windglider`)

Revue en lecture seule, 23/10/2026. Périmètre : `IosFlightEngine.kt`, `Safety.kt`, `flight/*` (FlightEngineCore, Vario, PfdInstruments, PilotDeclutter, ThermalZoom), `AppModel.swift`, `Sensors.swift`, `Audio.swift`, `PilotView.swift`, `FluidMapView.swift`, `PfdInstrumentsView.swift`, `PackStore.swift` (+ `Relief.kt`, `OwnshipPredictor.swift`, `WindGliderApp.swift` quand le chemin de défaillance y passe).

Référentiel : instrument complémentaire non certifié, mais jugé avec les réflexes DO-178C / ARP4754 : une donnée périmée ne doit pas avoir l'air valide, aucune exception ne doit traverser la frontière Kotlin → Swift, chaque valeur affichée porte sa source, toute dégradation est visible et annoncée.

Hors périmètre de ce rapport : la trace de la carte figée à 600 points (`FlightEngineCore.kt:142` + `FluidMapView.swift:219`), déjà relevée par la revue énergie et en cours de correction.

Légende : **C** critique (peut tromper le pilote sur sa sécurité ou faire planter l'app en vol), **M** majeur (perte de fonction ou hypothèse cachée), **m** mineur.

---

## Critique

### C1 — Marge, distance et position figées après une perte du GPS, présentées comme valides
- **Où** : `IosFlightEngine.kt:378-386` (`safetyState` n'est recalculé qu'à l'arrivée d'une position), `:458-476` (`ui()` publie `st` sans contrôle d'âge ; `pos = snap.gps.position` = dernière position, même vieille de 10 min), `FlightEngineCore.kt:265` (`gps = lastFix` toujours renvoyé), `PilotView.swift:62` (pastille GPS = `app.status.gps` = **autorisation** de localisation, pas fraîcheur du fix ; `p.gpsOk` n'est lu nulle part).
- **Scénario** : téléphone glissé sous la verrière, GPS perdu 3 min en transition à −1,5 m/s. La marge reste figée sur « +240 » (calculée avec l'ancienne altitude ET l'ancienne position) alors que la marge réelle est ≈ −30 m. Aucune pastille GPS (l'autorisation est toujours accordée), le planeur reste dessiné à l'ancienne position, aucune alerte (les alertes ne sont évaluées qu'à chaque fix).
- **Correction** : dans `ui()`, `val gpsFresh = (snap?.gpsAgeS ?: 99.0) < 5.0` ; si `!gpsFresh` → `marginKnown = false`, `marginText = "—"`, `hasPosition = false` (ou position affichée grisée avec « GPS 12 s »). Ajouter `computedAt` dans `SafetyState` et refuser un état plus vieux que 5 s. Dans `PilotView`, passer `gpsOk: p.gpsOk && app.status.gps`.

### C2 — Marge calculée sur une altitude non calée (ISA 1013) ou de secours, sans que la référence soit affichée
- **Où** : `IosFlightEngine.kt:381-385` (`afterGps` prend `snap.altitudeM` quelle que soit `altitudeRef`) ; `altRefText`, `varioLabel`, `finesseNote` sont calculés (`:478`, `:494`, `:537`) mais **jamais affichés** dans Pilotage (aucune occurrence en Swift hors carte Capteurs de Prévol).
- **Scénario** : QNH 990 hPa, précision GPS > 20 m au démarrage (pas de calage `BARO_GPS`) et départ à plus de 1,5 km du terrain : altitude = altitude pression ISA, environ 190 m trop haute. La marge est surestimée d'autant, sans aucune indication. Idem pour la référence `GPS` (±15-30 m verticaux) ou `OGN`.
- **Correction** : dans `afterGps`, ne calculer la sécurité que si `snap.altitudeRef in {BARO_FIELD, BARO_GPS, GPS}` (refuser `BARO_ISA`, `NONE`) ; afficher `altRefText` en petit sous « Alti. » dans `SafetyBand` (« calé GPS », « GPS »), et en couleur d'avertissement si la référence n'est pas barométrique calée.

### C3 — Alertes désarmées et moteur arrêté si l'enregistrement (REC) n'est pas lancé
- **Où** : `IosFlightEngine.kt:383` (`armed = snap.recording`) ; `AppModel.swift:94` (Lite : départ automatique désactivé par défaut) ; `AppModel.swift:205-210` + `IosFlightEngine.kt:573` (`inFlight = running && core.recording` : en arrière-plan, tout est coupé si l'on n'enregistre pas).
- **Scénario** : le pilote oublie REC (cas nominal en Lite, enregistrement manuel). Aucune alerte vocale « Sous la sécurité » de tout le vol. Il verrouille l'écran : `enterBackground` arrête moteur, capteurs et son du vario.
- **Correction** : faire tourner `TakeoffDetector` en permanence (il ne démarre l'IGC que si `autoTakeoff`) et définir `airborne = detector.phase == FLYING || recording`. `armed = airborne` et `inFlight = running && airborne`. Annoncer « En vol sans enregistrement » une fois.

### C4 — Exceptions Kotlin qui traversent vers Swift (arrêt de l'app, boucle de plantage au lancement)
- **Où** : `IosFlightEngine.kt:256-282` (`rebuildFields`, appelé par `setPack`/`setClub`/`setForecast` depuis Swift, sans `runCatching`) → `terrain.elevationM` (`:263`, `:272`) → `Relief.kt:60` (`require(r > 0)` sur fichier PMTiles tronqué) / `Relief.kt:269` (`Png.decode` sur tuile corrompue). Aussi `IosFlightEngine.kt:474-479`, `:536` : `roundToInt()` lève une exception sur NaN, dans `ui()` appelé à 4 Hz sans protection.
- **Scénario** : stockage presque plein, fichier relief tronqué (ou bit corrompu après vérification SHA). Au lancement, `packs.$active` → `setPack` → `rebuildFields` → exception → arrêt de l'app, **à chaque lancement**, y compris en vol. Une marge NaN (vent de prévision NaN dans le JSON, finesse 0 d'une future saisie libre) fait aussi planter `ui()`.
- **Correction** : (1) entourer `TerrariumTerrain` d'un `SafeTerrain` qui intercepte les erreurs, renvoie `null` et lève un drapeau `reliefFault` publié dans `PilotUi` ; (2) `rebuildFields` dans `runCatching` ; (3) `ui()` = `runCatching { build() }.getOrElse { PilotUi.degraded(it.message) }` ; (4) helper `Double.roundOrNull()` qui renvoie « — » si la valeur n'est pas finie.

---

## Majeur

### M1 — Son du vario perdu définitivement après un appel ou un changement de sortie audio
- **Où** : `Audio.swift:51-61`. Aucune gestion de `AVAudioSession.interruptionNotification` ni de `AVAudioEngineConfigurationChange`. Après une interruption, `running` reste `true` et la garde de `start()` empêche tout redémarrage.
- **Scénario** : appel entrant (ou casque Bluetooth branché ou débranché) en spirale : le vario se tait jusqu'à la fin du vol, sans aucune indication.
- **Correction** : observer les deux notifications ; à la fin d'une interruption ou après un changement de configuration : `setActive(true)`, puis `try engine.start()`. Remplacer le drapeau par `engine.isRunning`. Publier `audioOk` et afficher une pastille « SON » en défaut.

### M2 — Annonces vocales de sécurité muettes quand le son du vario est coupé
- **Où** : `Audio.swift:65-74`, `AppModel.swift:40,116` : la session audio `.playback` n'est activée que par `tone.start()` (son du vario actif).
- **Scénario** : son du vario coupé et commutateur silencieux activé, ou écran verrouillé : `AVSpeechSynthesizer` reste dans la session par défaut (`soloAmbient`), coupée par le mode silencieux et en arrière-plan. « Sous la sécurité » ne sort pas. De plus, une alerte URGENT attend derrière les annonces déjà en file.
- **Correction** : activer la session (`.playback`, mode `.voicePrompt`, `.duckOthers`) dans `startEngine`, indépendamment du son du vario. Pour le niveau 2, appeler `synth.stopSpeaking(at: .immediate)` avant `speak`.

### M3 — Positions en cache ou invalides acceptées comme fraîches
- **Où** : `Sensors.swift:63-74`, `IosFlightEngine.kt:361-375` (`ns = uptimeNs()` = heure de réception, pas l'heure du fix ; `horizontalAccuracy < 0` n'est pas rejeté).
- **Scénario** : au lancement, CoreLocation livre la dernière position connue (le parking, il y a 2 h). Elle est datée « maintenant » : `gpsOk` vrai, marge calculée à 15 km de la vraie position. Les positions livrées par lots au retour au premier plan reçoivent toutes le même horodatage `ns`.
- **Correction** : en Swift, ignorer `l` si `l.horizontalAccuracy < 0 || -l.timestamp.timeIntervalSinceNow > 5`. En Kotlin, `ns = uptimeNs() - ((now - fix.time).inWholeNanoseconds).coerceAtLeast(0)`.

### M4 — Relief inconnu dessiné comme du plat et marge « sans relief » non signalée
- **Où** : `IosFlightEngine.kt:491` (`terrainM ?: r.field.elevationM`), `Safety.kt:216` (`terrainKnown` jamais publié), `reliefLoaded` non affiché dans Pilotage.
- **Scénario** : route qui sort de la couverture du pack (ou pas de pack) : la coupe montre un sol plat à l'altitude du terrain et la marge ignore une crête de 1 200 m, avec le même aspect qu'une marge vérifiée.
- **Correction** : `ProfileDot.terrainM = NaN` si inconnu (trou dans le tracé, pas une valeur inventée) ; publier `reliefChecked = r.terrainKnown`. Afficher « relief non vérifié » sous la marge (Essentiel compris) si faux.

### M5 — Calage « terrain » sur l'altitude du modèle ARPEGE
- **Où** : `IosFlightEngine.kt:207` (`fieldElevationM = { fieldElevation }`) et `:267` (source « modèle ARPEGE ») → `FlightEngineCore.kt:184-187` (calage `BARO_FIELD`).
- **Scénario** : sans pack, sol ARPEGE à 250 m pour un terrain réellement à 120 m. Le baro est calé 130 m trop haut et affiché « calé terrain ». La marge vers le club reste juste (l'erreur se compense), mais elle est fausse de +130 m vers tout autre terrain et vers le relief.
- **Correction** : ne passer à `FlightEngineCore` que les altitudes openAIP ou relief (`fieldElevationM = { fieldElevation.takeIf { fieldElevationSource != "modèle ARPEGE" } }`) ; sinon, calage GPS.

### M6 — Arrêt de REC en mode départ automatique : l'IGC est fermé mais le compteur continue
- **Où** : `IosFlightEngine.kt:335` (`stopChrono` ferme toujours le fichier) et `FlightEngineCore.kt:163-168` (`stopManual` ne fait rien si le départ était automatique, `recorder` reste actif).
- **Scénario** : départ automatique activé, le pilote touche le bouton d'enregistrement en vol. Le fichier est fermé, chaque ligne B suivante échoue en silence (`runCatching`), « IGC n points » continue d'augmenter : le vol est perdu sans signe visible.
- **Correction** : `stopManual` doit aussi couper un enregistrement automatique (`recorder = null`, figer `landingTime`) ; le puits IGC compte ses erreurs d'écriture et `phaseText` affiche « IGC en erreur ».

### M7 — « Chrono lancé » annoncé alors que l'IGC n'a pas pu s'ouvrir
- **Où** : `FlightEngineCore.kt:156-161` (annonce sans vérifier `recorder`), `IosFlightEngine.kt:433-435` (99 fichiers par jour ou disque plein → `null`).
- **Scénario** : disque plein. Le pilote entend « Chrono lancé », mais rien n'est enregistré, et les alertes restent désarmées (lié à C3).
- **Correction** : annoncer « Chrono lancé » seulement si `recorder != null` ; sinon, annonce de niveau 1 « Enregistrement impossible, mémoire pleine ».

### M8 — L'interrupteur DÉMO, accessible en vol, coupe le vol réel
- **Où** : `PilotView.swift:325`, `IosFlightEngine.kt:308-315` (`stop()` ferme l'IGC, `newCore()`/`newSafety()` remettent tout à zéro).
- **Scénario** : appui accidentel dans le menu « ⋯ » en vol : l'IGC est fermé, les vrais capteurs sont ignorés, une marge simulée s'affiche avec seulement une petite pastille DÉMO.
- **Correction** : `.disabled(app.engine.inFlight)` sur le Toggle et refus dans `setDemo(true)` si `inFlight`.

### M9 — Redémarrage de l'app en vol : pas de reprise, alertes jamais réarmées
- **Où** : `Safety.kt:309-311` (`established` exige une marge ≥ 200 m), `WindGliderApp.swift:41-55` (accueil, avertissement, tutoriel, puis `PilotNoticeView`, onglet Prévol par défaut), aucune persistance « vol en cours ».
- **Scénario** : l'app est tuée par manque de mémoire en arrière-plan, à 400 m sol avec une marge de +80 m. Au relancement : plusieurs écrans avant Pilotage, plus d'enregistrement, et les alertes ne s'arment jamais tant que la marge n'atteint pas 200 m.
- **Correction** : persister `flightInProgress` (UserDefaults) au REC ou au décollage. Au lancement, si ce drapeau est présent et que la vitesse dépasse 50 km/h : aller directement à Pilotage, relancer `startManual` (nouveau fichier IGC numéroté « -suite ») et forcer `established = true`.

### M10 — Perte du GPS ou du baro jamais annoncée
- **Où** : `FlightEngineCore.kt:133` (les changements de source ne sont évalués qu'à chaque position GPS, et seulement si `recording`) ; aucune annonce « GPS perdu ».
- **Scénario** : GPS et baro perdus ensemble (application en pause système) : silence complet.
- **Correction** : chien de garde dans `ui()` (4 Hz) : GPS > 10 s → « GPS perdu, marge indisponible » (niveau 1) ; vario `NONE` > 5 s → « Vario indisponible ». Annoncer aussi le rétablissement.

### M11 — Seuil de fraîcheur du baro (1 s) égal à la cadence de CMAltimeter
- **Où** : `FlightEngineCore.kt:201` (`baroFresh` < 1 s), `Sensors.swift:35` (`startRelativeAltitudeUpdates`, environ 1 Hz sur la plupart des iPhone), `IosFlightEngine.kt:518` (`baroOk = baroHz > 3`, donc toujours faux à 1 Hz).
- **Scénario** : la source oscille entre `BARO` et `NONE`/`GPS` selon l'instant où `ui()` est appelé : vario « — » par intermittence, altitude qui saute de référence, annonces « Vario indisponible / rétabli ». **À mesurer sur appareil.**
- **Correction** : seuil de fraîcheur paramétrable (2,5 s sur iOS) ; `baroOk = source in {BARO, BARO_ACCEL}` ; évaluer `CMAltimeter.startAbsoluteAltitudeUpdates` (iOS 15+) ; Kalman réglé pour la cadence réelle.

### M12 — Vent affiché sans son âge ; fenêtre de prévision trop large
- **Où** : `IosFlightEngine.kt:488`, `:537` (`windText` sans âge, alors que `Wind.ageMinutes` existe), `Safety.kt:49` (vent estimé gardé jusqu'à 25 min), `IosFlightEngine.kt:393` (`/3600 > 3` en division entière : accepte une prévision décalée de 3 h 59).
- **Scénario** : vent mesuré il y a 24 min, avant une bascule de brise, affiché « 300°/15 » comme actuel, alors qu'il pèse directement dans la finesse sol.
- **Correction** : ajouter « · 12 min » au-delà de 5 min ; utiliser `abs(secondsBetween(...)) > 3 * 3600` ; publier `windAgeMin`.

### M13 — Alertes coupées par l'heuristique « au sol = vitesse sol < 30 km/h »
- **Où** : `Safety.kt:403` (`onGround = gs < 30`) et `:303-306`.
- **Scénario** : spirale par 45 km/h de vent : sur la branche face au vent, la vitesse sol passe sous 30 km/h, `inCircuit` remet la projection à zéro à chaque tour, et « Marge nulle dans deux minutes » n'est jamais annoncée.
- **Correction** : `onGround = detector.phase != FLYING` (C3), ou vitesse sol < 30 km/h tenue pendant 30 s **et** hauteur sol < 50 m.

### M14 — Déballages forcés sur des données venues du réseau
- **Où** : `PackStore.swift:116` (`URL(string: f.url)!`, URL lue dans le manifeste), `:98`, `:63`.
- **Scénario** : manifeste mal formé (espace ou caractère non encodé dans une URL) : plantage pendant le téléchargement automatique en Wi-Fi.
- **Correction** : `guard let url = URL(string: f.url) else { throw PackError("URL invalide") }`.

### M15 — Minuterie d'affichage suspendue pendant un geste, et effets de bord dans `ui()`
- **Où** : `AppModel.swift:187` (`Timer.scheduledTimer`, mode `.default` : la minuterie ne se déclenche pas pendant un défilement, un menu ou une feuille) ; `IosFlightEngine.kt:453-457` (`ui()` ferme l'IGC et coupe le son ; `sensors()` rappelle `ui()`, `:564`).
- **Scénario** : liste des terrains (`FieldPicker`) ouverte en défilement : marge et vario figés à l'écran le temps du geste.
- **Correction** : `RunLoop.main.add(timer, forMode: .common)` ; déplacer la fermeture de l'IGC à l'atterrissage dans `onGps` ; `ui()` doit être une fonction pure.

---

## Mineur

### m1 — Moyennes spirale et pompe figées pendant une perte du GPS
`FlightEngineCore.kt:138-139`, `:276` : la fenêtre `climbWindow` est indexée sur l'heure des fixes, donc elle n'est jamais purgée sans nouvelle position. L'index « spirale » de la barre vario garde une vieille valeur. Correction : purger aussi par `ns` dans `snapshot()` (et ne rien renvoyer si le dernier point a plus de 5 s).

### m2 — Planeur de la carte fluide toujours dessiné comme valide
`OwnshipPredictor.swift:64-72` : `valid` reste vrai indéfiniment après le premier fix (l'extrapolation est bornée à 3 s, puis le planeur reste figé). Correction : `valid = hasFix && t - t0 < 5`, et icône grisée ou barrée sinon (`FluidMapView.swift:146-149`).

### m3 — Terrain inatteignable à cause du vent exprimé par un nombre arbitraire
`Safety.kt:191` : `eff = 0.5` quand le vent interdit la route, ce qui donne des marges du type « −39 000 ». Cela a l'air d'une mesure. Correction : `GlideResult.windBlocked = true` et afficher « Inatteignable (vent) ».

### m4 — Invariants numériques des instruments
`PfdInstruments.kt:168-169` : `generateSequence(...).takeWhile { it <= altM + 250 }` tourne sans fin si `altM` est infini (`isNaN` est testé, pas `isFinite`). `PilotView.swift:118` teste la validité de l'altitude par comparaison de texte (`p.altText == "—"`). Correction : `require(altM.isFinite())` remplacé par un retour « invalide » ; publier `altKnown: Boolean`.

### m5 — Texte d'arrivée toujours en vert
`PilotView.swift:424` : `arrivalText` est en `PI.good` même pour « arrivée −120 m/sol ». Correction : couleur selon le signe (publier `arrivalAglM`).

### m6 — Couverture de tests du code de vol iOS
Aucun test dans `shared/` pour `IosFlightEngine`. Il manque en `commonTest` :
- GPS périmé (C1) ;
- référence ISA (C2) ;
- armement sans REC (C3) ;
- NaN ou infini dans `ui()` et dans `AltitudeTape` (C4, m4) ;
- `stopChrono` en mode automatique (M6) ;
- vent de face à 30 de finesse et 70 km/h (m3) ;
- terrain à 0 km ;
- altitude sous le terrain ;
- relief corrompu (C4).

Pour un code « plus aéronautique » :
- état dégradé explicite `PilotUi.health: List<Fault>` (GPS, BARO, SON, RELIEF, IGC), dessiné en Swift ;
- âge de chaque donnée publié avec la donnée ;
- un journal circulaire de vol (`CalibrationLog` existe déjà : y ajouter les sources, les pertes de capteurs, les alertes émises et les exceptions interceptées), joint à l'IGC ;
- des préconditions vérifiées aux frontières (`require` en test, valeur bornée et repli en production).

---

## Les 5 corrections à faire en premier

1. **C1** : invalider marge, position et distance dès que `gpsAgeS > 5 s` dans `IosFlightEngine.ui()`, et brancher la pastille GPS sur `p.gpsOk`.
2. **C3** : armer les alertes et garder le moteur en arrière-plan sur « en vol détecté » (détecteur toujours actif), pas sur « REC lancé ».
3. **C4** : `SafeTerrain` + `runCatching` dans `rebuildFields` et `ui()` (aucune exception Kotlin vers Swift, pas de boucle de plantage au lancement).
4. **C2** : pas de marge sur une altitude `BARO_ISA` ; afficher la référence d'altitude sous « Alti. ».
5. **M1 + M2** : gérer interruptions et changements de sortie audio, et activer la session audio pour la voix indépendamment du son du vario (alerte urgente prioritaire).
