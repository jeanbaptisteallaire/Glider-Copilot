# Protocole de test — Session 5 (capteurs, vario, trace IGC)

GLIDY 0.5.0. À faire sur ton téléphone Android. Il faut le baromètre pour les tests 1 et 2 ; pour le 3, voir plus bas.
Remplis les cases et renvoie les réponses (ou des captures) : elles servent à régler le filtre en session 6.

## 0. Préparation (2 min)
1. Installer `glidy-v0.5-debug.apk` (par-dessus la 0.4, les réglages sont gardés).
2. Autoriser la **localisation précise** et les **notifications** quand l'app les demande.
   Si l'app avait déjà été lancée : Réglages Android › Applications › GLIDY › Autorisations.
3. Prévol › carte **Capteurs & vols** : noter les valeurs affichées, téléphone posé sur une table.

| Valeur | Attendu | Relevé |
|---|---|---|
| Source du vario | « Vario baro+accél. » | |
| Baro (fréquence) | 10 à 50 Hz | |
| Bruit | 5 à 30 cm | |
| Accél. (fréquence) | 50 Hz environ | |
| GPS (précision) | ± 3 à 15 m dehors, « attente » à l'intérieur | |
| Altitude (ligne sous les valeurs) | proche de l'altitude du lieu, avec sa référence | |

Si « Baro : absent » s'affiche, ton téléphone n'a pas de baromètre : passer directement au test 3.

## 1. Test de l'escalier — réactivité du vario (5 min)
Critère de la session : « Vario sonore réactif en marchant dans un escalier ».

1. Prévol › Capteurs & vols : activer **Son du vario**, puis **Essai du son**.
   Tu dois entendre un grave, un silence, puis des bips de plus en plus rapides et aigus.
2. Aller dans **Pilotage**, téléphone à la main, écran allumé, au pied d'un escalier (au moins 2 étages).
3. Attendre 20 s immobile : le vario doit rester entre −0,2 et +0,2, sans bip.
4. Monter à allure normale, sans t'arrêter : le vario passe à +0,5 / +1 m/s, les bips démarrent.
5. S'arrêter en haut : les bips cessent en moins de 2 s.
6. Redescendre vite : le vario passe à −1 m/s ou moins (pas de son tant qu'on reste au-dessus de −1,7 m/s, c'est normal).
7. Refaire la montée **écran éteint** (bouton marche) : les bips doivent continuer (service de vol, notification « GLIDY »).

| Question | Réponse |
|---|---|
| Délai entre le premier pas et le premier bip (ressenti) | < 1 s / 1–2 s / > 2 s |
| Valeur maximale en montant | |
| Faux bips au repos ? | oui / non |
| Bips écran éteint ? | oui / non |
| La notification GLIDY est-elle visible ? | oui / non |

## 2. Test au sol — chrono, trace, annonces (10 min, voiture)
Critère de la session : « IGC valide ».

1. Prévol : vérifier que **Détec. auto. décollage** est activé (c'est le réglage par défaut).
2. Capteurs & vols : **Essai de la voix** doit dire une phrase en français.
   Si rien : Réglages Android › Synthèse vocale › moteur Google, langue française.
3. Passager en voiture, téléphone ouvert sur Pilotage (ou écran éteint) :
   - dépasser **50 km/h pendant au moins 3 s** : annonce « Décollage détecté, chrono lancé », chrono en marche ;
   - rouler quelques minutes ;
   - s'arrêter et rester **à l'arrêt 30 s** : annonce « Atterrissage. Vol de n minutes enregistré ».
4. Prévol › Capteurs & vols › Vols enregistrés : le trajet apparaît (date, durée, points).
   **Partager** vers mail, Drive ou WhatsApp, puis ouvrir le fichier dans un lecteur IGC, par exemple igcviewer ou WeGlide en mode « visualiser ».

| Question | Réponse |
|---|---|
| Annonce décollage entendue ? | |
| Annonce atterrissage entendue ? | |
| Durée affichée cohérente ? | |
| Trace lisible dans le lecteur IGC ? | |
| Altitude en voiture cohérente (± 20 m) ? | |

La trace est au format IGC d'un enregistreur **non approuvé** (pas de signature G) : lisible partout, sans valeur pour un badge.

## 3. Secours sans baromètre (facultatif)
Si ton téléphone n'a pas de baromètre et que le planeur appairé émet sur OGN, le vario affiche « Vario OGN · n s » avec son retard.
Annonce en vol « Baromètre indisponible, vario OGN en secours ». Rien à tester au sol : OGN ne voit le planeur qu'en vol.

## 4. En vol (quand la saison le permet)
- Téléphone fixé, sans vibration forte (pas contre le tableau de bord qui résonne), son réglé assez fort.
- Comparer le vario GLIDY au vario de bord en spirale : écart typique, retard.
- Après le vol : partager l'IGC, le comparer à la trace FLARM ou OGN.
- **Rappel : le vario de bord et la veille extérieure priment. GLIDY reste une aide secondaire.**

## Démonstration sans bouger
Rejouer le vol de démonstration (34 min, remorqué, spirales réelles OGN anonymisées, retour, atterrissage) :
```
adb shell am start -n com.neutronstar.glidercopilot/.MainActivity --ez glidy.flight.replay true --ef glidy.flight.speed 4
```
Le bandeau « REJEU VOL · capteurs simulés » est alors visible sur la carte. La trace de ce rejeu est rangée à part (`igc-rejeu`).
