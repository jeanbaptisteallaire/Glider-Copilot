# HANDOFF — état du projet (GLIDY, ex-Glider Copilot)

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
