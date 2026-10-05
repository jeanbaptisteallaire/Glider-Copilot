# HANDOFF — état du projet (spiral, ex-GLIDY, ex-Glider Copilot)

## Session 19.1 — page « en développement », tutoriel FR/EN et avis, V1.9.1-lite (06/10/2026)
- Publication Play **en pause** à la demande de JB (fiche non créée). Visuels prêts : `Planneur APP/Session 19/Play Store`.
- `app/Guide.kt` :
  - `DevNoticeScreen` : page blanche FR/EN (bouton globe), une seule fois après la page de connexion
    (`UserPreferences.devNoticeSeen`, `DEV_NOTICE_VERSION`) — invite à laisser un avis sur le Play Store ;
  - `TutorialScreen` : Prévol (club, carte hors ligne, appairage FLARM, détection du décollage), Pilotage (marge,
    coupe, finesse, terrain, orientation, sources GPS/BARO/DATA, pompes OGN, vario, repli), Mes vols (traces
    automatiques, carnet, rejeu 3D). Une capture réelle par étape (`drawable-nodpi/tuto_*.webp`, recadrées depuis
    la CI 80), langue du téléphone par défaut, choix retenu (`guideLanguage`).
  - Champ « Votre avis » en bas → table Supabase `feedback` (écriture seule, invités compris, rattachée au compte si
    connecté, effacée avec le compte). JB lit les avis dans Supabase → Table Editor → `feedback`.
