# Nuit du 22 au 23/10/2026 : fiabilité et énergie (iOS)

Deux revues d'experts, faites en lecture seule :
- [REVUE-RESILIENCE.md](REVUE-RESILIENCE.md) : logiciel critique, 24 constats ;
- [REVUE-ENERGIE.md](REVUE-ENERGIE.md) : 19 recommandations.

Ce document liste ce qui a été corrigé, ce qui a été mesuré sur l'iPhone 15 de JB, et ce qui reste à faire.

## Corrigé : fiabilité

| Constat | Correction |
|---|---|
| **C1** marge figée après une perte du GPS | Si la position a plus de 5 s, la marge, la distance et la SÉCU passent en tirets. La pastille GPS suit la fraîcheur de la position, plus seulement l'autorisation. Le planeur reste à sa dernière position connue. |
| **C2** marge calculée sur une altitude non calée | Pas de marge sur l'altitude pression standard (1013 hPa). La référence d'altitude s'affiche à côté de « Alti. ». |
| **C3** alertes armées seulement après REC | Les alertes s'arment dès qu'un vol est détecté. Le moteur est gardé en arrière-plan pendant un vol détecté. |
| **C4** exceptions Kotlin qui plantent l'app | Un relief ou un pack corrompu, ou une valeur NaN, ne remonte plus jamais jusqu'à Swift. En cas d'erreur, `ui()` rend la dernière image, avec des tirets. |
| Trace figée après 10 min de vol | La carte suit l'instant du dernier point, et non plus le nombre de points, qui plafonne à 600. |
| **M1** son du vario muet après un appel ou un changement de casque | Le son redémarre seul après un appel, un changement de sortie audio ou un redémarrage des services média. |
| **M2** voix muette quand le son du vario est coupé | La voix active sa propre session audio. Une alerte urgente passe devant les autres messages. |
| **M3** position en cache ou invalide acceptée | Une position invalide, imprécise (plus de 500 m) ou vieille de plus de 10 s est ignorée. |
| **M8** démo lançable en vol | Le bouton DÉMO est bloqué pendant un vol. |
| **M12** vent de prévision décalé jusqu'à 3 h 59 | La fenêtre de prévision est bornée à 3 h exactement. |
| **M13** alertes coupées en spirale par vent fort | « Au sol » demande désormais une vitesse sol sous 30 km/h pendant 30 s, hors spirale. Ce point est testé. |
| **M14** URL de carte mal formée | Elle donne une erreur de téléchargement, plus un plantage. |
| **M15** écran figé pendant un menu ouvert | La minuterie tourne en mode « common ». |

## Corrigé : énergie

- **Calcul de l'écran** : `sensors()` ne recalcule plus tout l'écran de vol à chaque tour. La carte Capteurs est mise à jour à 1 Hz.
- **Trace** : elle n'est recopiée vers Swift que lorsqu'elle change.
- **Rafraîchissement des onglets** : l'état du vol est publié dans un objet à part (`FlightModel`), observé par Pilotage seulement. Prévol, Mes vols et la racine ne sont plus redessinés 4 fois par seconde.
- **Carte** :
  - aucune image n'est calculée quand l'onglet Pilotage est caché ;
  - aucune nouvelle caméra n'est demandée quand le planeur ne bouge pas ;
  - la ligne vers le terrain n'est redessinée que dans sa zone ;
  - les halos des étiquettes sont mis en cache.
- **Noms des zones** : les sommets sont convertis une seule fois, les noms sont placés une fois par seconde, et seulement pour les zones proches de la vue.
- **Trafic OGN** :
  - rayon de 60 km au lieu de 250 sur iOS (Android ne change pas) ;
  - arrêt écran éteint ;
  - mise à jour seulement quand le trafic change.
- **Curseur du vario** : sans animation. L'animation de 0,8 s, relancée 4 fois par seconde, forçait l'écran à 60 i/s en continu.
- **Écran éteint en vol** : un tour léger à 1 Hz, rien n'est publié vers l'interface.
- **GPS** : précision « Best » au lieu de « BestForNavigation », toujours à 1 Hz.

