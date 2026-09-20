# HANDOFF — état du projet (GLIDY, ex-Glider Copilot)

## Session 8 — Robustesse vol long + préparatifs Play Store (20/09/2026)

### Livré
- **Robustesse vol long** (demande du plan S8 : « tenir 4 h de vol ») — analyse complète de
  `FlightService`/`FlightEngine`/`AndroidManifest.xml` :
  - **Vraie faille trouvée et corrigée** : sans `android:stopWithTask="false"` sur `<service
    android:name=".FlightService">`, l'OS arrêtait le service (donc tout le suivi : capteurs, GPS,
    IGC) dès que le pilote — ou une appli de nettoyage OEM — swipait GLIDY hors des tâches récentes,
    même avec le service de premier plan actif. C'est le risque réel identifié pour un vol de
    plusieurs heures, pas la reprise après un simple kill mémoire du process : celle-ci fonctionnait
    déjà correctement (`START_STICKY`, `TakeoffDetector` se ré-arme en ~3 s de vitesse GPS soutenue,
    ouvre un nouveau fichier IGC en continuation).
  - Conseil batterie ajouté dans Prévol (« Capteurs & vols ») : suggérer « Sans restriction » côté
    optimisation de batterie pour un vol long, sans utiliser l'API `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
    (usage restreint par les règles Play Store, risque de rejet pour une app de ce type — le service de
    premier plan `FOREGROUND_SERVICE_LOCATION` suffit et est déjà largement exempté du Doze).
  - **Audit hors ligne des fonctions de sécurité** : `grep` confirmant zéro référence réseau dans
    `core/domain` (dont `domain/safety/Safety.kt`) — conforme à la règle CLAUDE.md, rien à changer.
- **Signature de release câblée** (`app/build.gradle.kts`) : `signingConfig` "release" lu depuis 4
  secrets d'environnement (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), repli
  sûr sur la signature debug tant qu'ils sont absents — CI existante inchangée. Génération réelle du
  keystore **volontairement non faite par Claude** (le mode auto de ce sandbox bloque la création
  autonome de secrets cryptographiques) : procédure complète pour JB dans `docs/PLAY-STORE.md`.
- **Politique de confidentialité** : `docs/privacy/index.html`, autonome, prête pour GitHub Pages
  (activation manuelle nécessaire, `api.github.com` bloquée depuis le sandbox — voir `docs/PLAY-STORE.md`).
- **Brouillon fiche Play Store + formulaire Sécurité des données** (FR) : `claude/play-store-listing.md`
  dans le projet claude.ai — textes courts/longs, tableau Data Safety, justification du service de
  premier plan, icône 512×512 générée depuis l'icône adaptative existante.
- Version 0.8.0.

### Vérifié
- CI verte (un aller-retour : erreur de compilation Gradle Kotlin DSL — `java.io.File`/`java.util.Base64`
  en référence qualifiée ne résolvaient pas dans `build.gradle.kts`, corrigé par imports explicites en
  tête de fichier).
- Aucune régression visible sur les captures émulateur (mêmes écrans que V7.3, aucun élément UI modifié
  visuellement à part le texte batterie ajouté dans Prévol).

### Limites / bloquant côté JB
- Compte Google Play Console **organisation** Neutron Star (D-U-N-S) : pas créé, hors du champ possible
  pour cette session (nécessite une action de JB, potentiellement des documents d'entreprise).
- Keystore d'upload : pas généré (cf. ci-dessus) — JB (ou une session future avec son accord explicite)
  doit exécuter la commande `keytool` donnée dans `docs/PLAY-STORE.md` §1 et configurer les 4 secrets
  GitHub.
- GitHub Pages : pas activé (toggle Settings, impossible depuis le sandbox).
- Fiche Play Store et Data Safety : brouillon prêt, **à valider par JB** avant toute soumission —
  rien n'a été soumis à Google.
- Pas de bannière (feature graphic 1024×500) ni de sélection finale des captures — pas bloquant pour
  une première soumission.

## Session 7.3 — Mode calibration : capture des données brutes baro/accél./GPS (19/09/2026)

### Livré
- **Demande de JB** : la difficulté à venir n'est pas la fonctionnalité, mais le calibrage de la fusion
  baromètre + accéléromètre (le GPS et OGN servent surtout de référence indépendante pour vérifier, pas
  de source à fusionner). Plutôt que de deviner les réglages du filtre, JB veut un mode qui enregistre
  les données brutes pendant de vrais vols d'essai, pour un réglage hors ligne après coup.
- **Toggle « calib »** (Pilotage, à côté de « démo », `MapOverlays` dans `FlightScreen.kt`) : bascule
  manuelle, indépendante du vol détecté (au sol ou en vol). `FlightControls.setCalibration(Boolean)`,
  état `FlightLive.calibrationOn`/`calibrationSamples`.
- **Journal de calibration** (`core:domain/flight/CalibrationLog.kt`, nouveau) : CSV compact réutilisant
  le type `SensorSample` du banc de rejeu existant (`SensorReplay`) — baromètre brut (~25 Hz),
  accélération verticale **déjà projetée** (l'entrée réelle de `VarioFilter`, pas les 3 axes ni la
  gravité, pour rester léger), GPS (~1 Hz). `CalibrationRecorder` écrit au fil de l'eau comme
  `IgcRecorder` ; `CalibrationLog.parse()` relit un journal en `List<SensorSample>`, directement
  rejouable dans `VarioFilter` — testé (`CalibrationLogTest.replayedLogDrivesTheSameFilterAsLiveCalls`).
- **FlightEngine.kt** : hooks dans `sensorListener` (baro, accélération verticale déjà calculée) et
  `onLocation` (GPS). Fichier ouvert/fermé indépendamment de l'IGC (`calib/`, distinct de `igc/`),
  compressé en **gzip à la fermeture** pour rester léger à transmettre (quelques Mo/heure), vidé (flush)
  à 4 Hz sur la boucle de publication plutôt qu'à chaque échantillon (baro+accél. tournent jusqu'à 75 Hz
  combinés). Reprend un enregistrement resté armé après un arrêt/relance du moteur.
