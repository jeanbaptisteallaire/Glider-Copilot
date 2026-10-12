# iOS V3 — carte de vol fluide (branche `ios-v3-fluidite`)

Objectif : carte mobile et planeur aussi fluides qu'un GPS de voiture (Google Maps / Plans), sans à-coups ni délai
de rafraîchissement. iOS seulement ; Android et l'interface (disposition, couleurs, textes) inchangés.

## Diagnostic (V2)

1. La caméra ne bougeait que lorsque la position avait changé d'environ 50 m (clé arrondie au 1/2000 de degré),
   puis glissait en 0,9 s : marches d'escalier.
2. Le planeur ne se déplaçait qu'au rythme des données (GPS 1 Hz, état du moteur lu 4 fois par seconde) : sauts.
3. La carte SwiftUI `Map` reconstruisait tout son contenu (trace, espaces aériens, annotations) à chaque
   rafraîchissement de l'écran Pilotage (4 Hz) : saccades.

## Stratégie

| Couche | V3 |
|---|---|
| Capteurs | GPS : `BestForNavigation`, `airborne`, aucun filtre de distance (déjà le cas) ; chaque position est livrée **directement** à la carte (sans attendre le tour de 0,25 s). Le GPS de l'iPhone plafonne à 1 Hz : impossible d'aller plus vite. **Gyroscope** (CMDeviceMotion, déjà actif à 50 Hz) : taux de virage réel du planeur entre deux positions GPS (rotation projetée sur la verticale). |
| Prédiction | `OwnshipPredictor` : extrapolation à chaque image (60 Hz) depuis la dernière position, vitesse et route, **en virage à taux constant** (arc de cercle, donc fluide en spirale). Taux de virage = gyroscope s'il est frais, sinon dérivé des routes GPS. Quand une nouvelle position arrive, l'écart est **résorbé en douceur** (≈ 0,35 s) au lieu d'un saut. Extrapolation limitée à 3 s. |
| Rendu | `FluidMapView` : MKMapView (UIKit) piloté par un `CADisplayLink` (60 images/s) : caméra posée sans animation à chaque image, cap lissé (plus court chemin angulaire), distance lissée (+/−). Planeur = annotation déplacée à chaque image. |
| Contenu | Mises à jour **différentielles** : espaces aériens une fois, trace seulement quand elle s'allonge, trafic OGN par identifiant, route vers le terrain à 4 Hz. |
| Sécurité | Aucun calcul de sécurité ne change : la prédiction ne sert qu'à l'affichage de la carte ; marge, alertes, IGC restent sur les vraies positions (moteur commun). |

## Repli

`FlightMapView` (V2) reste dans le code. Réglage interne `fluidMapOff` (UserDefaults, faux par défaut) ; argument de
lancement `-mapV2` pour revenir à l'ancienne carte. Branche V2 intacte : `ios-lite-fonctions`.