## Mesures (iPhone 15, Pilotage en démo, build de développement)

Méthode : `-bench` (build de développement seulement) ouvre Pilotage en démo. `BenchMeter` écrit toutes les 30 s le temps processeur de l'app (en % d'un cœur), l'état thermique et la batterie dans `Documents/bench.log`. Le journal est relu depuis le Mac avec `devicectl`. Instruments (`xctrace`) n'a pas réussi à joindre l'iPhone par le réseau.

| Version / essai | Processeur (% d'un cœur) | Lecture |
|---|---|---|
| Avant (529f7f5) | 40,7 | Référence |
| Optimisations de cette nuit, avant le seuil caméra | 40,8 | Pas d'effet sur ce scénario : les postes allégés (onglets cachés, OGN, écran éteint) n'y jouent pas |
| Sans carte (`-benchNoMap`) | 15,3 | La carte coûte environ 25 points |
| Carte à 15 i/s (`-benchFps 15`) | 35 | La cadence n'est pas le principal levier |
| Sans zones aériennes / sans trace | 39,5 / 40,5 | Ni l'une ni l'autre |
| Carte immobile (`-benchStill`) | 22 | **Le mouvement de la caméra coûte environ 19 points** |
| Caméra animée par MapKit à 2 Hz / 1 Hz | 44 / 44 | Pire : MapKit dessine alors à 60 i/s. Abandonné |
| **Seuil invisible** (pas de caméra pour moins d'un demi-point d'écran et moins de 0,2°) | **29** | **Retenu : environ −30 % pour l'app, sans changement à l'écran attendu** |
| Carte par pas (`-benchStep` : recentrage à 15 % de l'écran ou 10° de route) | 23 | Environ −45 %, mais avec un changement visible : **à décider par JB** |

### Version optimisée (Kotlin et Swift en Release, signée pour le développement)

| Essai | Processeur (% d'un cœur) |
|---|---|
| Pilotage en démo, carte | 27 à 28 |
| Pilotage en démo, sans carte | 13 |

**Profil Time Profiler** (20 s, sans carte), sur environ 11 % de processeur :
- interface SwiftUI de Pilotage : environ 5 % ;
- calcul de l'image de l'écran (`ui()`) : environ 5 %, dont **`formatFixed` pour 3,9 %** ;
- sécurité et relief : environ 3 %, dont 2 % pour le relief ;
- nettoyage mémoire de Kotlin : environ 2 %.

`formatFixed` créait un `NSNumberFormatter` à chaque appel. Il est maintenant mis en cache, avec une sortie identique, vérifiée par les tests de format sur la cible iOS. Dans ce scénario de démo, le gain est faible : 13,4 % → 13,0 %.

**À retenir** : l'app consomme environ 27 % d'un cœur en vol, dont environ 15 points pour la carte. Le plus gros levier restant est la carte par pas (essai : 23 % en build de développement). C'est une décision pour JB.

### Découverte en mesurant : le vario barométrique ne marchait pas sur iPhone

Le baromètre de l'iPhone livre environ 1 mesure par seconde. Le moteur, réglé pour Android (environ 25 Hz, seuil de fraîcheur de 1 s), jugeait chaque mesure périmée. De plus, l'horodatage du capteur dérive par rapport à l'horloge du moteur. Résultat : « Vario indisponible » en permanence.

Corrigé : seuil de 3 s sur iOS, et chaque mesure est datée à son arrivée. Sur 25 relevés consécutifs, le vario affiche 25 fois « Vario baro+accél. ».

**À vérifier en vol** : la réactivité du vario, qui reçoit désormais 1 mesure baro par seconde fusionnée avec l'accéléromètre à 50 Hz.

Limites :
- en build de développement, le Kotlin n'est pas optimisé : les chiffres absolus sont plus élevés qu'en version publiée ;
- l'iPhone est branché : la courbe de batterie n'a pas de valeur ;
- le travail du processeur graphique (MapKit, satellite) n'est pas compté.