- **Partage** : Prévol → « Capteurs & vols » → « Journaux de calibration » (`SensorsCard.kt`), même
  mécanisme `share()`/FileProvider que les IGC. **Piège trouvé en relisant `file_paths.xml`** : le
  fichier ne déclarait que les racines `igc/`/`igc-rejeu/` — sans y ajouter `calib/`, le partage aurait
  échoué en silence (exception avalée par `getOrNull()`).

### Vérifié
- CI verte (un aller-retour : `core:domain:compileTestKotlin` a d'abord échoué sur deux erreurs
  — `VarioFilter.onBaroAltitude` attend une altitude, pas la pression brute, et `assertEquals` sur des
  `Double?` veut des non-null explicites — corrigées, sans rien changer côté production).
- Tests JVM nouveaux (`CalibrationLogTest`) : aller-retour écriture/lecture sans perte (baro, accél.,
  GPS complet et avec champs optionnels absents), en-tête et lignes vides ignorés au parsing, et le test
  de bout en bout qui rejoue un journal dans `VarioFilter` et vérifie l'identité avec un enregistrement
  en direct.
- Captures émulateur : bouton « calib » visible à côté de « démo » sur Pilotage, sans chevauchement.
  Aucune exception ni avertissement dans le logcat.
- Livrables : `Planneur APP/Session 7.3/` (APK 0.7.3, captures, LISEZ-MOI).

### Limites / à faire
- Le banc de captures CI ne simule pas d'appui sur le toggle : aucune capture ne montre la liste
  « Journaux de calibration » remplie ni le fichier en cours d'écriture — seul le mécanisme est couvert,
  par les tests automatiques.
- N'enregistre qu'en mode téléphone (PHONE) : rien en mode démo/rejeu/suivi, qui n'ont pas de vrais
  capteurs à calibrer.
- Le prochain pas, quand JB aura transmis un premier journal réel : rejouer le fichier dans
  `VarioFilter` avec plusieurs réglages (bruit baro/accél., dérive du biais) et comparer au GPS pour
  affiner les constantes par défaut de `Vario.kt`.

## Session 7.2 — Charte aéronautique, mode clair, RTE, NOTAM, écran d'accueil (19/09/2026)

### Livré
- **Couleurs aéronautiques** : nouveau jeton `heading` (mauve) dans `core:designsystem/Theme.kt`, réservé au **cap** (haut droit de la
  zone de sécurité) et au **vecteur de retour** sur la carte (jamais réutilisé ailleurs — le badge AUTO/MANU, l'onglet actif et la flèche
  de vent gardent le jeton `route` vert historique). **Distance** passée au jeton `ink` (blanc/quasi-noir).
- **Mode clair** pour Prévol, Check-lists et Carte (`GlidyLightColors` + `GlidyAdaptiveTheme` dans Theme.kt, préférence `lightMode` dans
  `UserPreferences`/DataStore) : togle unique, partagé, sous l'en-tête de chaque écran. **Pilotage reste noir, obligatoire** (thème racine
  `GlidyTheme` inchangé). La carte hors ligne est construite deux fois (`buildFlightMapConfig`/`mapPalette` dans `AppRoot.kt`, désormais
  des fonctions pures) : palette sombre pour Pilotage, palette qui suit le mode pour Carte.
- **Mode RTE** (`feature:flight/FlightMap.kt`) : `MapOrientation.TRACK` (déjà étiqueté « RTE », toujours orienté au cap) existait dans le
  contrôleur depuis une session précédente mais **n'était câblé à aucun bouton** — corrigé, second `RoundTool` à côté du recentrage
  (AUTO → N↑ → RTE).
- **NOTAM** (`feature:prevol/OfflineMapCards.kt` → `NotamCard`) : carte en bas de Prévol, lien vers **SOFIA-Briefing** (DGAC). Aucun flux
  NOTAM interrogé par l'app (sécurité sans dépendance réseau) — accès direct à la source qui fait foi.
- **Écran d'accueil** (`AppRoot.kt` → `SplashScreen`) : logo écureuil fourni par JB sur fond bleu (~1,6 s), puis l'avertissement habituel.
- Version 0.7.2.

### Audit lecture seule (demande JB) — terrains de repli
Candidats = pack openAIP local (filtre `type !in {4,7,8,10}` : héliports, fermé, hydrobase — codes vérifiés contre l'énumération officielle
openAIP, corrects) + terrain du club. Deux points signalés à JB, **rien changé** : (1) une base militaire, un terrain ULM, une piste
agricole ou un altiport restent des candidats AUTO comme les autres, sans distinction ; (2) altitude manquante à la fois dans openAIP et
le relief local → retombe silencieusement à 0 m au lieu de signaler la donnée manquante (marge alors trop optimiste). Pas de terrain
fantôme à (0°, 0°) possible (géométrie invalide ignorée au chargement).

### Vérifié
- CI verte (deux corrections trouvées sur les captures CI avant livraison : un oubli de câblage `@Composable`, puis le togle mode clair qui
  chevauchait le titre de chaque écran).
- 20 captures émulateur. Livrables : `Planneur APP/Session 7.2/` (APK 0.7.2, captures, LISEZ-MOI).

