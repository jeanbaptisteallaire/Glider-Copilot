# SeeYou Navigator (Naviter) — analyse concurrentielle, mode planeur

Relevé du 19/10/2026 sur Pixel 8 Pro, version **3.11.6+3779**, compte gratuit (sans abonnement SeeYou, « Free trial » non activé), au sol à Montpellier, sans GPS fixé ni capteur externe. Les fonctions payantes n'ont été vues que par leurs libellés.

Captures : `captures/`.

## 1. Architecture générale

| Zone | Contenu |
|---|---|
| Barre haute permanente | **REC** (armement de l'enregistrement, démarrage automatique au décollage) · heure + batterie + état **Online** · bouton **FLY** (passe à la carte de vol) |
| Accueil (Home) | Alertes (ex. « GPS »), **Glider** (choix de l'aéronef/polaire), **Target** (cible), **Task** (épreuve, payant), **Devices** (capteurs externes), **Sleep** |
| Logbook | Bilan annuel (temps, vols, distance) + cartes de vol avec trace sur fond relief (`16-carnet.png`) |
| Settings | ~20 rubriques (voir §6) |

L'app sépare nettement **préparation (listes blanches, style Material)** et **vol (carte plein écran + champs flottants)**. Le menu ☰ de l'écran de vol ramène à l'accueil.

## 2. Écran de vol (FLY)

`04-ecran-vol-menu-navbox.png`

- **Carte plein écran**, triangle bleu = planeur, ligne de cap fine noire vers le haut.
- **Bandeau haut flottant** : batterie, **heure en très grand** (police condensée, contour noir sur fond carte), pastille GPS.
- **Colonne gauche** : MacCready **+ / valeur 0.3 m/s / −** directement sur la carte.
- **Colonne droite** : boussole (tap = Nord en haut / route en haut), zoom + / −, **AUTO** (auto-zoom vers la cible), icône **phase de vol** (au sol / ligne droite / spirale).
- **Panneau bas semi-transparent 2 × 4 champs** par défaut : Vitesse sol · Finesse actuelle · Finesse requise · Distance parcourue / Cible · Écart de route (« To ») · Distance.
- Échelle (5 km) en bas à gauche.
- Toast « **Ready for takeoff** » à l'ouverture.
- Glisser la carte = mode libre : les champs disparaissent, un bouton **Re-center** apparaît. Pas de pages par balayage.

### Personnalisation (point fort majeur)

Appui long sur un champ (`04`, `05`, `09`) → menu **Add navbox / Edit navbox / Move & resize / Edit pages / Remove navbox**.
- **Edit navbox** : 10 couleurs, 5 transparences (plein, 50 %, 30 %, contour, contour inversé), alignement quand un panneau d'info s'ouvre.
- **Move & resize** : placement libre de chaque champ sur la carte.
- **Edit pages** : plusieurs pages de vol (miniatures, « Add page »).
- **Layouts** : enregistrer / importer une mise en page (fichier), modèles **Dark** et **Light**.

### Catalogue des champs (« Navboxes gallery », avec recherche)

`06`, `07`, `08`. Chaque entrée a une valeur d'exemple, un nom et une phrase d'explication ; badge « On map » si déjà affiché.

- **Position** : Altitude (m, ft), AGL, Flight level, Altitude GPS (m, ft), Au-dessus du décollage, Sol, Finesse actuelle (1 min), Finesse de transition (depuis le dernier thermique), Espace aérien le plus proche (distance H/V).
- **Vitesse / temps** : TAS, Heure, Durée de vol, Route vraie / magnétique / en lettres (NE), Rayon de virage, Vario, Vario moyen, **Netto**, Vent, Fréquence cardiaque (BT).
- **Navigation** : Arrivée (hauteur prévue, marge + MC + vent + moucherons + ballast), Relèvement vrai / magnétique, Radial (pour l'ATC), Distance (km, NM), Depuis le décollage, Distance à la zone d'observation, MacCready, Finesse requise, ETA / ETE cible, « To » (écart angulaire), Terrain le plus proche, **Team code**.
- **Épreuve** : Arrivée épreuve, Distance restante, Finesse requise, Vitesse, Vitesse vers la porte, Chrono, Temps restant AAT, Vitesse requise AAT, ETA / ETE, Δtemps, % parcouru, Vario moyen.
- **Statistiques** : Gain d'altitude, Vario moyen 60', Distance libre, Triangle (préfixe FAI), Closed OLC, 3 TP, Closed 3 TP, Dernier thermique, Vitesse 60' / XC 60' / 120', Alt. max, Hike vario.
- **Indicateurs graphiques** : Boussole, **Vue de profil** (coupe du relief + espaces aériens + plané), **vario rond**, **vario linéaire**, Batterie, GPS, NET, Échelle, Phase de vol.
- **Boutons** : Menu, Couches, Zoom ±, Auto zoom, MC ±, Point de virage suivant / précédent, **Pilot Event Marker** (PEV dans l'IGC).

## 3. Carte et couches

`02-couches.png`, `15-couches-config.png`, `14-carte-thermiques-skyways.png`, `03-carte-espaces-point.png`

- **Fonds** : Light, Terrain, Outdoor (par défaut, relief ombré + topo), Satellite (payant). Chacun peut être masqué du sélecteur.
- **Couches** : Airports, Paragliding (désactivé en mode planeur), FAI area, Distance circles, **Radar** (trafic), **Live wind** (balises au sol, flèche colorée + « vent/rafale » en nœuds, ex. 14/30), **Rain** (RainViewer), **Skyways** et **Thermals** (thermal.kk7.ch, carte de chaleur), SkySight et TopMeteo (abonnement SeeYou + compte du fournisseur).
- **Espaces aériens** : contours bleus (CTA/TMA), CTR en rouge pointillé. Un tap sur la carte → **liste des espaces superposés au point** avec classe, plancher/plafond en FL **et** m MSL, fréquence, silhouette, et boutons **Disable Today / Disable Always**.
- **Appui long sur un point** → coordonnées (copier), altitude du terrain, **distance, hauteur d'arrivée, finesse requise**, bouton **Go To**.
- Réglages espaces : classes visibles, espaces masqués en vol, masquer au-dessus d'une altitude, source SeeYou (mise à jour auto) ou fichiers perso `.cub` / OpenAir.
- **Obstacles** : alertes, base de 17 106 obstacles (dont 14 061 lignes électriques, 2 429 remontées mécaniques, 327 téléphériques).

## 4. Radar / trafic

`11-radar-vario.png`

- Sources : **OGN** (envoi et réception, identifiant OGN attribué, ex. FD254A), **PureTrack** gratuit (envoi de sa position), **PureTrack Pro** (réception du trafic d'autres réseaux), **XContest** (suivi en direct).
- Nom du pilote affiché sur les services de suivi ; **Buddies** (cibles favorites) ; **Team code**.
- FLARM direct : via « Devices » (Bluetooth, permission « appareils à proximité » demandée — refusée pendant le test, donc liste des appareils non vue).
- Au sol sans trafic proche, aucun planeur n'est apparu : symbologie du trafic non observée.

## 5. Aides au vol

- **Assistant thermique** (`12`) : Désactivé / Zoom seul / **Bulles** (payant : la carte zoome et affiche les bulles du thermique qui dérivent sous le vent ; trace colorée selon le vario). Audio : **Thermal Sniffer** (alerte quand le taux de chute diminue) et **Action Sound** (« ding » pour serrer ou élargir).
- **Vario sonore** : activé ou non, bip au sol, sensibilité, moyenneur 20 s, son de descente, sons personnalisés.
- **Marge de sécurité** (`13`) : 200 m par défaut, avec schéma explicatif (altitude planeur → altitude sûre → altitude cible).
- **Alerte d'altitude limite** (désactivée par défaut).
- **Soaring Spot** : import des épreuves de compétition.
- **Données hors ligne** : par région (payant).

## 6. Réglages (`10-reglages.png`)

Abonnement · Collections actives (payant) · Espaces aériens · Buddies · Marge de sécurité · Alerte altitude · Appareils · Navboxes · Layouts · Données hors ligne (payant) · Radar · Vario · Assistant thermique · Carte et couches · Obstacles · Soaring Spot · Team Code · Compte · Système (langue, unités, orientation) · Aide · À propos.

## 7. Comparaison avec Wind Glider

| Domaine | SeeYou Navigator | Wind Glider (état actuel) | Lecture |
|---|---|---|---|
| Positionnement | Instrument de vol complet, planeur + parapente, compétition | Lite : Prévol météo + cartes, Pilotage, Mes vols ; Full : Feed social, check-lists, club | SeeYou = calculateur de vol ; Wind Glider = préparation + sécurité + carnet + social |
| Préparation météo | Couches Live wind / pluie / KK7 ; SkySight et TopMeteo payants | Prévol PRECOG (ARPEGE), source + hypothèse sur chaque valeur | **Avantage Wind Glider** : prévol intégré, gratuit, traçable |
| Écran de vol | Carte + champs librement configurables, pages, mises en page | Pilotage fixe, épuré (charte v8, garde-fou CI) | SeeYou très riche mais chargé ; Wind Glider plus lisible. Opportunité : 2 ou 3 *préréglages* plutôt que l'édition libre |
| Catalogue de données | ~80 champs avec explication d'une ligne | Jeu restreint, chaque valeur sourcée | Reprendre l'idée de la **phrase d'explication par champ** |
| Finesse / arrivée | Arrivée avec marge + MC + vent + moucherons + ballast ; marge 200 m | Marge +300 m (F20), terrain local (openAIP / Copernicus / ARPEGE) | Comparable ; afficher l'hypothèse, comme le fait Wind Glider, reste un plus |
| Espaces aériens | Tap → liste multi-couches, FL + m, fréquences, désactiver aujourd'hui / toujours | openAIP dans les packs | **À reprendre** : la fiche au tap et le « désactiver aujourd'hui » |
| Point sur carte | Appui long → distance, arrivée, finesse requise, Go To | — | **À reprendre** (simple, très utile) |
| Trafic | OGN émission + réception, PureTrack, XContest, FLARM BT | OGN (data:ogn, ODbL, ≤ 24 h) | Comparable sur OGN ; l'émission OGN depuis le téléphone est une fonction attendue |
| Thermique | Assistant zoom / bulles + sons | Détecteur de thermiques (ThermalDetector) | Les sons « sniffer » / « action » sont différenciants |
| Vue de profil | Coupe relief + espaces + plané | — | Fort intérêt sécurité, candidat Pilotage (accord JB requis) |
| Obstacles | 17 k obstacles + alertes | — | Lacune de sécurité à combler (lignes électriques, câbles) |
| Carnet | Bilan annuel + liste de vols | Mes vols : IGC, rejeu 3D, profil, partage | **Avantage Wind Glider** (rejeu 3D, social) |
| Modèle économique | Gratuit limité ; épreuves, satellite, bulles, hors ligne, SkySight / TopMeteo en abonnement | — | Les paywalls sont visibles partout (badges orange) : ergonomie dégradée pour le gratuit |
| Style | Material générique, bleu ; vol : chiffres condensés à contour noir | Inter, échelle Apple, pages blanches | Wind Glider plus soigné ; la police condensée à contour reste très lisible sur carte |

## 8. Recommandations prioritaires

1. **Fiche espace aérien au tap** (empilement, FL + m, fréquence, « ignorer aujourd'hui »).
2. **Appui long sur la carte** → distance, hauteur d'arrivée (avec hypothèse), finesse requise, « Aller vers ».
3. **Obstacles** (lignes électriques, câbles) avec alerte, hors réseau.
4. **Vue de profil** relief + espaces + plané (Pilotage : accord JB).
5. **Explication d'une ligne** sous chaque donnée affichée (cohérent avec la règle « source + hypothèse »).
6. Couche **Thermals / Skyways KK7** dans Prévol (vérifier la licence).
7. Émission de position **OGN** depuis le téléphone (vérifier les règles OGN / DDB).

## Limites du relevé

Pas de vol réel ni de simulation : trafic, assistant thermique, alertes et valeurs dynamiques non observés en action. Fonctions payantes non testées. Permission Bluetooth refusée, donc liste des capteurs (FLARM, varios) non vue. Pendant le test, les couches **Thermals** et **Skyways** ont été activées dans l'app (réversible dans Couches).

## Fiches par capture

Chaque fiche dit ce que montre la capture et ce qu'on en retient pour Wind Glider. À relire avant de toucher l'écran concerné.

| Capture | Ce qu'on voit | À retenir pour Wind Glider |
|---|---|---|
| `01-accueil.png` | Accueil : REC / heure / Online / FLY, alerte GPS, Glider, Target, Task (payant), Devices, Sleep | Un seul écran « prêt à voler » qui résume planeur, cible, capteurs. Équivalent possible : carte de synthèse en tête de Prévol |
| `02-couches.png` | Feuille Couches : 4 fonds, 8 couches en pastilles rondes | Sélecteur compact en feuille basse, une pastille = une couche ; modèle pour l'onglet Carte |
| `03-carte-espaces-point.png` | (1-2) tap carte → pile d'espaces aériens, FL + m MSL, fréquence, silhouette, Disable Today / Always ; (4) appui long → coordonnées, altitude sol, distance, arrivée, finesse requise, Go To | Recommandations n° 1 et 2. Afficher l'hypothèse d'arrivée (marge, MC, vent) comme partout chez nous |
| `04-ecran-vol-menu-navbox.png` | Écran de vol complet : heure géante, MC ± à gauche, boussole/zoom/AUTO/phase à droite, panneau 2 × 4 champs ; menu appui long sur un champ | Repères de disposition ; police condensée à contour noir très lisible sur carte. Pilotage reste figé sans accord JB |
| `05-edit-navbox.png` | Édition d'un champ : 10 couleurs, 5 transparences, alignement | Trop de choix pour Lite ; retenir seulement « contour » pour lisibilité sur carte claire |
| `06-navboxes-1.png` | Catalogue : Position, Vitesse/temps, Navigation, Épreuve (début) | Liste de référence des données attendues par les vélivoles ; chaque champ a une phrase d'explication |
| `07-navboxes-2-indicateurs.png` | Fin d'Épreuve, Statistiques (OLC, FAI, 3 TP, vitesses 60'/120'), Indicateurs (boussole, vue de profil, varios rond et linéaire) | Vue de profil = recommandation n° 4 ; statistiques OLC utiles pour Mes vols |
| `08-indicateurs-boutons.png` | Indicateurs (batterie, GPS, NET, échelle, phase) et boutons (MC ±, TP ±, Pilot Event Marker) | Le marqueur PEV dans l'IGC est simple à ajouter à l'interrupteur REC |
| `09-pages.png` | Édition des pages de vol (miniatures, Add page) | Pas de pages en Lite ; à considérer pour Full |
| `10-reglages.png` | Réglages : abonnement, collections, espaces, buddies, marge, alerte altitude, appareils, navboxes, layouts, hors ligne | Les badges « Free trial » partout dégradent l'expérience gratuite : à éviter |
| `11-radar-vario.png` | Radar : OGN émission/réception, PureTrack gratuit/Pro, XContest, ID OGN ; Vario : son, sensibilité, moyenneur 20 s, son de descente | Recommandation n° 7 (émission OGN). Moyenneur 20 s = valeur de référence |
| `12-thermique-obstacles.png` | Assistant thermique (désactivé / zoom / bulles payant) + Thermal Sniffer, Action Sound ; base de 17 106 obstacles | Sons d'aide au centrage = différenciation possible avec ThermalDetector ; obstacles = recommandation n° 3 |
| `13-espaces-marge-devices-layouts.png` | Espaces (classes, masqués, plafond, .cub/OpenAir), marge 200 m avec schéma, Devices vide, Layouts Dark/Light | Le schéma de la marge est un bon modèle pédagogique pour notre « +300 m » |
| `14-carte-thermiques-skyways.png` | Carte avec couches KK7 actives (taches violettes = thermiques) et balises de vent « vent/rafale » | Recommandation n° 6 (KK7 dans Prévol, licence à vérifier) |
| `15-couches-config.png` | Configuration des couches par fournisseur (Naviter, Radar, RainViewer, SkySight, TopMeteo, kk7) | Montrer le fournisseur de chaque couche = cohérent avec notre règle « source affichée » |
| `16-carnet.png` | Carnet : bilan annuel + carte de vol avec trace | Plus pauvre que Mes vols (pas de rejeu 3D, pas de social) |

## Comment refaire le relevé

Téléphone Android en débogage USB, SeeYou Navigator ouvert, puis depuis le Mac :

- capture : `~/Library/Android/sdk/platform-tools/adb exec-out screencap -p > capture.png`
- appui : `adb shell input tap X Y` (coordonnées en pixels réels, 1008 × 2244 sur Pixel 8 Pro)
- ne pas utiliser la touche retour (`keyevent 4`) : depuis l'accueil de l'app, elle sort vers le lanceur Android. Utiliser la flèche en haut à gauche de l'app.
- vérifier chaque capture avant de la versionner : le dépôt est public (e-mail, téléphone, notifications).
