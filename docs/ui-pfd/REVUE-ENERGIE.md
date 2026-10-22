# Revue énergie : Wind Glider iPhone, écran de vol (branche ui-pfd-windglider)

Revue en lecture seule, 23/10/2026. Contexte : vols de 3 à 6 h, écran allumé, téléphone sur la planche de bord, souvent au soleil et sans alimentation.
Objectif : consommer le moins possible sans perdre en fluidité ni en sécurité (son du vario réactif, marge à jour).

Les gains sont **estimés** à la lecture du code. Rien n'a été mesuré sur l'appareil : la partie 6 donne la méthode pour le faire, avant et après chaque changement.

---

## 0. À lire d'abord

1. **Défaut fonctionnel : la trace de la carte se fige au bout de 10 minutes.** `FlightEngineCore.kt:142` borne la trace à 600 points (`while (trace.size > 600) trace.removeFirst()`), alors que `FluidMapView.swift:219` ne redessine la trace que si `p.trace.count != traceCount`. Une fois les 600 points atteints, le nombre ne change plus et la carte n'affiche plus les nouveaux points. Corrigez ce défaut en même temps que la recommandation 4 (version de trace au lieu du nombre de points).
2. Le commentaire « 60 i/s » est périmé. La carte tourne déjà à **30 i/s fixes** (`FluidMapView.swift:118`) et l'Info.plist ne contient pas `CADisableMinimumFrameDurationOnPhone`, donc l'app est plafonnée à 60 Hz même sur un écran ProMotion. **Il ne faut pas ajouter cette clé.**
3. Un point compte autant que tout le reste : **l'écran au soleil**, en luminosité maximale. L'app ne règle pas la luminosité, et c'est bien. Le code ne peut agir que sur trois choses : ce qui force le rendu à 60 Hz, l'activité radio (réseau cellulaire) et la chauffe. La chauffe déclenche l'atténuation automatique de l'écran par iOS, ce qui gêne précisément au soleil.

---

## 1. Ce qui tourne en permanence pendant un vol