### Limites / à faire
- Pas de capture dédiée de l'écran d'accueil ni du mode clair activé (banc de captures inchangé) — à vérifier en vol par JB.
- Lien NOTAM : recherche générale SOFIA-Briefing, pas de fiche pré-remplie pour le terrain du club.
- Audit des terrains de repli : en attente de décision de JB.


## Session 7.1 — Pilotage allégé, carte au format FLARM, suivi depuis la carte (18/09/2026)

### Livré
- **Masquage, pas suppression** (`feature:flight/UiMask.kt`) : drapeaux booléens gardant intacts le code et les calculs des éléments
  masqués (ligne de tendance, titre/légende du profil de retour, historique d'altitude, ancienne pastille de trafic). **Gardé** : le graphe
  du profil lui-même (seule vue du relief franchi). Toute la place rendue va à la carte (≈ 40 % → 55 % de la hauteur d'écran).
- **Distance et cap réorganisés** : distance au centre, 34 sp (le double de l'origine). Bug de largeur trouvé sur les captures CI et corrigé
  avant livraison (calage final : marge 44 sp, distance 34 sp, colonne finesse 128 dp, ALT/SÉCU empilés).
- **Symbologie FLARM** sur l'onglet Carte : flèche orientée à la route + immatriculation complète, à la place de la pastille à deux lettres
  (gardée dans le code, masquée par `UiMask`).
- **Bouton SUIVRE** sur la fiche d'un aéronef (Carte) : bascule en suivi central sur Pilotage et change d'onglet automatiquement
  (`GliderRepository.followAddress`).
- Version 0.7.1.

### Vérifié
- Captures émulateur : trois colonnes complètes sur 360 dp, symbologie FLARM lisible, bouton SUIVRE testé.
- CI verte. Livrables : `Planneur APP/Session 7.1/` (APK 0.7.1, 23 captures, LISEZ-MOI).

### Limites / à faire
- Incident de livraison sans rapport avec le code : APK d'abord nommé hors convention (`glidy-0.7.1.apk` au lieu de
  `glidy-v0.7.1-debug.apk`), corrigé après signalement de JB.
- Reste ouvert : faut-il aussi masquer le graphe du profil de retour pour gagner encore de la place ?


## Session 7 — Onglet Carte, pistes des terrains, choix du terrain (18/09/2026)

### Livré
- **4e onglet « Carte »** (`feature:flight/TrafficMapScreen.kt`) : la même carte aéronautique hors ligne que Pilotage (mêmes sources, mêmes espaces),
  **sans aucune fonction de vol** — ni planeur, ni trace, ni route, ni marge. Caméra libre, départ au zoom 7,4 (tout le sud de la France).
  Chaque aéronef reçu de l'OGN est une **pastille à deux caractères** : les deux dernières lettres de l'immatriculation quand la DDB l'autorise,
  sinon les deux derniers caractères de l'adresse radio (les avions et jets vus par leur adresse ICAO ne sont pas dans la base). Teinte verte en spirale.
  Un appui ouvre la **fiche** de l'aéronef : type (planeur, remorqueur, parapente, hélico, avion, jet…), altitude, vitesse sol, vario, distance au terrain et âge de la dernière trame.
  En-tête : nombre d'aéronefs en vol et état du réseau. Tolérance de touche 22 dp autour du doigt.
- **Portée OGN élargie** : filtre APRS `r/lat/lon/250` (tout le sud de la France) au lieu de 15 km. Pour tenir en mémoire, au-delà de **60 km du club**
  seules les **trois dernières positions** sont gardées (à cette échelle la trajectoire n'apporte rien) ; à moins de 60 km, trajectoire complète, spirales et pompes inchangées.
- **Pistes des terrains** (`core:domain/aero/Runways.kt`, `data:carto/RunwayGeoJson`) : bande blanche cerclée de noir dessinée à l'**orientation réelle**
  (couche `runways`, `icon-rotate` sur la propriété `hdg`, alignée sur la carte, visible à partir du zoom 8,5). Trois terrains pour l'instant :
  **LFMT** Montpellier 12L/30R (120°), **LFNL** Saint-Martin-de-Londres 12/30 (120°, en herbe), **LFMS** Alès-Cévennes 01/19 (010°).
  L'orientation n'est pas écrite sur la carte. Table tenue à la main : openAIP ne publie pas l'orientation des pistes dans le pack.
- **Correctif du bug récurrent du terrain de repli** : le menu (AUTO · LFNL, en haut à droite de Pilotage) ne s'ouvrait **que lorsque le moteur de sécurité avait déjà calculé
  des solutions de plané** — donc jamais au sol, ni avant la première position GPS, ce qui donnait un bouton mort. Il est maintenant alimenté par `FlightLive.fieldChoices` :
  les terrains calculés par la sécurité quand ils existent, sinon les **huit terrains les plus proches du pack** (distance seulement). Le menu s'ouvre désormais dès l'ouverture de l'app.
- Version 0.7.0.

### Vérifié
- Tests JVM : 5 nouveaux (108 au total) — orientation et position des trois pistes dans le GeoJSON, terrains sans piste connue ignorés,
  pastille à deux caractères (immatriculation, numéro de concours, anonyme), rétention réduite au-delà de 60 km et trajectoire complète près du club.
- CI verte (build 31) ; captures émulateur de l'onglet Carte (sud de la France, fiche d'un aéronef, pistes au zoom terrain).
- Livrables : `Planneur APP/Session 7/` (APK 0.7.0, captures).

### Limites / à faire
- Trois terrains seulement ont leur piste ; la table `Runways.KNOWN` est à compléter terrain par terrain (une ligne par terrain).
- La pastille ne porte pas le type : un planeur, un parapente et un jet se ressemblent tant qu'on n'ouvre pas la fiche.
- Au-delà de 60 km, pas de spirale ni de pompe détectée (trois positions ne suffisent pas) — voulu.
- Restent de la session 7 prévue : orientation de carte au choix (nord/route), zone de décision et bascule des aides au vol, vent de prévision affiché avant la première spirale.


## Session 6 — Sécurité en vol, mode démo, suivi & debug (17/09/2026)

### Livré
- **Relief réel hors ligne** (`data:carto/Relief.kt`) : lecteur PMTiles v3 (répertoires gzip, feuilles, Hilbert) et décodeur PNG sans dépendance
  (filtres 0–4, RVB/RVBA/palette), altitude Terrarium interpolée au zoom 11 (≈ 38 m), cache de 9 tuiles. Interface `Terrain` dans `core:domain`.
- **Moteur de sécurité** (`core:domain/safety/Safety.kt`, JVM pur) :
  - **vent en spirale** : moyenne des vecteurs vitesse sol sur chaque tour complet (14–65 s), lissage entre tours, validité 25 min ;
  - **arrivée terrain** : finesse sol = finesse × Vsol / 90 km/h avec le vent, +300 m à l'arrivée, **relief franchi à +100 m** tous les 100 m le long de la route (hors 200 premiers et 800 derniers mètres) ;
    altitude nécessaire = max(arrivée, obstacles), obstacle retenu signalé ;
  - **projection à 2 min** : route et vitesse sol (dérive du vent en spirale), montée moyenne ;
  - **terrain de référence** : club tant qu'il est rejoignable, sinon meilleur terrain openAIP à 40 km (hystérésis 100 m, retour au club au-delà de +80 m), choix manuel prioritaire ;
  - **alertes** voix + vibration : marge nulle dans 2 min, marge faible (< 150 m, rétablie > 200 m), sous la sécurité (répétée toutes les 30 s, cap et distance du terrain),
    relief sur la route, terrain de repli, marge rétablie ; armées seulement une fois la marge confortable atteinte (silence au remorqué/treuil), muettes dans le circuit (terrain < 1,5 km, ou < 3 km et atteignable) et au sol ; projection annoncée après 10 s de persistance.
- **Pilotage** : MARGE / ALT / SÉCU / distance / cap calculés par ce moteur ; **coupe sur le relief Copernicus** avec obstacle « RELIEF » ; « 2 min : ±n m » sous la marge ;
  case **vent estimé** (« vent — » tant qu'aucune spirale) ; bandeau rouge **CAP TERRAIN** sous la sécurité ; **choix du terrain** par appui sur AUTO (liste distance + marge, AUTO/MANU) ;
  finesse F20/F15/F10 transmise au moteur. Plus de plafond ni de vent inventés en usage réel.
- **Mode démo** (demande JB) : bascule discrète « démo » en haut à droite de la carte ; vol simulé en boucle (6 pompes et transitions à 3,5–5 km de LFNL, 1 450–1 650 m, vent 300°/15)
  passé par le banc capteurs, donc vario, son, vent, sécurité et coupe fonctionnent ; **trafic OGN en direct conservé** (légende « n à 15 km »). Pas d'IGC.
- **Suivi & debug** (demande JB) : Prévol › interrupteur sous « Détec. auto. décollage », saisie ou raccourcis **F-CGXB** (Twin Astir III, DDB2A0) et **F-CEIQ** ;
  vérification DDB (refus de suivi respecté), boîtiers ajoutés au filtre APRS, trames du planeur suivi injectées dans le moteur de vol à la place du téléphone
  (vario OGN, spirales et vent à la cadence OGN), sécurité et trajectoire calculées dessus ; étiquette « SUIVI F-CGXB · FLARM via OGN · n s » ; rien n'est enregistré.
- Plus aucun signal de démonstration implicite dans l'app : sans donnée, les valeurs affichent « — » (démo = bascule explicite, aperçus seulement).
- Protocole : `docs/PROTOCOLE-TEST-S6.md`.

### Vérifié
- Tests JVM : 17 nouveaux (90 au total), dont :
  - **scénario rejoué** (planeur qui s'éloigne puis spirale) : « 2 min » à 31 s (après 10 s de persistance), « faible » à 74 s, « sous la sécurité » à 141 s puis toutes les 30 s, « rétablie » à 792 s, conformes au calcul ;
  - vent retrouvé à ±2,5 km/h / ±6° en spirale ; crête sur la route ; hystérésis du choix de terrain ; silence dans le circuit ;
  - **relief réel** : extrait du pack occitanie-est (4 tuiles z11) — LFNL 178 m (openAIP 183), sommet du Pic Saint-Loup 600–680 m, obstacle détecté sur la route Pic → LFNL ;
  - **suivi sur trace OGN réelle** (planeur anonymisé en spirale) : source OGN, spirales, pompe > 1 m/s, vent estimé, terrain LFNL et marge ;
  - boucle du mode démo fermée, altitudes et distance au terrain bornées.
  - **vol de démonstration rejoué avec la sécurité** : aucune alerte au remorqué, en finale ni au sol, vent estimé (défaut trouvé en rejouant le vol : alertes au décollage et en tour de piste, corrigé).
- CI verte ; captures émulateur : vol rejoué avec marge sur relief Copernicus et vent estimé en spirale (200°/28), mode démo, liste des terrains, F10, carte Suivi & debug
  (F-CGXB trouvé dans la DDB, en attente de trame), Pilotage en suivi.
- Livrables : `Planneur APP/Session 6/` (APK 0.6.0, captures, protocole).

### Limites / à faire
- Vitesse de plané fixe (90 km/h) et vent au sol ignoré en finale ; polaire et MacCready en v1.1.
- Relief au pas de 100 m sur le zoom 11 : un piton isolé plus étroit peut être lissé.
- Suivi & debug dépend du réseau OGN (retard, trous de couverture) : outil de mise au point, pas d'aide au vol.
- Vent de prévision (precog) non utilisé en attendant la première spirale ; carte orientée route et consignes de centrage : session 7.


## Session 5 — Capteurs, vario, trace IGC (17/09/2026)

### Livré
- **Vario du téléphone** (`core:domain/flight/Vario.kt`) : filtre de Kalman à 3 états (altitude, vitesse verticale, biais d'accéléromètre),
  prédiction à l'accélération verticale (~50 Hz, `acc·ĝ − g` avec le capteur de gravité ou un passe-bas), correction au baromètre (altitude pression ISA, ~25 Hz).
  Sans accéléromètre : même filtre en « baro seul ». Amortissement affichage τ 1 s, son τ 0,3 s. Qualité mesurée : fréquence et bruit du baro (écart type détendu sur 4 s).
- **Moteur de vol** (`FlightEngineCore`, JVM pur, testé) : source du vario (baro+accél. › baro › **OGN de mon planeur si pas de baromètre** › indisponible, toujours affichée),
  altitude calée terrain au sol près du terrain (openAIP), sinon calée GPS (altitude mer : `mslAltitude` Android 14, trame NMEA GGA avant), sinon altitude pression 1013 ;
  calage figé en vol. Décollage > 50 km/h pendant 3 s, atterrissage < 10 km/h pendant 30 s (réglage « Détec. auto. décollage », **activé par défaut** ; coupé : chrono manuel par appui sur le chrono).
  Spirales par la route GPS (≥ 150° sur 20 s), moyennes Spirale (25 s), Pompe (depuis l'entrée en spirale), Jour (spirales montantes).
- **IGC** (`Igc`, `IgcRecorder`) : enregistreur non approuvé (`AXXXGLY`, pas d'enregistrement G), 1 Hz, altitude pression ISA + hauteur GNSS ellipsoïdale, fichier `AAAA-MM-JJ-XXX-GLY-NN.igc`
  écrit ligne à ligne dans le dossier privé de l'app (`igc/`, rejeux dans `igc-rejeu/`), partage par FileProvider.
- **Annonces vocales** (TextToSpeech français, délai 45 s par type) : décollage, atterrissage avec durée, perte du baromètre / vario OGN en secours. Règles de marge prêtes (hystérésis), branchées en S6.
- **Android** : `FlightEngine` (fil dédié, capteurs, GPS 1 Hz, son, voix, OGN) ; `FlightService` de premier plan type localisation, lancé app visible quand Pilotage est ouvert ou pendant un vol enregistré :
  capteurs, son, voix et réseau OGN continuent écran éteint ; notification chrono · vario · altitude, action « Arrêter le suivi ». Permissions : notifications demandées avec la localisation.
- **Pilotage** : plus aucune valeur de démonstration dès qu'une source réelle existe ; vario, altitude (et sa référence), chrono, moyennes, pastilles GPS/BARO réelles,
  position et trace colorée par le vario, **distance et cap réels vers le terrain**, marge calculée sur la distance réelle (« — » si position ou altitude inconnue).
  Sans pack de carte : trace réelle sur fond neutre (plus de spirale démo). Relief du profil et vent restent schématiques (S6), libellés comme tels.
- **Prévol** : carte **Capteurs & vols** (source du vario, fréquence et bruit baro, accéléromètre, précision GPS, altitude et référence, état du vol, son, annonces, essais du son et de la voix, 5 derniers IGC avec partage).
- **Banc de rejeu** (`SensorReplay`) : reconstruit baro 25 Hz, accélération 50 Hz (dérivée seconde Hermite + vibrations + biais) et GPS 1 Hz depuis un IGC.
  **Vol de démonstration** `assets/flight/demo.igc` (`tools/flight/make_demo_igc.py`) : sol LFNL, roulage, remorqué à 3 m/s, **trace réelle OGN anonymisée du planeur en spirale (10 min)**, retour, tour de piste, atterrissage — 34 min.
  `--ez glidy.flight.replay true --ef glidy.flight.speed 4` (rejeu OGN lancé en même temps), signalé « REJEU VOL · capteurs simulés ».
- Protocole de test sur téléphone : `docs/PROTOCOLE-TEST-S5.md` (escalier, sol en voiture, secours, vol).

### Vérifié
- Tests JVM : 16 nouveaux (73 au total) — ISA, signe de l'accélération, convergence à 2 m/s (±0,1), **retard sur une entrée en pompe 0 → 3 m/s en 1 s : 0,03 s avec l'accéléromètre contre 0,79 s en baro seul**,
  bruit au repos 0,10 m/s avant amortissement, biais appris, décollage/atterrissage (à-coup de roulage ignoré), format B (35 caractères) et aller-retour IGC, hystérésis des annonces,
  secours OGN, chrono manuel, et **vol de démonstration de bout en bout** : décollage à 60–75 s, altitude à 0,4 m près, pompe 3,0 m/s, atterrissage, IGC 1 871 s, annonces.
- CI verte (build 19 et 20) ; captures émulateur avec le vol rejoué ×6 : vario « baro+accél. », chrono, moyennes, trace, carte Capteurs & vols en vol puis « Posé · vol de 0 h 31 enregistré » ;
  **IGC récupéré depuis l'émulateur et contrôlé par `tools/flight/check_igc.py` : 1 898 points, OK**.
- Défaut trouvé grâce aux captures : l'émulateur (position Mountain View) changeait le club le plus proche au premier fix GPS → carte absente en Pilotage. Émulateur positionné sur LFNL.
- Livrables : `Planneur APP/Session 5/glidy-v0.5-debug.apk`, `Session 5/captures/`, `Session 5/PROTOCOLE-TEST-S5.md` (Sessions 1–4 intactes).

### Limites / à faire
- **Critère « escalier » et essais réels à faire par JB** (protocole) : réglages du filtre (bruits 0,35 m / 0,6 m/s²) à ajuster sur ses mesures.
- Vario sur téléphone : la pression statique en cabine varie avec la vitesse et l'aération (pas de compensation d'énergie totale) ; le vario de bord prime.
- Hauteur GNSS ellipsoïdale dans l'IGC (`HFALGALTGPS:ELL`) ; altitude affichée : mer.
- Marge et relief réels, vent estimé, choix du terrain de repli : session 6.
- Rejeu : l'accélération est dérivée d'une trace 1 Hz, plus douce qu'en vol réel.


## Session 4 — OGN en direct (17/09/2026)

### Livré
- **Mesure du flux réel** (`.github/workflows/record-ogn.yml`, `tools/ogn/record.py`) : enregistrement APRS-IS, statistiques publiées dans `ci-data/ogn-live/`,
  flux brut en artefact 1 jour seulement (règle OGN des 24 h), extrait **anonymisé** (identifiants, récepteurs, heures, lieux déplacés autour de LFNL).
  Enregistrement quotidien à 12 h 20 UTC (14 h 20 en France, rayon 450 km autour de 44,2 N 4,5 E).
- **Mesure du 16/09 22 h 16 UTC** (flux mondial, 10 min) : 759 aéronefs, 168 299 trames, **cadence médiane 2 s** (p90 5 s), **retard médian 0,4 s** (p90 3,2 s),
  champ montée présent sur 96 % des trames, taux de virage sur 17 %. La cadence de 30 s évoquée au départ n'est pas confirmée : c'est bien plus fréquent.
- **`data:ogn`** : client APRS-IS en lecture (`pass -1`, filtre `r/lat/lon/100` + `b/FLRxxxxxx` pour mon planeur, keepalive 180 s, reconnexion 5 → 120 s),
  parseur OGN (adresse, type, furtif, no-tracking, `!Wxy!`, fpm, rot = 3 °/s, passage de minuit), trafic en mémoire (30 min, pompes 45 min),
  choix DDB respectés (`tracked=N` ignoré, `identified=N` anonyme), détection de spirales (virage cumulé ≥ 540° en ≥ 30 s, montée par altitude GPS) regroupées en pompes (1,2 km, 15 min).
- **Mon planeur** : adresses DDB de l'immatriculation appairée ; position, altitude, vario OGN, cadence et retard mesurés. En Pilotage, le vario OGN remplace la démo s'il a moins de 60 s (libellé « Vario OGN · n s »).
- **Pilotage** : flèches de trafic (libellé CN/immatriculation si autorisé, écart d'altitude), pompes du réseau colorées par la montée (×n planeurs), légende OGN.
- **Prévol** : carte « Réseau OGN · 100 km » (état de connexion, aéronefs, pompes, cadence, retard, mon planeur).
- **Rejeu** : extrait réel anonymisé embarqué (`assets/ogn/replay.aprs`, ×4), activé par `--ez glidy.ogn.replay true` (captures CI, démo hors saison), signalé « REJEU OGN ».

### Vérifié
- Tests JVM locaux : 57 OK, dont 3 sur l'extrait réel anonymisé (4 planeurs, 1 292 trames : 2 pompes à +2,5 et +2,2 m/s détectées, spirales sans montée écartées)
  et un test du client APRS-IS contre un serveur local (ligne de connexion, filtre, lecture).
- CI verte sur 15d5ed0 : build, lint, APK, 11 captures. Dans l'émulateur : connexion réelle à aprs.glidernet.org établie depuis Prévol (0 aéronef à 100 km de LFNL à 2 h 50 UTC),
  puis rejeu : 4 aéronefs et une pompe +2,5 m/s sur la carte hors ligne.
- Correction trouvée grâce aux captures : la DDB (5,5 Mo) était relue sur disque à chaque trame ; index en mémoire relu au plus toutes les 10 min.
- Livrables : `Planneur APP/Session 4/glidy-v0.4-debug.apk` et `Session 4/captures/` (Sessions 1–3 intactes).

### Limites / à faire
- Réseau OGN actif seulement app au premier plan (service de vol en S5).
- Pompes dérivées d'OGN non conservées (serveur et carte historique : v1.1, règles ODbL à vérifier).
- Extrait réel de planeurs en spirale issu d'un vol nocturne européen (vols américains) ; l'enregistrement quotidien français permettra d'affiner les seuils.


## Session 3 — Cartographie et aéro, hors ligne (16/09/2026)

### Livré
- **Packs régionaux fabriqués par la CI** (`.github/workflows/region-packs.yml`, `tools/pack/`), publiés sur la release GitHub **`cartes`** avec `catalog.json` :
  - fond OpenStreetMap vectoriel : extrait du build quotidien Protomaps (`pmtiles extract`, niveau ≤ 12) ;
  - relief : Copernicus GLO-30 (AWS Open Data) → Web Mercator → Terrarium PNG 512 px, niveau ≤ 10 (≈ 76 m), altitude au mètre ;
  - courbes de niveau tous les 100 m (gdal_contour + tippecanoe, couche `contours`, attribut `ele`) ;
  - données openAIP aplaties en GeoJSON (espaces, terrains, balises, points de report), appels espacés et relancés sur 429 ;
  - manifeste par région : version, emprise, validité aéro (28 jours), tailles, SHA-256 ; noms de fichiers versionnés, anciennes versions purgées après 2 jours.
  - 4 régions couvrant les 50 clubs géolocalisés : `occitanie-est`, `provence-alpes-sud`, `toulouse-pyrenees`, `aquitaine-bearn` (≈ 65–90 Mo chacune).
  - Rafraîchissement automatique les 1er et 15 du mois.
- **`data:carto`** (JVM pur, testé) : catalogue et choix du pack du club, téléchargement vérifié (fichier `.part`, reprise, 416 → redémarrage, SHA-256, manifeste écrit en dernier), lecture du GeoJSON openAIP, style MapLibre GLIDY.
- **Domaine aéro** (`core:domain/aero`) : énumérations openAIP confirmées sur la spécification officielle, limites verticales (SFC, ft AMSL/ASFC, FL), familles d'espaces, point dans polygone (trous gérés), distance au bord.
- **Pilotage** : vraie carte **MapLibre Native** (`android-sdk-opengl` 13.0.2, dernière série compilée en Kotlin 2.0) lue depuis les PMTiles locaux :
  fond sombre, ombrage, courbes, espaces aériens colorés (contrôlés bleu `#69c8ff`, réglementés orange, information vert), terrains et balises openAIP, planeur démo géolocalisé près du terrain du club avec trace colorée par le vario, route vers le terrain.
  AUTO/LIBRE (suivi du planeur), zoom +/−, échelle graphique réelle, attribution. Sans pack : carte démo et invitation à télécharger.
- **Prévol** : carte « Carte hors ligne » (région, taille, validité aéro, téléchargement avec progression/annulation, mise à jour) et carte « Espaces aériens · 15 km autour du terrain » (type, classe, plancher → plafond, distance).
- Altitude du terrain de référence lue dans openAIP (LFNL 183 m) pour la coupe démo.

### Vérifié
- Tests JVM locaux : 44 OK, dont 3 sur le **pack réel occitanie-est** (215 espaces, 88 terrains ; LFNL 183 m, 122.505) et la reprise de téléchargement (416).
- CI verte sur 108e662 : build, lint, APK, 10 captures émulateur. Pack occitanie-est (67 Mo) téléchargé et vérifié en 24 s dans l'émulateur ;
  carte MapLibre rendue hors ligne (fond, ombrage, espaces, terrains, libellés) ; 12 espaces listés à 15 km de LFNL.
- Livrables : `Planneur APP/Session 3/glidy-v0.3-debug.apk` et `Session 3/captures/` (Sessions 1 et 2 intactes).

### Limites / à faire
- openAIP est sous licence **CC BY-NC** : à revoir avant toute monétisation (plan, risque S3).
- Niveaux de vol affichés en atmosphère standard (QNH inconnu) : affichage, pas alerte.
- openAIP ne décrit pas les activations du jour (SUP AIP, NOTAM) : rappel affiché.
- ABI embarquées : arm64-v8a et x86_64 (MapLibre natif).


## Session 2 — GLIDY, charte v8, Check-lists, Pilotage v8, appairage FLARM (16/09/2026)

### Livré
- **Nom : GLIDY** (libellé de l'app, écran d'avertissement, user-agent). `applicationId` conservé (`com.neutronstar.glidercopilot`) : aucune publication encore, renommage possible avant la fiche Play Store.
- **Charte v8** (`planeur-pilotage-prevol-v8.html`) dans `core/designsystem/Theme.kt` : fond noir, vert `#b7f7a5`, orange `#ff9f43`,
  texte `#f5f5f5/#c4c4c4/#adadad`, filets `#383838/#292929`, commandes `#171717/#292929`, échelle vario v8
  (`#2d7043 → #7dc77e → #bebebe → #ffbd70 → #ff9130 → #ff8078`), police système. Polices B612/Barlow retirées. Plus aucune couleur codée dans les écrans.
- **Navigation** v8 : Prévol · Check-lists · Pilotage, pictogrammes au trait, passage direct d'un toucher (la confirmation de sortie du vol de S1 est supprimée, conformément à la maquette).
- **Check-lists** (`feature:checklist`, contenu dans `core:domain/checklist`) : copie de la maquette (CRIS 18, TVBCR 10, VERDO 8, APRÈS 7),
  briefing rupture de câble avec plan généré (mêmes règles que `renderCablePlan()`), progression par carte, « Tout effacer ».
  **Toutes les polices × 1,3** (constante `SCALE`). Cases et briefing persistés localement.
- **Pilotage** v8 : marge verte (orange sous la sécurité), finesse F20/F15/F10, terrain AUTO + distance/cap ;
  **profil de retour au terrain rétractable** (Réduire/Déployer) ; carte avec **GPS · BARO · DATA en bas à gauche** et **chrono en bas au centre** ;
  **vario simplifié sans conseil** (valeur colorée, barre, Spirale/Pompe/Jour, bande altitude 5 min) ; **colonne d'actions : son du vario + repli du vario**.
  Son réel (AudioTrack, règles de la maquette), coupé au repli. Pastilles : GPS = permission précise + récepteur actif, BARO = capteur de pression présent, DATA = réseau validé.
- **Prévol** : carte **Planeur du jour · FLARM** (saisie immatriculation, Valider/Annuler, statut, pastille), 3 dernières immatriculations en raccourcis,
  interrupteur « Détec. auto. décollage » (réglage mémorisé ; branchement GPS en S5).
  Vérification dans la **Device Database OGN** (`data:ogn`) : cache 24 h, copie locale hors réseau, respect de `tracked=N` (pas d'appairage) et de `identified`.
- **Club le plus proche** : permission de localisation demandée une fois ; club choisi à la main > club géolocalisé le plus proche > CVV Montpellier.

### Vérifié
- Tests JVM locaux : 32 OK (dont Check-lists, plan de câble, DDB synthétique + **échantillon réel DDB** : 36 559 fiches, 1 771 planeurs F-C, 355 non suivis).
- CI verte sur c301cb1 : tests, lint, APK, 10 captures émulateur (Pixel 6 / API 34) : avertissement, Prévol, appairage, météo, Check-lists (dont cases cochées), Pilotage (normal, profil réduit, vario replié).
- Appairage réel en CI : **F-CPHI → FLARM ID 004839 · Duo Discus** (base OGN téléchargée par l'émulateur).
- Émulateur CI : avec `-gpu swiftshader_indirect`, l'émulateur tombait à l'ouverture de Pilotage (hôte, pas l'app : aucun crash applicatif au logcat).
  Passé en `-gpu swangle_indirect`, captures bornées par des `timeout`, trace et logcat continus publiés dans `ci-data/screens`.
- Livrables : `Planneur APP/Session 2/glidy-v0.2-debug.apk` et `Session 2/captures/` (Session 1 intacte).

### Limites / à faire
- Pilotage toujours sur signal de démonstration (carte S3, capteurs S5, sécurité réelle S6).
- La DDB complète (5,5 Mo) est téléchargée à la première validation ; prévoir un index compact si la mémoire pose problème sur petits téléphones.
- Chrono de vol : affiché à 0 h 00 tant que la détection de décollage n'a pas de source GPS.


## Session 1 — Fondations + Prévol météo (16/09/2026)

### Livré
- Projet Android Kotlin multi-modules : `app`, `core:designsystem`, `core:domain`, `data:precog`, `feature:prevol`, `feature:flight`.
- Versions : AGP 8.9.1, Gradle 8.11.1, Kotlin 2.1.20, Compose BOM 2024.12.01, compileSdk/targetSdk 35, minSdk 26.
- Écran d'avertissement (acquittement mémorisé), navigation Prévol / Vol, sortie de l'écran Vol confirmée (deux gestes).
- **Prévol** branché sur l'API precog (ARPEGE) :
  - `/v1/arpege/forecast` → surface (T, Td, pression sol, rayonnement net différencié, nébulosité, CAPE, vent 10 m) ;
  - `/v1/arpege/profile?at=` pour chaque heure 9 h–18 h locales → profil 24 niveaux ;
  - `/v1/vigilance` → niveau du département du club ;
  - revalidation ETag/304, cache disque, repli hors ligne signalé, sources horodatées et « périmé ».
- Modèle thermique (`core/domain/Thermal.kt`) : sommet par parcelle adiabatique (θ), base Cu (Espy 125 m/K), w* de Deardorff
  (flux sensible = 0,35 × rayonnement net), montée = 0,85 w* − 0,75. **Constantes à valider avec un instructeur.**
- Vent par tranches QNH 1000–3000 m (interpolation u/v) + rose des vents.
- Annuaire FFVP embarqué (`app/src/main/assets/clubs_fr.json`) : 152 clubs, 50 géolocalisés (sud). Club par défaut : CVV Montpellier Pic Saint-Loup (LFNL). Sélecteur manuel.
- Préférences locales derrière l'interface `UserPreferences` (club, 3 dernières immatriculations, avertissement) → prête pour un futur compte.
- **Vol** : squelette des 3 zones sur signal de démonstration, boutons F20/F15/F10 (relèvement confirmé), vario fermable (croix = masquer + couper le son, bouton pour rouvrir).
- CI GitHub Actions : tests JVM, lint, APK debug, captures sur émulateur, enregistrement de vraies réponses precog/openAIP → branche `ci-data`.

### Vérifié (16/09/2026)
- CI verte : tests JVM (17, dont 4 sur réponses precog **réelles** relevées à LFNL), lint (31 avertissements, 0 erreur), APK debug, captures émulateur Pixel 6 / API 34.
- Journée réelle du 16/09 à LFNL : sol modèle 213 m, plafond 12 UTC 2 078 m QNH sous cumulus (base Espy 1 865 m sol), w* 2,2 m/s, montée estimée 1,1 m/s, vigilance verte (34).
- openAIP : clé en secret `OPENAIP_KEY`, `/api/airports` OK ; `/api/airspaces` a répondu 429 (limite de débit) → espacer les appels en CI.

### Hypothèses et limites connues
- Profil ARPEGE limité à 3 000 m sol : plafond « ≥ sommet » possible en montagne.
- Maille ARPEGE ~10 km ; AROME (1 km) à intégrer pour la surface en relief.
- Nébulosité basse ARPEGE parfois ~100 % alors que le modèle prévoit des cumulus : alerte affichée, pondération à affiner avec un pilote.
- Charte provisoire : remplacée en S2 par la charte v8 de JB.

### Pour la session 2 (bilan en tête de fichier)
1. Appliquer la charte graphique de JB (`core/designsystem`).
2. Saisie d'immatriculation → fiche planeur via OGN DDB, mémoire des 3 dernières.
3. Club le plus proche de la position (permission localisation « approximative »), repli sur le choix manuel.
4. Géolocaliser les ~100 clubs restants (CI + openAIP), corriger les adresses postales.
5. Remplacer les fixtures synthétiques par les réponses réelles de `ci-data/fixtures` et ajuster les mappers si besoin.

### Accès
- Dépôt : https://github.com/jeanbaptisteallaire/Glider-Copilot (public).
- Poussée depuis l'appareil de JB (jeton restreint au dépôt). Le sandbox cloud de Claude n'a pas accès à Google Maven ni à precog : **tout build passe par GitHub Actions**.