- Accès : onglet **Tuto** (toque d'écolier) tout à gauche de la barre, et case fine « Tutoriel » en haut du menu.
- Politique de confidentialité, page de suppression et formulaire Sécurité des données mis à jour (commentaires).
- CI : captures `01a-info-developpement`, `30`–`33` (tutoriel, avis, menu), `96-release-tuto`.

## Session 19 — conformité Google Play + textes réglementaires, V1.9.0-lite (05/10/2026)
- Compte Google Play Developer de JB passé en **organisation** (éditeur : Jean-Baptiste Allaire, EI).
- Audit complet et réponses Play Console : `docs/play-store/CONFORMITE-GOOGLE-PLAY.md` (URL à saisir, formulaire
  Sécurité des données, déclaration du service de premier plan, public cible, points bloquants avant examen).
- Textes publiés par GitHub Pages depuis `site/` (`.github/workflows/pages.yml`) : `confidentialite.html`,
  `conditions.html`, `suppression-compte.html`, `index.html` (FR + EN). Adresses dans `GcLegal` (designsystem).
- Code :
  - divulgation « Position GPS » avant la demande d'autorisation (AppRoot `LocationDisclosure`) ;
  - liens Conditions · Confidentialité sur la page d'accueil, l'avertissement et en bas de Mes vols (`GcLegalLinks`) ;
  - encart d'information au-dessus des boutons de connexion du compte ;
  - connexion par code e-mail masquée tant que le SMTP n'est pas configuré (`BuildConfig.EMAIL_LOGIN`, variable `EMAIL_LOGIN`) ;
  - indicatif OGN tiré à chaque lancement, plus d'identifiant d'installation enregistré ;
  - `FlightService` jamais lancé sans autorisation de localisation (même en rejeu).
- CI : captures `28-lite-mes-vols-legal`, `29-lite-compte-information`.
- Reste (JB) : publier l'écran de consentement OAuth, client Android pour le SHA-1 Play App Signing, SMTP, SIREN/adresse.

## Session 18.7 — Pilotage sur fond blanc + carte façon carte VFR papier, V1.7.0-lite (03/10/2026)
- **Accord explicite de JB** : Pilotage passe sur fond blanc (thème clair, par défaut). Mêmes fonctions, mêmes
  tailles ; seules les couleurs s'inversent : `GlidyFlightLightColors` (Theme.kt) — encre noire pure pour les
  chiffres, vert foncé #0E7A2E (marge positive), **orange foncé #C2410C** (alerte), rouge #C62828, cap mauve
  #7B2CBF, vario du vert foncé au rouge brique. `GlidyFlightTheme(light)` : en thème sombre, la charte noire v8
  d'origine reste strictement identique (`GlidyTheme`).
- **Carte claire façon carte aéronautique papier** (`paperChartPalette()` dans AppRoot, Pilotage et Carte en clair) :
  terrain crème #F4F1E4, forêts vert pâle, eau bleu clair, routes rouge brique, courbes brunes plus marquées, relief
  ombré chaud. Séparation des espaces aériens conservée : contrôlés bleu plein, réglementés rouge-magenta tireté,
  information vert ; terrains magenta, retour au terrain violet. `MapPalette` gagne des réglages d'intensité
  (courbes, remplissage et épaisseur des espaces, relief) dont les valeurs par défaut sont celles de la carte sombre.
- FlightScreen : plus aucun blanc codé en dur (quadrillage, bordures, chrono, statuts → `c.ink` / `c.lineSoft`).
- Barre d'onglets et barre d'état claires sous Pilotage en thème clair.
- Garde-fou `tools/en-vol` régénéré (accord JB) et étendu à la charte claire de Pilotage (10 empreintes).

## Session 18.6 — appairage FLARM de retour en Lite, V1.6.0-lite (03/10/2026)
- Prévol (Lite) : carte « Planeur du jour · FLARM » remise **sous les NOTAM** (immatriculation → trafic OGN,
  base OGN et refus de suivi respectés), sans « Suivi & debug » (`PairingCard(showFollow = false)`).
- Interrupteur **Détec. auto. décollage** actif aussi en Lite (avant : forcé à manuel). Par défaut : manuel en Lite
  (`UserPreferences`), automatique dans l'édition complète. Désactivé → interrupteur REC dans Pilotage.
- CI : capture `27-lite-prevol-appairage`.

## Session 18.5 — menu d'accueil spiral (3 cartes), V1.5.0-lite (03/10/2026)
- Après la page de connexion (et l'avertissement au premier lancement), **menu d'accueil** `app/HomeMenuScreen.kt`
  d'après la maquette de JB (« Menu 2 inspiration »), cartes à coins arrondis (22 dp) :
  - **1° Preflight & weather** : carte blanche avec la vraie journée du terrain choisi (déclenchement, plafond, fin
    + graphique des plafonds heure par heure, `PrevolDayPreview` exposé par `feature:prevol`, même ViewModel) → Prévol ;
  - **2° Flight computer** : photo cockpit (`menu_flight_computer.webp`) → Pilotage ;
  - **3° 3D flight log** : rejeu 3D (`menu_flight_log.webp`) → Mes vols.
  - Photos de « 1 Graphic assets » (`Sans titre-2.jpg`, `rejeux 3D.jpg`) recadrées à 1,45:1, à 90 % d'opacité,
    bandeau clair dégradé sous le titre.
- Le retour système dans les onglets ramène au menu ; quitter Pilotage arrête le service de vol sauf vol en cours.
- CI : `pass_home` (tap « Ouvrir Prévol ») après chaque accueil, capture `00b-menu`.

## Session 18.3 — l'app devient « spiral », page d'accueil avec connexion Google, V1.3.0-lite (03/10/2026)
- **Nouveau nom : spiral** (minuscules, décision JB). Renommé partout où c'est visible : nom sous l'icône, page
  d'accueil, avertissement, notification de vol, textes de Mes vols / Prévol, en-tête IGC (`HFFTYFRTYPE:SPIRAL`).
  Inchangés (techniques) : `applicationId` `com.neutronstar.glidercopilot` (Google + Play Store), code fabricant
  IGC `AXXXGLY`, identifiant OGN `GLIDYnnnn`, noms de modules et de paquets.
- **Icône** : logo spiral sur fond clair (icône adaptative vectorielle).
- **Page d'accueil** (`app/WelcomeScreen.kt`), remplace l'écran écureuil :
  - fond `welcome_background.webp` (photo sans planeur) + planeur détouré `welcome_glider.webp`, posé à sa place
    dans la photo (centre 51 % / 54 %, envergure 83 %), assets de « Planneur APP/1 Graphic assets » ;
  - logo vectoriel `spiral_logo.xml` à 60 % d'opacité, nom « spiral » en Inter 46 sp ;
  - animation : le planeur monte de 14 dp en 20 s (une fois) ; parallaxe au toucher (fond ±6 dp, planeur ±18 dp,
    ressort très souple, retour au relâché) ;
  - boutons pilule givrés : « Continuer avec Google » (logo G officiel de Google Play Services,
    `googleg_standard_color_18`, gardé par `res/raw/keep.xml`) et « Continuer en invité » ;
  - sans compte : affichée à **chaque ouverture** (décision JB) ; avec compte : écran de lancement 1,6 s.
- CI : `tools/screens.sh` passe l'accueil à chaque lancement à froid (`pass_welcome`), capture `00-accueil` ;
  le test de fumée release aussi.

## Session 18.2 — « Continuer avec Google » dans Mes vols, V1.2.0-lite (02/10/2026)
Demande JB : depuis « Se connecter », option de connexion Google ; les vols sont sauvegardés en base et
reviennent après une mise à jour, une réinstallation ou sur un autre téléphone.
- **Carte « Sauvegarde en ligne »** : bouton **Se connecter** → *Continuer avec Google* (si configuré), « ou »,
  puis e-mail + *Recevoir un code* (étape code : *Valider*).
- **Google** : Credential Manager + `GetSignInWithGoogleOption` (`app/GoogleSignIn.kt`), nonce aléatoire
  (empreinte SHA-256 envoyée à Google, valeur brute à Supabase). Jeton échangé par
  `SupabaseClient.signInWithIdToken` (`/auth/v1/token?grant_type=id_token`, 2 tests). Aucune nouvelle table.
- À la connexion, `FlightSyncService` part aussitôt : envoi des vols du téléphone, restauration de ceux en ligne.
- **Configuration à faire par JB** (docs/supabase/SETUP.md §6) : clients OAuth Web + Android dans Google Cloud,
  fournisseur Google dans Supabase, secret GitHub `GOOGLE_WEB_CLIENT_ID`. Sans ce secret, bouton Google masqué.
- **SHA-1** : la CI écrit `build-report/signing-sha.txt`. L'APK debug change de clé à chaque build ; tester
  Google avec l'APK release signé par la clé d'upload (secrets `KEYSTORE_*`).
- Dépendances : `androidx.credentials` 1.3.0 (+ play-services-auth), `googleid` 1.1.1 ; règle R8 ajoutée.

## Session 18.1 — « New UI », V1.1.0-lite (01/10/2026)
Nouvelle couche visuelle demandée par JB (rôle : directeur artistique), inspirée d'Apple Santé. Orchestration :
fondations (design system, barre d'onglets, Pilotage) faites directement, puis trois agents en parallèle sur des
fichiers disjoints (Prévol ; Mes vols ; Rejeu 3D + Feed + Check-lists).
- **Police Inter partout** (v4.1, SIL OFL, `licenses/INTER-FONT-LICENSE.txt`) : `core/designsystem/res/font`
  (4 graisses statiques) via `GcFonts.ui/mono/numbers` ; `InterVariable.woff2` pour le rejeu 3D.
- **Échelle typographique d'Apple** dans `GcType` : largeTitle 34 · title1 28 · title2 22 · title3 20 ·
  headline 17 · callout 16 · subhead 15 · footnote 13 · caption1 12 · caption2 11 · `metric` 28 tabulaire.
  Les pages blanches passent en Body 17 / Subheadline 15.
- **Pages blanches** : fond groupé `F2F2F7`, cartes blanches à coins 14 dp, titre de carte teinté par catégorie
  avec pictogramme (jetons `sky`, `sun`, `wind`, `altitude`, `heart`, `mint`), chiffres « Apple Santé » (libellé
  au-dessus, grand nombre, unité petite et grise via `metricText`). **Sélection = bleu ciel `0A84FF`** (`route`,
  `accentFill`) à la place du vert clair.
- **Graphiques** : barres en capsules à dégradé doux, grilles en pointillés légers, peu d'étiquettes (Prévol) ;
  nouveau profil d'altitude à aire dégradée indigo dans le détail d'un vol (Mes vols).
- **Barre d'onglets** : pictogrammes pleins façon SF Symbols (`GcIcons.Tab` : soleil, planeur vu de dessus,
  personne, cartes, coche, carte pliée), 26 dp ; bleu ciel sélectionné, barre blanche translucide.
- **Pilotage** : fond noir et mise en page inchangés ; **seule la police change** (Inter, chiffres tabulaires),
  accord JB (« tu peux changer les polices à travers toutes les pages »). Empreintes du garde-fou régénérées.
  La barre d'onglets sous Pilotage reste noire, avec les nouveaux pictogrammes.
- **Rejeu 3D** : HUD en panneaux translucides floutés, Inter, lecture et progression en bleu ciel.
- **Passe de rédaction** : phrases raccourcies dans toute l'app (avertissement d'accueil, Prévol, Mes vols, Feed,
  Check-lists, rejeu, messages du moteur et du cloud). Textes utilisés par `tools/screens.sh` et les tests conservés.
- Édition complète : 0.11.0.

## Session 18 Lite — édition allégée pour un lancement Play Store rapide, V1.0.0-lite (01/10/2026)
Changement de stratégie demandé par JB : une V1 « Lite » avec trois onglets. Tout le code de l'édition complète
reste dans le dépôt ; elle se construit avec `./gradlew -Pglidy.edition=full …`.
- **Drapeau d'édition** : `BuildConfig.LITE` (propriété Gradle `glidy.edition`, « lite » par défaut).
  versionName `1.0.0-lite` (complète : 0.10.2).
- **Onglets** : Prévol · Pilotage · Mes vols. Pas de Feed, Check-lists ni Carte. L'app s'ouvre sur Prévol.
- **Prévol Lite** (décision JB) : météo + cartes hors ligne + espaces aériens + NOTAM. Pas de planeur FLARM, OGN,
  capteurs, ni suivi.
- **Pilotage** : seule modification, demandée explicitement par JB, un **interrupteur discret « REC »** à droite du
  chrono (bas de l'écran) qui lance ou arrête l'enregistrement IGC.
  - Il apparaît dès que la détection automatique du décollage est coupée.
  - En Lite elle l'est toujours (enregistrement manuel seulement, décision JB) ; le rejeu CI garde l'auto.
  - Empreintes du garde-fou « En vol » régénérées (`--update`) avec cet accord.
- **Mes vols Lite** : page profil sans partage (pas d'icônes, pas de bouton fil) ; carte Compte visible d'emblée.
- **Compte** : optionnel (décision JB), connexion par code e-mail Supabase déjà codée (S12).
  - S'active dès que les secrets `SUPABASE_URL` et `SUPABASE_ANON_KEY` existent (guide `docs/supabase/SETUP.md`).
- CI : captures 26 (Pilotage hors démo avec REC), 26b (REC activé), 26c (Mes vols Lite).

## Session 17 — Onglet Feed (premier onglet), V0.10.2 (27/09/2026)
Étape 4 du plan réseau social. Pilotage inchangé (garde-fou CI) ; onglet d'ouverture toujours Prévol.
- **6 onglets** : Feed · Prévol · Check-lists · Pilotage · Carte · Mes vols (icône grille `GcIcons.Feed`).
- **`core:social`** (Kotlin pur, 10 tests) :
  - `FeedPilot`, `FeedFlight`, `FeedPage` (curseur), `SocialRepository`, `FollowStore` ;
  - recherche sans accents sur le début des mots du nom et du pseudo ;
  - `DemoSocialRepository` : 14 pilotes fictifs marqués « exemple », clubs « Club exemple · … », 6 à 15 vols
    chacun, traces synthétiques déterministes (transitions + spirales), normalisées pour les vignettes.
- **`feature:feed`** (nouveau module, 3 tests VM) :
  - barre de recherche (délai de frappe 250 ms), résultats avec Suivre / Suivi ;
  - grille 3 colonnes des vols des pilotes suivis : vignette claire + trace rouge (`GcTraceThumbnail`,
    maintenant dans le design system), puis « distance · durée » et @pseudo ;
  - défilement infini par pages de 18 (curseur, sans doublon) ; suggestions « Pilotes à suivre » en tête tant
    qu'on suit moins de 3 pilotes, puis en fin de fil ;
  - détail d'un vol : pilote + Suivre, grande vignette, durée, distance, altitude max, date. Pas de rejeu 3D
    pour les vols d'exemple (pas de fichier IGC).
- `FollowHost` (app) : suivis dans les préférences `glidy_follows`. S18 : tables `follows`, vue `feed`.
- CI : captures 25 à 25d (fil vide, grille après 3 abonnements, défilement, recherche « lea ») ; test de fumée
  release sur 6 onglets.

## Session 16 — Mes vols devient la page profil du pilote, V0.10.1 (27/09/2026)
Étape 3 du plan réseau social. Pilotage inchangé (garde-fou CI).
- **Nouveau module `core:social`** (Kotlin pur, 5 tests) : `PilotProfile` (nom, pseudo, bio, club, niveau
  Élève / Breveté / Autorisé emport passager / Formateur, heures antérieures), règles du pseudo (`Usernames` :
  3 à 20 caractères a-z 0-9 _ ., suggestion depuis le nom), `ProfileStore`, total d'heures.
- **Profil local** : `ProfileHost` (préférences privées `glidy_pilot_profile`). Unicité du pseudo : S18 (serveur).
- **Page profil** (`ProfileScreen.kt`) :
  - en-tête : @pseudo, interrupteur de thème, menu ⋯ (Voir le compte, Modifier nom et pseudo, Modifier le
    profil, Supprimer le compte) ;
  - avatar à initiales, chiffres Vols / Heures / Km, nom, bio, club, niveau ;
  - boutons Modifier (ou Créer mon profil) et Importer un IGC.
- **Grille des vols, 3 par ligne** : vignette claire (quadrillage façon carte) + trace **rouge** (jeton `trace`),
  date courte, durée · distance.
  - Icône de partage sur chaque tuile, cochée (aplat vert) une fois partagé.
  - Le vol d'exemple ne se partage pas. Même bascule dans le détail (« Partager sur le fil GLIDY »).
- **Room v2** : colonnes `visibility` (PRIVATE par défaut) et `publishedAtEpochMillis`, migration 1→2 non
  destructive ; `FlightArchiveRepository.setVisibility`. La mise en ligne réelle viendra avec Supabase (S18).
- Supprimer le compte : efface le profil local et, si connecté, le compte en ligne (S12). Les vols restent.
- Écran d'édition plein écran (nom, pseudo avec @ et contrôle en direct, bio 160, club, niveau, heures).
- Tests VM : partage/départage, vol d'exemple jamais partagé, profil chargé/enregistré/effacé.
- CI : captures 21b (vol partagé) et 21c (édition du profil).

## Vol d'exemple : retour au vol synthétique de Saint-Martin-de-Londres (27/09/2026)
Demande de JB : la trace Condor (Italie) ne fonctionnait pas bien sur son téléphone → le vol synthétique LFNL
(identique à la S13) redevient le vol d'exemple. Capture CI 23c : saut à 12 % du vol (spirale à gauche).

## Correctif météo — clé d'API precog (27/09/2026)
precog-api.com exige désormais une clé (préfixe `pcg_`). La clé n'est **jamais dans le dépôt** : secret GitHub
`PRECOG_API_KEY` → `BuildConfig.PRECOG_API_KEY` → `UrlConnectionHttpClient(apiKey)`, qui l'envoie en
`Authorization: Bearer` et `X-API-Key` (précog seulement ; OGN et cartes sans clé). HTTP 401/403 → message
« clé d'API météo refusée », copie locale servie si elle existe. La CI écrit `build-report/precog-auth.txt`
(codes HTTP par variante d'en-tête, jamais la clé) pour confirmer la bonne convention. `record_fixtures.sh` et
son workflow utilisent aussi le secret (avec contrôle anti-fuite dans les fixtures).
Note : une clé embarquée dans un APK reste extractable ; à terme, passer par un relais serveur (Supabase Edge Function).

## Session 15 — Thème clair « social », V0.10.0 (26/09/2026)
Étape 2 du plan réseau social. **Pilotage (« En vol ») strictement inchangé**, garanti par la CI.
- **Thème social blanc par défaut** (nouvelle préférence `light_mode_social`, vraie par défaut ; le bouton
  Clair/Sombre reste) sur Prévol, Check-lists, Carte et Mes vols.
  - Palette : fond blanc, gris neutres, texte #0F0F0F, vert foncé #1A7F37 pour les chiffres (contraste ≥ 4,5:1),
    vert GLIDY #b7f7a5 en aplat de marque (`accentFill`).
  - Typographie : titres en casse normale, plus gras (`gcHeading`, `socialType`).
  - Composants (`GcCard`, `GcPill`, `GcKpi`, `GcButton`) : variante sociale (titres de section en gras, pastilles
    pleines pâles, boutons pleins à coins de 12 dp). La variante sombre est identique à la V0.9.x.
- **Barre d'onglets** blanche (sélection noire, style réseau social) ; sous Pilotage, exactement la barre noire
  d'avant. Icônes de la barre d'état foncées sur les onglets blancs, réglage d'origine sous Pilotage et en rejeu 3D.
- **Garde-fou « En vol »** (`tools/en-vol/check_en_vol.py`, étape CI) : empreintes SHA-256 de la S13 pour
  FlightScreen, FlightLive, FlightMap, VarioTone, UiMask, la charte sombre (couleurs, typographie, GlidyTheme) et
  l'appel de Pilotage dans AppRoot. Toute modification fait échouer la CI. Évolution voulue par JB :
  `--update`.
- CI : bouton du rejeu en « Revoir le vol en 3D » (casse sociale) ou capitales ; repli de position de la frise (23c).

## Session 14 — Rejeu 3D corrigé (retours de JB sur téléphone), V0.9.4 (26/09/2026)
Première étape du plan « réseau social » (`claude/plan-reseau-social.md` dans le projet claude.ai ; décisions
de JB : 6 onglets avec Feed en premier, « En vol » = Pilotage seul, vols publiés visibles par tout inscrit,
connexion par code e-mail). **Pilotage non modifié.**
- **Inclinaison du mauvais côté** : le repère local du rejeu (x est, y haut, z nord) est main gauche ; le
  modèle (main droite) y apparaissait en miroir, d'où l'aile basse à l'extérieur du virage. Correction dans
  `attitude-frame.mjs` (modèle symétrisé en X + signe du roulis inversé), test `attitude-frame.test.mjs`
  qui échoue avec l'ancienne convention et vérifie « aile basse côté intérieur » pour tous les caps.
- **≈ 40° en spirale stabilisée** : `bankGain` 2,05 appliqué progressivement à tan φ (≈ 1 près de 0 : lignes
  droites et bruit inchangés). Spirale r 150 m à 90 km/h : 39,3° ; vol Condor de JB : 42,5° en thermique ;
  roulis ≤ 25 °/s, ±55° max. Test dédié ; les tests de physique pure passent `bankGain: 1`.
- **Vitesses 1×, 2×, 4×**, 4× à l'ouverture. Zoom à deux doigts : plage élargie (caméra 35 m → ×10).
- **Vol d'exemple** = trace de JB (Condor, Antares 18S, Biella-Cerrione → Chavez-Marini, 21/06/2024, 1 h 09),
  badge « EXEMPLE · SIMULATEUR CONDOR ». Hors France → imagerie EOX. Le vol synthétique reste dans `samples/`.
- CI : tous les tests JS (`*.test.mjs`) ; nouvelles captures 23c/23d (saut à 23,5 % du vol, dans une spirale à droite).

## Session 13 — Rejeu 3D refondu (équipe de 3 agents), V0.9.3 (26/09/2026)
Demande de JB : graphisme qui saute et tremble, clipping, faible portée visuelle, vrai modèle de planeur,
inclinaison réaliste déduite du tracé, meilleures cartes. Travail réparti en 3 lots puis intégré et relu.
- **Modèle 3D** : « UNDERPOLY: Free Sailplane Glider » (Sketchfab, **CC BY 4.0**, usage commercial permis,
  crédit affiché dans l'attribution de la carte 3D). Converti en format maison sans dépendance
  (`glider.bin` + `glider.json` + 2 PNG, 218 Ko, 8 032 triangles, 1:1 m, envergure 15 m) chargé par
  `glider-model.mjs`, repli procédural. Train sorti au sol, rentré au-dessus de 55 km/h.
  Licence : `vendor/licenses/GLIDER-MODEL-LICENSE.txt`.
- **Dynamique** (`flight-dynamics.mjs`) : trace rééchantillonnée, lissage Savitzky-Golay zéro-phase, spline
  Hermite C1, cap tangent lissé (figé au sol), inclinaison en virage coordonné atan(V·ω/g) avec filtre de
  roulis du 2e ordre aller-retour (anticipation, 30 °/s max, ±60°), tangage = pente de trajectoire.
  7 tests `node --test` (spirale bruitée 22,8° pour 23,0° attendus, ligne droite < 0,5°, trous, doublons),
  jitter du cap ÷10 000 par rapport à l'ancienne méthode. Hypothèse : vitesse air ≈ vitesse sol (pas de vent).
- **Rendu** (`replay.mjs` réécrit) : une seule boucle requestAnimationFrame, caméra de poursuite amortie
  (ressorts critiques) qui suit la route de fond et non les spirales, matrices en float64 ré-ancrées sur le
  planeur (fin du tremblement float32 de 5 à 8 px), trace en ruban 5 px colorée au vario et visible en
  transparence derrière le relief, silhouette du planeur quand il est masqué, fil + anneau au sol, caméra
  toujours au-dessus du relief, relief ×1,15 appliqué aussi à la trace, pixelRatio jusqu'à 2 avec qualité
  adaptative, brouillard repoussé à l'horizon.
- **Cartes** : orthophoto **IGN** 20 cm (Géoplateforme, Licence Ouverte Etalab 2.0) sur la France,
  EOX s2cloudless 2017 ailleurs, relief AWS Terrain Tiles. Non vérifié en réel dans le bac à sable (proxy) :
  à valider sur téléphone (tuiles IGN hors France/frontières, `TILEMATRIXSET=PM`).
- CI : tests JS ajoutés au workflow.

## Session 12 — Préparation de la sauvegarde cloud Supabase, V0.9.2 (25/09/2026)

### Livré
- `docs/supabase/schema.sql` : tables `profiles` et `flights` (clé unique pilote + empreinte SHA-256), RLS
  « chacun ses lignes », bucket privé `igc` (un dossier par pilote), `delete_my_account()` (Google Play).
- `docs/supabase/SETUP.md` : pas-à-pas pour JB (projet en UE, SQL, modèle d'e-mail avec `{{ .Token }}`,
  2 secrets GitHub `SUPABASE_URL` / `SUPABASE_ANON_KEY`, tests).
- `data:flightcloud` (JVM pur, sans dépendance) : `SupabaseClient` en HTTP direct (connexion par code
  e-mail à 6 chiffres, rafraîchissement de session, Storage, PostgREST, suppression de compte), `MiniJson`,
  `FlightSyncService` (envoi idempotent des vrais vols, jamais le vol d'exemple, restauration des vols
  absents du téléphone, états `SyncState` dans l'index Room). 8 tests contre un faux serveur Supabase.
- Carnet : `readIgc`, `updateSyncState` (requête Room, sans changement de schéma).
- App : `CloudHost` (session en préférences privées, exclue du cloud Google et du transfert d'appareil),
  carte « Compte · sauvegarde en ligne » dans Mes vols (« bientôt disponible » tant que les secrets
  n'existent pas), jamais d'envoi pendant un vol enregistré.
- Rejeu 3D : l'attribution des cartes se replie après le chargement.

### À faire par JB
Suivre `docs/supabase/SETUP.md` (15 min), puis relancer la CI. Ensuite : politique de confidentialité et
formulaire Sécurité des données à mettre à jour avant la publication (liste dans SETUP.md), et page web de
suppression de compte (exigence Play).

## Session 11 — Mes vols à la charte GLIDY, rejeu 3D sur toute la trace, V0.9.1 (25/09/2026)
- Écrans Mes vols réécrits avec `core/designsystem` (plus aucune couleur codée). Profil d'altitude
  coloré à l'échelle vario GLIDY. Vol d'exemple marqué EXEMPLE.
- Rejeu 3D :
  - trace complète relue dans l'IGC (`FlightArchiveRepository.loadTrack`), avec les vrais horodatages ;
  - simplification Douglas-Peucker à 2,5 m au-delà de 20 000 points ;
  - trace colorée au vario ; vario, vitesse et inclinaison physique calculés ;
  - tableau de bord et commandes calés sur les marges système ; charte noir/vert.
- Imagerie EOX s2cloudless **2017** (CC BY 4.0) au lieu de 2020 (non commercial). Politique de
  confidentialité mise à jour.
- CI verte (build 59 / captures 59) : rejeu 3D rendu sur l'émulateur Android 16, 0 plantage, test de
  fumée release OK sur les 5 onglets.

## Session 10 — Intégration de l'app Mes vols, onglet « Mes vols », V0.9.0 (25/09/2026)
- App autonome « GLIDY Mes vols » (ChatGPT, 6 phases) fusionnée avec son historique : modules
  `core:flightarchive`, `data:flightarchive` (Room), `data:flightcloud`, `feature:myflights` et
  `feature:replay3d`. Docs dans `docs/mes-vols/`.
- Un seul FileProvider (autorité `.files`). `FlightArchiveHost` : chaque IGC fermé par le moteur de vol
  est archivé en tâche de fond. Reprise des IGC existants à chaque lancement (dédoublonnés).
- 5e onglet « Mes vols ». Rejeu 3D plein écran, bloqué pendant un vol enregistré. Option CI
  `--ez glidy.flights.demo true` (vol d'exemple) et `--ez glidy.flights.replay3d true`.
- Frontières contrôlées en CI (`tools/mes-vols/verify_module_boundaries.py`).
- CI verte (build 58) : le vol rejoué en CI a été archivé tout seul (« vol archivé : Imported »), 150 tests,
  0 plantage.
- Reste : la liste « derniers vols » de la carte Prévol « Capteurs & vols » est gardée telle quelle
  (doublon mineur avec Mes vols).

## Session 9 — Audit de conformité Play Store, V0.8.3 (24/09/2026)

### Audit (état V0.8.2, rapport lint CI build 54)
- **Bloquant trouvé : targetSdk 35.** Depuis le 31/08/2026, Google Play exige targetSdk 36 (Android 16)
  pour toute nouvelle app et toute mise à jour. → corrigé (compileSdk/targetSdk 36, AGP 8.10.1, qui gère
  l'API 36 et corrige un plantage R8 avec Kotlin 2.1.20).
- **Pages mémoire 16 Ko** (exigé depuis nov. 2025 pour le code natif) : vérifié sur l'APK, les 3 .so
  (MapLibre, graphics.path, datastore) sont alignés 16 Ko en ELF et dans le zip. Rien à faire.
- **Format AAB** exigé par Play : la CI ne produisait qu'un APK debug. → `bundleRelease` ajouté.
- Lint : 0 erreur, 43 avertissements (surtout versions de dépendances). Traités : `HardwareIds`
  (ANDROID_ID remplacé par un numéro aléatoire local — l'indicatif APRS « GLIDYnnnn » n'en utilisait
  que 4 chiffres), `DataExtractionRules`, `MonochromeLauncherIcon`, `ObsoleteSdkInt` (×2),
  `UnusedResources`. Laissés volontairement : orientation portrait verrouillée (Android 16 l'ignore sur
  tablette, sans gravité), `LogNotTimber`, montées de versions.
- Aucun secret, aucun TODO/FIXME dans le code. Aucune dépendance d'analytics/publicité/crash reporting.
- **Données** : tracé dans le code, la position GPS du pilote ne quitte jamais le téléphone. Météo
  (precog) et filtre OGN reçoivent la position **du club choisi**, pas celle du téléphone. →
  le formulaire Sécurité des données doit déclarer « Position précise : non collectée » (le brouillon S8
  disait « collectée », à tort au sens de Google : un traitement local seul n'est pas une collecte).

### Livré (V0.8.3, commit « S9, V0.8.3 »)
- targetSdk/compileSdk 36, AGP 8.10.1.
- Release minifiée : R8 + `isShrinkResources`, `proguard-rules.pro` (numéros de ligne conservés),
  `mapping.txt` publié (gzip) dans `ci-data/build-report/release/` avec l'AAB et l'APK release.
- Lint bloquant (`abortOnError = true`, `checkReleaseBuilds = true`).
- CI : secrets de signature passés à Gradle s'ils existent (sinon clé debug, comme avant) ; émulateur
  des captures passé en **API 36** ; nouveau `tools/smoke-release.sh` qui installe l'APK release (R8),
  parcourt les 4 onglets en vol rejoué et fait échouer la CI en cas de `FATAL EXCEPTION`.
- `res/xml/data_extraction_rules.xml` : pas de sauvegarde cloud, transfert d'appareil autorisé
  (garder ses vols IGC en changeant de téléphone).

### Non vérifié — à faire en premier en S10
- **Le commit n'a pas pu être poussé depuis le bac à sable** (le proxy git de cette session n'avait
  pas le dépôt dans ses sources autorisées). Il est livré en patch + bundle git dans
  `Planneur APP/Session 9/`. Tant qu'il n'est pas poussé et que la CI n'est pas verte (build + captures
  API 36 + test de fumée release), **V0.8.3 n'est pas validée**. Points de vigilance CI : image
  émulateur API 36 (repli possible en 35), temps de build (R8 ×2), règles R8.

### Login + sauvegarde des vols (conseil, non implémenté)
Recommandation : **Supabase** (offre gratuite, région UE) — Auth + Postgres + Storage. Détail, schéma
et points RGPD/Play Store dans le document de projet `claude/handoff-session-9.md`.

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

### Correctifs terrain V0.8.1 / V0.8.2 (test live au club, 20/09/2026)
JB a testé V0.8.0 en vol réel au club (terrain FLNL) et remonté deux bugs observés directement :
- **« Saut » en Suivi & debug (Pilotage)** : le planeur suivi revenait au terrain toutes les ~2 s
  avant de repartir en vol. Cause : `FlightLive.gpsFresh` utilisait un seuil unique de 5 s, trop
  strict pour la cadence normale des trames OGN relayées (2-5 s, parfois plus) — la position
  repassait « périmée » entre deux trames et `FlightScreen` retombait sur `map.field`. **Corrigé en
  V0.8.1** : seuil porté à 20 s en mode `FOLLOW`, resté à 5 s en `PHONE` (vraie perte GPS à détecter
  vite). Confirmé corrigé par JB.
- **Immatriculations affichées en adresse radio brute** (« DDB179 » au lieu de « F-CGXB ») pour le
  trafic environnant. Cause : la copie locale de la DDB (annuaire des immatriculations OGN) n'était
  chargée qu'au premier appairage/suivi — tant que le pilote n'avait rien appairé depuis le dernier
  lancement, elle restait vide. **V0.8.1** a ajouté un rafraîchissement au démarrage du réseau OGN
  (`OgnDeviceDatabase.refresh()`). JB a confirmé que le symptôme persistait malgré ça. **V0.8.2** va
  plus loin :
  - `OgnDeviceDatabase.status()`/`DdbStatus` : état de la copie locale (âge, nb d'appareils, hors
    ligne ou non), sans appel réseau.
  - Nouvelle ligne diagnostic dans Prévol → Réseau OGN (« Base des immatriculations (DDB) : X
    appareils, à jour il y a Y min »), pour que JB puisse vérifier lui-même au sol si la DDB a bien
    chargé, sans repasser par une session Claude.
  - Le repli sur la carte n'est plus l'adresse radio brute (trompeuse, ressemble à une fausse
    immatriculation) mais une chaîne vide, pour activer le repli déjà prévu dans
    `FlightMap`/`TrafficMapScreen` (2 lettres, puis le type d'appareil).
  - Important : un planeur dont le propriétaire a coché « refus d'identification » dans la DDB
    continuera de s'afficher sans immatriculation — comportement voulu (vie privée OGN, cf.
    CLAUDE.md), pas un bug. Impossible de distinguer ce cas à distance : `ddb.glidernet.org` est
    injoignable depuis le sandbox **et** depuis le Mac de JB (403 côté proxy dans les deux cas) —
    seule la CI (réseau non restreint) a pu confirmer un chargement réel (34 224 appareils).
- **Vérification appairage / suivi auto (demandée explicitement par JB avant un vol de test)** :
  tracé dans le code — `GliderRepository.pair()` interroge la DDB directement par immatriculation
  (`ddb.lookup()`), indépendamment de la copie locale utilisée pour étiqueter le trafic (le bug
  ci-dessus). Une fois apparié, le filtre APRS-IS (`OwnGlider.aprsFilter`) inclut une clause exacte
  sur l'adresse du planeur : le suivi démarre automatiquement, sans action supplémentaire. Check au
  sol donné à JB : « Mon planeur · F-XXXX » visible dans Prévol → Réseau OGN (appairage OK), puis la
  ligne en dessous passe de « Pas encore reçu » à une ligne de données dès la balise allumée (suivi
  auto OK) — vérifiable sans voler.
- V0.8.1 et V0.8.2 vérifiées vertes en CI (build + captures, 0 `FATAL EXCEPTION`, IGC contrôlé),
  livrées dans `Planneur APP/Session 8/correctif-0.8.1/` et `correctif-0.8.2/` (MD5 vérifié entre CI
  et le Mac de JB à chaque fois).

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