| Source | Fréquence | Coût probable | Où |
|---|---|---|---|
| `CADisplayLink` de la carte : la caméra `MKMapCamera` est réassignée à chaque image, même si rien n'a bougé ; il continue de tourner quand l'onglet Pilotage n'est pas affiché (la vue n'est pas démontée par la `TabView`) | 30 Hz | moyen (GPU et rendu MapKit) | `FluidMapView.swift:115-193` |
| **Animation implicite du curseur vario** (`.animation(.easeOut(duration: 0.8), value: ms)`) : `ms` change tous les 0,25 s, donc l'animation ne s'arrête jamais et SwiftUI/Core Animation composent l'écran **à 60 Hz en continu** | 60 Hz | **élevé** : elle double la cadence d'affichage imposée par la carte | `PfdInstrumentsView.swift:171` |
| Minuterie `AppModel` : appelle `engine.ui()`, puis `engine.sensors()`, qui refait **deux** `snapshot()` et **un second `ui()`** | 4 Hz | moyen : environ 2 × 600 `TracePoint` copiés, 2 × 600 `TraceDot` créés, `ThermalZoom` calculé deux fois, une trentaine de chaînes formatées, et la collecte mémoire Kotlin/Native sur le fil principal | `AppModel.swift:187-199`, `IosFlightEngine.kt:450-570` |
| `placeAirspaceLabels` : parcourt **tous les sommets de toutes les zones** (jusqu'à 400 polygones) en passant par les `KotlinDouble` (`a.coords[i].doubleValue`) | 4 Hz | **élevé en CPU** : des centaines de milliers d'accès traduits entre Kotlin et Swift chaque seconde | `FluidMapView.swift:217, 267-292` |
| Trace : à chaque nouvelle longueur (1 Hz pendant les 10 premières minutes), `overlapFade` (grille de hachage) est recalculé, puis **tous** les calques de trace sont retirés et recréés (un `MKPolyline` par tranche de 0,5 m/s, souvent plus de 100 calques) | 1 Hz | élevé, et MapKit redessine les tuiles des calques à chaque fois | `FluidMapView.swift:219-224, 294-342` |
| `RouteOverlay` : rectangle englobant `.world` et `setNeedsDisplay()` sans zone précise : toutes les tuiles visibles de ce calque sont redessinées | 4 Hz | moyen | `FluidMapView.swift:226-240, 444-460` |
| Trafic OGN : `update()` refait `UIView.animate` et `configure` (nouvelle image SF Symbol, `sizeToFit`) pour **chaque** aéronef à 4 Hz, alors que l'instantané ne change qu'à 1 Hz. Les ombres `layer.shadow*` sont posées sans `shadowPath`, ce qui impose un rendu hors écran à chaque image de la carte | 4 Hz, et 30 Hz pour les ombres | moyen, proportionnel au nombre d'aéronefs | `FluidMapView.swift:242-255, 547, 553-563, 391` |
| Publication SwiftUI : `pilot`, `sensorsUi` et `status` sont republiés à chaque tour. `status.gps` est réassigné même sans changement. `RootView` lit `app.pilot.recording`, donc toute la `TabView` (Prévol avec ses graphiques, Mes vols) est réévaluée | 4 Hz | moyen | `AppModel.swift:190-193`, `WindGliderApp.swift:46` |
| `ProfileStrip` (Swift Charts) entièrement reconstruit, alors que la coupe ne change qu'à chaque position GPS | 4 Hz | moyen (mise en page des graphiques) | `PilotView.swift:78, 390-431` |
| `CMDeviceMotion` livré sur la file **principale**, avec un appel Kotlin et `Date()` à chaque échantillon | 50 Hz | faible à moyen (50 réveils par seconde du fil principal) | `Sensors.swift:40-49` |
| `AVAudioEngine` actif en continu, même silencieux (le son est coupé entre −1,7 et +0,25 m/s) | flux audio à 22 kHz | faible à moyen (le matériel audio reste éveillé) | `Audio.swift:51-56` |
| Socket APRS d'OGN avec un **rayon de 250 km**, sans filtre de type | flux continu | **élevé en radio** : la radio cellulaire ne s'endort jamais, et en altitude, loin des antennes, elle émet fort | `OgnLiveSession.kt:156`, `OgnTraffic.kt:291` |

Ce qui est déjà bien fait : la carte est à 30 i/s, l'interface est sombre (`preferredColorScheme(.dark)`), les capteurs, le son et OGN sont coupés en arrière-plan hors vol, la météo n'est chargée qu'au sol et une seule fois, et le relevé de vols se lit hors du fil principal.

## 2. Capteurs

- `desiredAccuracy = kCLLocationAccuracyBestForNavigation` (`Sensors.swift:21`). Apple réserve ce réglage aux appareils sur secteur : il ajoute la fusion avec d'autres capteurs. `kCLLocationAccuracyBest` livre les mêmes positions GNSS à 1 Hz.
- `distanceFilter = kCLDistanceFilterNone` et `activityType = .airborne` : **à garder**. L'IGC et la marge ont besoin de 1 Hz, et `.airborne` est le bon profil.
- `pausesLocationUpdatesAutomatically = false` : à garder en vol. En revanche, la localisation démarre dès le lancement (`WindGliderApp.swift:21`, `.task { app.startEngine() }`), même dans Prévol au sol, avec la précision maximale, les capteurs de mouvement à 50 Hz et OGN. Au terrain, avant le vol, l'app consomme donc déjà autant qu'en vol.
- Baromètre : `startRelativeAltitudeUpdates`, dont la cadence est fixée par le système. Rien à gagner de ce côté.
- `CMDeviceMotion` à 50 Hz sur `.main` : c'est la fusion baromètre et accéléromètre du vario, plus le taux de virage du prédicteur. On peut le garder, mais il faut sortir les échantillons du fil principal (voir la recommandation 14).
- Arrière-plan pendant un vol enregistré : la localisation et l'audio restent actifs, ce qui est correct. Mais **la minuterie à 4 Hz, `ui()` et OGN continuent aussi**, écran éteint, sans que personne ne voie l'écran.

## 3. Réseau en vol

- **OGN** : le filtre `r/lat/lon/250`, un keepalive toutes les 180 s et une reconnexion après 5, 10, 30, 60 puis 120 s. Avec 250 km de rayon dans le sud de la France en été, il arrive des trames plusieurs fois par seconde. La radio LTE ou 5G reste en permanence en état connecté. En altitude, la couverture est intermittente, d'où des reconnexions et une puissance d'émission élevée. **C'est probablement la plus grosse consommation que l'app contrôle après l'écran.** Le rayon de détail n'est que de 60 km (`DETAIL_KM`) : le reste du flux est reçu, analysé puis en grande partie jeté.
- La météo (PRECOG) et les packs ne sont chargés qu'au sol, à la demande. Pas de requêtes périodiques en vol : c'est bien.
- Fond « satellite » (`MKHybridMapConfiguration`, `FluidMapView.swift:58-61`) : les tuiles d'images sont plus lourdes que les tuiles vectorielles du plan. À 100 km/h, de nouvelles tuiles arrivent en continu.

## 4. Écran

- L'interface est déjà sombre. Les panneaux lavande `#2B2A3A` sont un peu moins économes que du noir pur sur un écran OLED, mais ils couvrent environ 25 % de l'écran : le gain serait faible, et la charte Godspeed (« jamais noir pur ») l'exclut. **Ce n'est pas un levier prioritaire.**
- Le levier réel est la **cadence de composition** : 60 Hz permanents à cause du curseur vario, au lieu de 30 Hz. Un écran LTPO peut descendre sa fréquence quand rien ne bouge, mais seulement si l'app ne demande pas d'images.
- `isIdleTimerDisabled = true` quand Pilotage apparaît et `false` quand il disparaît (`PilotView.swift:111-112`) : c'est correct en vol. Au sol, Pilotage ouvert sans enregistrement garde l'écran allumé indéfiniment (voir la recommandation 18).
- Chauffe : au soleil, l'état `.serious` ou `.critical` fait baisser la luminosité par iOS. Moins de calcul signifie moins de chauffe, donc un écran plus lisible plus longtemps.

---

## 5. Recommandations (19), classées par gain et par risque

Légende : gain **E** élevé, **M** moyen, **F** faible ; risque **0** nul, **f** faible, **m** moyen.

### 1. OGN : rayon de 50 km en vol et pause écran éteint (gain E, risque f)
- `OgnLiveSession.kt:156` : passer `RADIUS_KM` de 250 à `60`, la valeur de `DETAIL_KM`, ou recevoir le rayon en paramètre : 250 km en Prévol pour la vue d'ensemble, 50 km en vol.
- `AppModel.swift:205` : dans `enterBackground()`, appeler `traffic?.stop()` **aussi en vol**. Personne ne regarde la carte écran éteint, et l'anticollision reste le rôle du FLARM. Relancer OGN dans `enterForeground()`, où `startTraffic()` est déjà appelé au tour suivant.
- Option : en mode Économie d'énergie ou sous 20 % de batterie, couper OGN et afficher une pastille « OGN en pause ».
- Option à valider sur aprs.glidernet.org : ajouter au filtre un filtre de type qui exclut les balises des récepteurs.

### 2. Curseur vario : ne plus forcer 60 Hz en continu (gain E, risque f)
`PfdInstrumentsView.swift:171` : supprimer `.animation(.easeOut(duration: 0.8), value: ms)`. Lisser localement à 30 i/s au plus, et seulement pendant que le curseur bouge :
```swift
TimelineView(.animation(minimumInterval: 1.0 / 30, paused: abs(shown - ms) < 0.02)) { _ in
    Capsule()…offset(y: -CGFloat(VarioPalette.shared.fraction(ms: shown)) * barH + 2.5)
}
// shown suit ms par lissage exponentiel (τ ≈ 0,5 s) dans un @State, mis à jour à chaque image
```
Autre solution : déplacer le curseur depuis le `CADisplayLink` de la carte, déjà à 30 Hz. Le chiffre et le son restent bruts, comme aujourd'hui, donc la réactivité perçue ne change pas.

### 3. Étiquettes des zones : sommets précalculés, 1 Hz, tri par zone visible (gain E, risque f)
`FluidMapView.swift:267-292` :
- quand `ids != airspaceIds` (ligne 211), convertir **une seule fois** chaque zone en `[CLLocationCoordinate2D]` ou en `[MKMapPoint]`, stockés dans `AirspaceOverlay` ;
- dans `placeAirspaceLabels`, ignorer d'emblée les zones dont le `boundingMapRect` n'intersecte pas `v.visibleMapRect` élargi de 50 % ;
- ne l'appeler qu'une fois par seconde, ou quand l'avion a bougé de plus de 200 m.

On supprime ainsi des centaines de milliers d'accès `KotlinDouble.doubleValue` par seconde sur le fil principal.

### 4. Trace : mise à jour incrémentale, un seul calque, et correction du gel à 600 points (gain E, risque m)
- Côté Kotlin (`IosFlightEngine.kt:512`) : ne plus copier la trace à 4 Hz. Exposer `traceVersion` (l'horodatage du dernier point) et `fun traceSince(version): List<TraceDot>` ; `PilotUi.trace` n'est rempli que si la version a changé.
- Côté Swift (`FluidMapView.swift:219-224`) :
  - comparer `traceVersion`, pas `count` (c'est ce qui corrige le gel) ;
  - remplacer les N `MKPolyline` par **un `MKOverlay` unique avec un renderer dédié**, qui dessine les segments colorés et le liseré ;
  - à chaque nouveau point, appeler `renderer.setNeedsDisplay(newSegmentRect.insetBy(-pad))`, et ne redessiner l'ensemble qu'une fois toutes les 10 s, quand `overlapFade` est recalculé (l'estompage des tours n'a pas besoin d'être à jour à la seconde près).

C'est le changement le plus délicat (rendu par tuiles), mais il supprime à la fois les recréations de calques à 1 Hz et le défaut décrit en 0.1.

### 5. `CADisplayLink` : ne demander une image que si quelque chose bouge (gain M à E, risque f)
`FluidMapView.swift:141-193` :
- en tête de `frame()` : `if v.window == nil { return }`, ou mieux, `link.isPaused = (v.window == nil)` dans `didMoveToWindow` ou via `onAppear`/`onDisappear` de Pilotage. Aujourd'hui, la carte continue de tourner à 30 Hz quand on regarde Prévol ;
- garder la caméra précédente et ne pas réassigner `v.camera` si le centre a bougé de moins de 0,3 pt, le cap de moins de 0,05° et la distance de moins de 0,1 %. Au sol, ou planeur arrêté, on passe alors à 0 image par seconde ;
- en mode LIBRE (`!control.follow`), MapKit gère lui-même les gestes. Le lien peut descendre à 10 Hz (seule la flèche du planeur tourne) : `link.preferredFrameRateRange = .init(minimum: 8, maximum: 15, preferred: 10)` ;
- garder 30 Hz en suivi ; la fluidité perçue reste identique.

### 6. `sensors()` ne doit plus refaire `ui()` ni deux `snapshot()` (gain M, risque 0)
- `IosFlightEngine.kt:553` : `ui().varioLabel` reconstruit **tout** `PilotUi`, trace comprise. Extraire `varioLabel(snap.source)` dans une fonction et réutiliser un seul `snapshot`.
- `AppModel.swift:192` : ne calculer `sensorsUi` qu'à 1 Hz (`tick % 4 == 0`) **et** seulement quand la carte Capteurs de Prévol est affichée, avec un indicateur positionné par `onAppear`/`onDisappear` de cette carte.

Résultat : environ deux fois moins de calcul et d'allocations Kotlin par tour.

### 7. Route vers le terrain : un calque Core Animation au lieu d'un rendu par tuiles (gain M, risque f)
`FluidMapView.swift:226-240, 444-460` : remplacer `RouteOverlay`/`RouteRenderer` par une `CAShapeLayer` en pointillés, ajoutée au-dessus de la carte, dont le chemin est recalculé dans `frame()` avec `v.convert(coord, toPointTo: v)`. Le tracé coûte quasiment rien au GPU, suit le planeur prédit à chaque image (c'est même plus fluide qu'aujourd'hui), et `setNeedsDisplay()` sur `.world` à 4 Hz disparaît. À défaut, limiter le `boundingMapRect` au rectangle entre le départ et le terrain, et appeler `setNeedsDisplay(union(ancien, nouveau))`.

### 8. Annotations OGN : mise à jour seulement si l'instantané change, et ombres moins coûteuses (gain M, risque f)
- `FluidMapView.swift:242` : `guard traffic !== lastTraffic else { skip }`. L'instantané est un objet Kotlin, il suffit de comparer les identités. On passe de 4 Hz à 1 Hz.
- `PlaneView.configure` (lignes 553-563) : mettre en cache les deux images de flèche (blanche, et orange en spirale) dans des `static let`, et ne refaire `sizeToFit` que si le texte change.
- Ombres (lignes 391 et 547) : fixer `layer.shadowPath`, ou dessiner le halo avec `NSAttributedString` (`.strokeColor` / `.shadow`), ou poser `layer.shouldRasterize = true` avec `rasterizationScale = UIScreen.main.scale` sur l'étiquette, qui ne tourne pas. Sans `shadowPath`, chaque ombre exige un rendu hors écran à 30 Hz.
- Ne garder que les aéronefs à moins de 60 km, ou dans le rectangle visible élargi.

### 9. SwiftUI : publier séparément les valeurs rapides et les valeurs lentes (gain M, risque f à m)
- Sortir `pilot` d'`AppModel` dans un `@Observable final class FlightModel` (iOS 17 est la cible minimale), observé par `PilotView` seulement. `RootView` (`WindGliderApp.swift:46`) ne doit lire qu'un booléen `recording`, publié uniquement quand il change. Prévol et Mes vols ne seront plus réévalués 4 fois par seconde.
- `AppModel.swift:193` : `let g = …; if status.gps != g { status.gps = g }`. Faire de même pour `trafficSnap` : ne republier que si l'instantané est nouveau.
- `ProfileStrip` (`PilotView.swift:390`) : rendre la vue `Equatable` sur (`profileVersion`, altitude arrondie à 5 m, `requiredAltM`, `arrivalAltM`, `routeKm`) et appliquer `.equatable()`. Le graphique n'est alors reconstruit qu'à environ 1 Hz.
- Dans `body` (`PilotView.swift:65`), lire `ProcessInfo…arguments` et `UserDefaults` une seule fois, dans une constante statique.

### 10. Au sol, ne pas démarrer le moteur en mode vol (gain M au terrain, risque f)
`WindGliderApp.swift:21` et `AppModel.swift:181` : au lancement et dans Prévol, démarrer la localisation en `kCLLocationAccuracyHundredMeters` (club le plus proche, pastilles), sans `CMDeviceMotion` ni son. Basculer en **mode vol** (précision `Best`, mouvement à 50 Hz, son, OGN rayon vol) à l'ouverture de Pilotage, au REC, ou dès que la vitesse dépasse 30 km/h (décollage automatique). C'est sûr : la marge n'est calculée qu'en Pilotage, et le décollage automatique reste détecté.

### 11. Arrière-plan pendant un vol enregistré : tour léger à 1 Hz (gain M écran éteint, risque f)
`AppModel.swift:205-210` : si `engine.inFlight`, remplacer la minuterie à 4 Hz par une minuterie à 1 Hz qui n'appelle qu'un `engine.tick()` léger. Ce `tick()` reprend la détection de `LANDED` avec la fermeture de l'IGC, et la coupure du son sans source (ce que fait `ui()` aux lignes 453-457) ; il n'appelle ni `ui()`, ni `PilotUi`, ni trafic. La sécurité (`afterGps`) et le son vario sont pilotés par les capteurs : ils restent intacts. Revenir à 4 Hz dans `enterForeground()`.

### 12. Réagir à la chauffe et au mode Économie d'énergie (gain M au soleil, risque f)
Nouveau code dans `AppModel` : observer `ProcessInfo.thermalStateDidChangeNotification` et `.NSProcessInfoPowerStateDidChange`.
- `.serious`, ou mode Économie d'énergie : carte à 20 i/s, OGN en pause ou rayon de 25 km, fond « plan » au lieu de « satellite », `sensorsUi` arrêté.
- `.critical` : en plus, la carte ne suit plus qu'à 10 i/s.

Le vario sonore, la marge et l'IGC ne sont **jamais** dégradés. Une pastille « Économie » permet au pilote de savoir pourquoi la carte est moins fluide.

### 13. GPS : `Best` au lieu de `BestForNavigation` (gain F à M, risque f)
`Sensors.swift:21` : `location.desiredAccuracy = kCLLocationAccuracyBest`. On garde `distanceFilter = kCLDistanceFilterNone`, `.airborne` et `pausesLocationUpdatesAutomatically = false`. La cadence reste à 1 Hz (le GNSS de l'iPhone n'en livre pas davantage), et l'IGC et la marge ne changent pas. À vérifier avec l'instrument « Location Energy Model » (partie 6).

### 14. `CMDeviceMotion` hors du fil principal (gain F à M, risque m)
`Sensors.swift:41-42` : livrer les échantillons sur une `OperationQueue` dédiée (`maxConcurrentOperationCount = 1`, `qualityOfService = .userInitiated`). Calculer là le taux de virage, cumuler les échantillons, et les envoyer au moteur sur `.main` **par paquets de 5** (10 Hz), chacun avec son horodatage d'origine. La fusion du moteur utilise les horodatages, donc le résultat est identique, avec 10 réveils du fil principal par seconde au lieu de 50. Ne passer à 25 Hz qu'après avoir mesuré la réactivité du vario (le délai entre l'échelon de montée et le premier bip, en vol démo avec rejeu des capteurs). Remplacer `Date()` par `m.timestamp` (ligne 48).

### 15. `ui()` en Kotlin : séparer les champs rapides des champs lents (gain F à M, risque f)
`IosFlightEngine.kt:450-544` : calculer `ThermalZoom.view` (ligne 468), `profile` (ligne 491) et les textes de terrain, de vent et de fréquence dans `afterGps()`, à 1 Hz, et les garder en cache. À 4 Hz, ne recalculer que le vario, l'altitude, le chrono et la marge. Moins d'allocations, donc moins de passages du ramasse-miettes Kotlin/Native sur le fil principal.

### 16. Fond « satellite » : le proposer, ne pas l'imposer en vol (gain F à M en réseau, risque 0)
`FluidMapView.swift:58-61` : le plan standard sombre est vectoriel, plus léger en données et en mémoire. Mettre « Fond satellite » par défaut à non, ou revenir au plan en mode Économie (recommandation 12). Rappeler dans le guide qu'un survol préalable de la zone en Prévol remplit le cache de tuiles de MapKit.

### 17. Son du vario : mesurer avant de toucher (gain F à M, risque m)
`Audio.swift:51-56` : le moteur audio tourne en continu, même silencieux. Option : passer en `engine.pause()` après 20 s de silence continu (zone neutre), puis appeler `start()` dès que `set(v)` sort de la zone neutre. La reprise prend environ 20 à 80 ms : à valider à l'oreille. **Ne pas le faire si la mesure montre un gain négligeable**, car la réactivité du son est une exigence de sécurité. Sans lien avec l'énergie mais à corriger : `NSLock` dans le bloc de rendu temps réel (ligne 18) risque une inversion de priorité. Préférer un `Double` atomique (`Synchronization.Atomic` ou `OSAllocatedUnfairLock` avec `withLockIfAvailable`).

### 18. Mise en veille au sol (gain variable, risque f)
`PilotView.swift:111` : garder `isIdleTimerDisabled = true` pendant tout le vol. Au sol (pas de REC et vitesse sous 10 km/h pendant plus de 10 min), le remettre à `false`, et le repasser à `true` dès le REC ou dès que la vitesse dépasse 30 km/h. On évite ainsi un écran allumé une heure dans le cockpit avant le remorquage.

### 19. Petites allocations à chaque image (gain F, risque 0)
`FluidMapView.swift:146, 182-183` : `Date()` est créé à chaque image, de même que deux objets `CLLocation`, avec deux appels à `convert`, pour calculer les mètres par point. Utiliser `CACurrentMediaTime()` avec un décalage connu, et calculer les mètres par point directement depuis `camDistance` et `v.bounds.height`, sans `convert` (la carte est vue à la verticale, `pitch` 0). C'est sans risque, et cela fait du travail en moins à 30 Hz.

### Ordre conseillé
D'abord **6, 3, 2, 8, 5 et 1**. Ce sont de petits changements, faciles à vérifier, et ils devraient apporter l'essentiel du gain en calcul. Viennent ensuite **4** (avec la correction du gel de la trace), **7, 9, 11 et 12**. En dernier, **13, 14 et 17**, à décider sur mesure.

---

## 6. Mesurer sur l'iPhone branché (ligne de commande)

**À mesurer sur un build Release.** Le framework Kotlin/Native en Debug est beaucoup plus lent et fausserait le profil. Pour les essais répétables, utilisez un vol démo ; l'argument `-demoFlight` est DEBUG seulement, donc il faut soit une variante Release qui l'accepte, soit le mode DÉMO activé à la main dans le menu « ⋯ ».

Identifier l'iPhone :
```sh
xcrun devicectl list devices            # repérer l'UDID de l'iPhone (Reality = physical)
UDID=<UDID-de-l-iPhone>
```

Profil d'énergie, la référence (instrument Power Profiler de Xcode 27, présent dans `xcrun xctrace list templates`) :
```sh
xcrun xctrace record --template 'Power Profiler' --device "$UDID" --attach WindGlider \
  --time-limit 10m --run-name avant --output ~/Desktop/wg-energie.trace
# après le changement, même scénario, dans le même fichier :
xcrun xctrace record --device "$UDID" --attach WindGlider --time-limit 10m \
  --append-run --run-name apres --output ~/Desktop/wg-energie.trace
```

Vue d'ensemble en une seule passe (CPU, affichage, chauffe, coût GPS, réseau). Ces instruments figurent tous dans `xcrun xctrace list instruments` :
```sh
xcrun xctrace record --template 'Activity Monitor' \
  --instrument 'Display' --instrument 'Thermal State' \
  --instrument 'Location Energy Model' --instrument 'Network Connections' \
  --device "$UDID" --attach WindGlider --time-limit 15m --output ~/Desktop/wg-global.trace
```

Points chauds du CPU (recommandations 3, 4, 6, 15) et cadence d'images et à-coups (2, 5) :
```sh
xcrun xctrace record --template 'Time Profiler'    --device "$UDID" --attach WindGlider --time-limit 2m  --output ~/Desktop/wg-cpu.trace
xcrun xctrace record --template 'Animation Hitches' --device "$UDID" --attach WindGlider --time-limit 2m  --output ~/Desktop/wg-hitches.trace
xcrun xctrace record --template 'SwiftUI'          --device "$UDID" --attach WindGlider --time-limit 1m  --output ~/Desktop/wg-swiftui.trace   # réévaluations de body par seconde (9)
xcrun xctrace record --template 'Network'          --device "$UDID" --attach WindGlider --time-limit 10m --output ~/Desktop/wg-net.trace      # OGN (1)
```

Lire une trace sans l'interface :
```sh
xcrun xctrace export --input ~/Desktop/wg-global.trace --toc > /tmp/toc.xml     # liste des tables (schémas)
xcrun xctrace export --input ~/Desktop/wg-global.trace \
  --xpath '/trace-toc/run[@number="1"]/data/table[@schema="<schéma relevé dans toc.xml>"]' > /tmp/table.xml
open ~/Desktop/wg-energie.trace                                                  # ou l'ouvrir dans Instruments
```

**Limite de la mesure branchée** : le câble alimente le téléphone, si bien que la courbe de batterie ne veut rien dire, et la chauffe due à la charge s'ajoute. Pour un chiffre fiable de consommation :
1. Enregistrer une trace sur l'appareil, sans câble : Réglages > Développeur > Performance Trace, profil d'énergie, pendant 30 min de vol démo. Puis la rapatrier et l'ouvrir dans Instruments.
2. Faire un test de décharge A/B : batterie entre 80 et 90 %, luminosité fixe à 50 % (luminosité automatique coupée), mode Avion **sans** puis **avec** données cellulaires, 30 min en démo sur Pilotage. Noter le niveau toutes les 5 min. Pour l'avoir dans la trace, ajouter un journal temporaire avec `os_log` (`UIDevice.current.batteryLevel`, `ProcessInfo.processInfo.thermalState`, `isLowPowerModeEnabled`) toutes les 60 s, et l'enregistrer avec `--instrument os_log`.
3. Cibles indicatives à confirmer par la mesure :
   - CPU de l'app en vol suivi : sous 15 % en moyenne ;
   - composition de l'écran : 30 Hz en suivi, 0 à 10 Hz au repos ou en mode LIBRE ;
   - pas de passage à l'état thermique `.serious` en 30 min à l'intérieur ;
   - radio cellulaire : périodes de repos visibles dans « Network Connections » avec un rayon OGN de 50 km.
