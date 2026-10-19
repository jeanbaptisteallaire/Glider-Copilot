# Pilotage épuré — plan (proposition, à valider par JB)

Retour pilotes : « interface trop chargée pour le pilotage ».
Objectif : en vol, l'œil ne doit trouver que ce qui sert **maintenant**, en moins d'une seconde, sans lecture.

> Rappel : Pilotage ne change jamais sans accord explicite de JB (garde-fou `tools/en-vol/check_en_vol.py`).
> Ce document est une proposition ; rien n'est codé tant que JB n'a pas validé.

---

## 1. Ce que font les standards

| Référence | Principe retenu |
|---|---|
| Garmin G1000 / G3X (« Declutter ») | Un bouton enlève les couches de la carte par niveaux ; les alertes restent toujours visibles. |
| Airbus / Boeing (« dark cockpit ») | Rien ne s'affiche tant que tout est normal ; un élément apparaît quand il devient anormal. |
| LXNAV, ClearNav, XCSoar | Pages ou « modes » selon la phase : **spirale** (vario, moyenne, centrage) / **transition** (finesse, arrivée, route) / **final** (hauteur d'arrivée). Changement automatique en entrant en spirale. |
| AC 25-11B, CS-23 (codes couleur) | Rouge = alarme, orange = attention, vert = normal ; la couleur ne sert jamais à décorer. |
| Lisibilité en vol | Valeurs critiques ≥ 1 cm de haut sur iPhone, contraste fort au soleil ; une info = une place fixe (le pilote mémorise l'emplacement). |

## 2. Ce qu'on affiche aujourd'hui (≈ 25 éléments en même temps)

- **En-tête** : marge sécu (gros), SÉCU, ALT, bouton F10/F20 « Fin. +300 m », CAP, distance terrain, nom du terrain, cap vers le terrain.
- **Coupe** vers le terrain + interrupteur « relief ».
- **Carte** : pastille OGN, DÉMO, boîte vent, échelle vario latérale, recentrage, AUTO/N↑/RTE, pastilles GPS · BARO · DATA, bascule jour/nuit, couches, zoom + / −, mentions Maps.
- **Bas** : vario (valeur + barre colorée), moyennes Spirale / Pompe / Jour, son, interrupteur « vario », REC + chrono.
- **Barre d'onglets** (4 onglets).

Constat : les **réglages** (jour/nuit, couches, relief, interrupteurs, zoom) et l'**état technique** (GPS/BARO/DATA, OGN) occupent autant de place que les **valeurs de vol**. Le pilote doit trier lui-même.

## 3. Proposition

### 3.1 Trois niveaux d'information

| Niveau | Toujours visible ? | Contenu |
|---|---|---|
| **A — Vital** | Oui, toujours | Marge sécu (+ couleur), distance + terrain, vario (valeur + barre), alarme marge négative. |
| **B — Phase** | Selon la phase de vol | Spirale : moyenne de la pompe, centrage. Transition : finesse requise, cap vers le terrain, coupe. Sol : REC, DÉMO. |
| **C — Réglages / état** | Non : caché, un toucher pour l'ouvrir | Jour/nuit, couches, relief, zoom, AUTO/N↑, son, F10/F20, OGN, GPS/BARO/DATA (sauf panne). |

### 3.2 « Dark cockpit » : caché tant que c'est normal

- **GPS · BARO · DATA** : invisibles quand tout va bien ; une pastille **orange** apparaît seulement si un capteur est dégradé, **rouge** s'il est perdu.
- **Marge sécu** : le chiffre reste, mais le cadre ne se colore qu'en approche du seuil (orange sous +200 m, rouge sous 0 m ; seuils à confirmer avec JB).
- **Coupe vers le terrain** : repliée quand la marge est confortable ; elle **se déplie seule** quand la marge passe sous le seuil orange.
- **OGN** : la pastille n'apparaît que s'il y a du trafic proche ou une pompe repérée.
- **Vent** : affiché en transition, masqué en spirale (le pilote le voit à la dérive).

### 3.3 Changement automatique selon la phase (déjà calculée : `circling` dans `FlightEngineCore`)

| Phase | Mise en avant |
|---|---|
| **Au sol** | REC, DÉMO, choix du terrain, réglages accessibles. |
| **Transition** | Marge, distance, finesse requise, cap vers le terrain, coupe (si utile), vent. |
| **Spirale** | Vario en grand, moyenne de la pompe, carte zoomée sur le cercle ; en-tête réduit à la marge. |
| **Final / marge faible** | Marge en très grand, coupe dépliée, terrain et cap ; tout le reste masqué. |

Avec un délai d'environ 10 s avant de changer de phase, pour éviter le clignotement.

### 3.4 Bouton « Essentiel »

- Un bouton fixe, toujours au même endroit (coin bas droit, au pouce), bascule **Complet ⇄ Essentiel**.
- **Essentiel** = uniquement le niveau A + les alertes, en très grand ; carte simplifiée (trace, planeur, terrain, route).
- Le choix est mémorisé ; une alerte (marge négative, capteur perdu) s'affiche **toujours**, même en Essentiel.

### 3.5 Regrouper les réglages

- Jour/nuit, couches, relief, interrupteurs coupe/vario, son, AUTO/N↑ : dans **un seul bouton « ⚙︎ » sur la carte** qui ouvre un petit panneau.
- Zoom + / − : gardés (utile en vol), mais plus petits et semi-transparents.
- En vol, la barre d'onglets peut se réduire (un toucher la fait revenir).

## 4. Où coder (règle de parité)

- Les règles « quoi afficher selon la phase et les seuils » vont en **Kotlin commun** (nouveau module `logic:pilot`, `commonMain`), testées une fois en `commonTest` : Android et iOS dessinent le même état.
- iOS (`PilotView.swift`) et Android (Compose) ne font que lire cet état.
- Garde-fou Pilotage à régénérer après accord de JB.

## 5. Étapes pour le build 5

1. **Validation JB** de ce plan (seuils, contenu du mode Essentiel).
2. `logic:pilot` : état de visibilité (phase, alertes, mode Essentiel) + tests.
3. iOS : bouton « Essentiel », pastilles capteurs en « dark cockpit », panneau réglages unique, coupe auto-dépliée.
4. Test en simulateur (DÉMO) puis sur iPhone ; captures avant / après envoyées à JB.
5. Android : même état, même rendu (peut suivre au build Android suivant).
6. Build 5 = correction « Se connecter avec Apple » + Pilotage épuré.

## 6. Questions pour JB

1. Seuils de la marge : orange à **+200 m**, rouge à **0 m** ? Autres valeurs ?
2. Mode **Essentiel** : marge + distance + terrain + vario suffisent ? Faut-il y ajouter la finesse requise ou le cap vers le terrain ?
3. Changement automatique de phase **dès le build 5**, ou seulement le bouton Essentiel d'abord (moins risqué), et la phase automatique au build suivant ?
4. Android en même temps, ou iOS d'abord ?
