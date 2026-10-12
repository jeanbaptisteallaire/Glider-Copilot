# Android V3 — porter l'écran Pilotage iOS V3 sur Android (Play Store)

Rédigé le 12/10/2026. Référence : iOS V3, branche `ios-v3-fluidite` (commit af60ab7), déclarée **version définitive** par JB.
Cible : une Lite Android 2.1.0 équivalente à l'iPhone, publiée sur le Play Store.

## 0. Principe

- **Un seul code pour la logique.** Tout ce qui est calcul ou texte passe en Kotlin commun, pour qu'iOS et Android ne divergent plus :
  - prédiction du planeur (`OwnshipPredictor`, aujourd'hui en Swift) ;
  - estompage des traces superposées ;
  - libellés de l'en-tête (« Marge sécu. », « Fin. / Finesse », « CAP », « Dist. ter. ») ;
  - bascule F10 ⇄ F20 ;
  - décalage du planeur de 5 % vers le bas.
- **Par plateforme, il ne reste que le dessin.** iOS utilise SwiftUI et MapKit ; Android utilise Compose et MapLibre.
- **Sécurité :** les calculs de marge, d'alerte et d'IGC ne changent pas. Seul l'affichage change.

## 1. Écarts à trancher (JB)

| Sujet | iOS | Android : proposition |
|---|---|---|
| Fond de carte | Apple Plans (propre à iOS, pas disponible sur Android) | **MapLibre avec un style clair proche de Plans** (OpenFreeMap / packs hors ligne existants), avec le relief ombré. L'autre option, Google Maps SDK, demande une clé, une facturation et ne fonctionne pas hors ligne. Je recommande MapLibre. |
| Fichiers Pilotage protégés | — | `FlightScreen.kt`, `FlightMap.kt`, `FlightLive.kt`, `VarioTone.kt`, `UiMask.kt`, `Theme.kt` (charte sombre) et `AppRoot.kt` vont changer. Il faut **l'accord explicite de JB**, puis régénérer le garde-fou `tools/en-vol/check_en_vol.py`. |
| Barre d'onglets flottante | Liquid Glass d'iOS | Barre Compose flottante en capsule, translucide (flou plus transparence), la carte passant dessous. L'effet ressemble à iOS mais n'est pas identique. |

## 2. Étapes (chaque étape = APK de test + captures CI)

### Étape 1 : logique commune, sans effet visible sur Android

1. Porter `OwnshipPredictor` (virage à taux constant, gyroscope, résorption τ = 0,35 s) dans `core:domain` (`flight/OwnshipPredictor.kt`), avec des tests en `commonTest`. iOS l'appelle ensuite à la place de sa version Swift.
2. Porter `TraceFade` dans `core:domain` : grille de 30 m, passages distincts à plus de 15 s, niveaux 0 à 5, opacités 1 / 0,8 / 0,68 / 0,58 / 0,5 / 0,45.
3. Créer `logic:flight` (état d'en-tête `PilotHeaderUi`, libellés, F10/F20, flags démo) : utilisé par iOS, Android l'adopte à l'étape 3.
4. Démo : `DemoFlight.generate(..., windKmh = 0.0, spiralWanderMs = 0.35, spiralGainM = 220.0)`. Les paramètres existent déjà en commun, et les tests Android utilisent toujours les valeurs par défaut.

### Étape 2 : fluidité de la carte

1. Boucle d'image `Choreographer`, à 60 images par seconde :
   - caméra posée sans animation à chaque image ;
   - cap lissé (τ 0,4 s), zoom lissé (τ 0,3 s), centre de spirale lissé (τ 0,5 s) ;
   - planeur décalé de 5 % vers le bas.
2. Planeur dessiné en Compose au-dessus de la carte : sa position à l'écran est fixe en mode suivi, il ne reste qu'à le faire tourner.
3. Sources GeoJSON mises à jour par différence :
   - trace : seulement quand elle s'allonge ;
   - espaces aériens : une seule fois ;
   - OGN : par identifiant ;
   - route vers le terrain : 4 fois par seconde.
4. Capteurs :
   - GPS livré directement à la carte ;
   - gyroscope (`TYPE_GYROSCOPE` projeté sur la gravité) transmis au prédicteur.
5. Recentrer conserve le zoom choisi au doigt.
6. Mesure : `adb shell dumpsys gfxinfo` et capture vidéo analysée avec ffmpeg, même méthode que sur iOS. Objectif : irrégularité d'image ≤ 0,8 en démo.

### Étape 3 : disposition identique à iOS V3

1. Carte plein écran sous l'en-tête et sous la barre d'onglets.
2. En-tête en 4 boîtes (blanc à 90 %, coins arrondis) :
   - Marge sécu. avec SÉCU et ALT ;
   - finesse F10/F20 en un seul bouton (« Fin. +300 m » ou « Finesse ») ;
   - CAP GPS ;
   - Dist. ter., terrain et cap du terrain, à la même taille que le CAP.
3. Coupe « relief » et vario :
   - boîtes posées sur la carte ;
   - interrupteurs fixes sur fond noir, agrandis de 20 % ;
   - la carte n'est jamais redimensionnée quand on les ouvre.
4. Boutons de la carte :
   - à gauche, en bas : cible (recentrer), orientation (boussole rouge N en mode nord), GPS/BARO/DATA, jour/nuit ;
   - à droite, en bas : couches aéronautiques et +/−.
   - Les deux colonnes remontent quand le vario s'ouvre.
5. Éléments de la carte :
   - pastille OGN sous la marge ;
   - REC et chrono dans une boîte en bas au centre ;
   - échelle au-dessus de l'attribution ;
   - pas de pastille démo.
6. Tracés :
   - planeur vectoriel blanc à contour noir fin ;
   - route vers le terrain fine et pointillée ;
   - contours des zones aéronautiques à 1,05 pt, avec leurs noms ;
   - bouton de couches pour les afficher ou les masquer.

### Étape 4 : mode sombre

1. Jetons `PilotPalette` clair et sombre dans `Theme.kt`, jamais de couleur dans un écran. En sombre :
   - boîtes noires opaques, textes blancs un peu plus gras ;
   - vert fluo très clair pour la marge, violet fluo très clair pour la route et le cap.
2. Vignettage noir court sur les bords et dégradé noir sous l'en-tête. **Même fond de carte** qu'en clair.
3. Coupe : altitudes blanches, plané bleu fluo, sécurité orange, relief vert vif.
4. Barre d'onglets inversée, barre d'état masquée, choix mémorisé (DataStore).

### Étape 5 : traces et démo

1. Traces recouvertes estompées (logique commune de l'étape 1), dessinées dans l'ordre chronologique. Une trace seule reste nette, même après une heure de vol.
2. Démo avec des pompes quasi verticales et sans vent.

### Étape 6 : publication

1. Tests :
   - Tests JVM, `commonTest` sur le simulateur, lint.
   - Garde-fou régénéré avec l'accord de JB.
   - Captures CI des écrans clair et sombre.
2. Version **2.1.0-lite**.
3. Diffusion :
   - **Test interne** dans la Play Console, puis production. Seul JB téléverse ou valide.
4. Conformité :
   - Fiche « Sécurité des données » : aucune nouvelle donnée collectée, rien à changer.
   - Captures du Store à refaire en clair et en sombre.

## 3. Ce qu'il me faut

1. **Accord** pour modifier les fichiers Pilotage protégés, `Theme.kt` et `AppRoot.kt`, et régénérer le garde-fou.
2. **Choix du fond de carte** : MapLibre (recommandé) ou Google Maps.
3. **Un téléphone Android** branché en USB, avec le débogage activé, pour mesurer la fluidité et tester le gyroscope et le GPS réels. À défaut, l'émulateur.
4. Le code est déjà dans le dépôt (`feature/flight`, `app`, `core/designsystem`), donc **aucun fichier nouveau** n'est nécessaire. Seule exception : le téléversement sur la Play Console, que tu fais toi-même.

## 4. Estimation

Les étapes 1 et 2 sont la base, les étapes 3 et 4 reprennent l'interface, les étapes 5 et 6 terminent. Chaque étape est livrée sur une branche `android-v3` séparée de `main` jusqu'à la publication.
