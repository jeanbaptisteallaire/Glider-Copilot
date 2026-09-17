# Protocole de test — Session 6 (sécurité, mode démo, suivi & debug)

GLIDY 0.6.0. Ces tests ne demandent pas de voler. Il faut le **pack de carte hors ligne installé** (Prévol › Carte hors ligne) : c'est lui qui fournit le relief et les terrains.

## 1. Mode démo (5 min, au sol)
1. Pilotage : toucher le petit bouton **démo**, en haut à droite de la carte. Il passe en orange « DÉMO ».
2. Le bandeau affiche « DÉMO · planeur simulé · trafic OGN réel ».
   Le planeur simulé enchaîne pompes et transitions à moins de 5 km de LFNL, vers 1 450–1 650 m.
3. Vérifier en haut de l'écran :
   - **Marge** : valeur verte, qui baisse en transition et remonte en spirale ;
   - **LFNL** : distance et cap qui changent avec la position ;
   - sous la marge, la ligne « tendance · 2 min : ±… m ».
4. **Vent** : après deux ou trois spirales, la case vent (à droite de la carte) passe de « vent — » à environ 300°/15. C'est le vent simulé, retrouvé par la dérive des spirales.
5. **Profil de retour** : la coupe indique « relief Copernicus ». Le Pic Saint-Loup et l'Hortus apparaissent quand le planeur est à l'est du terrain.
6. **Trafic** : les planeurs réels (OGN) autour de LFNL restent affichés. La légende donne « n aéronefs (m à 15 km) ».
7. Toucher **F10** : la marge baisse et la ligne de plané de la coupe devient plus raide. Remonter à F20 demande deux appuis (confirmation).
8. Toucher le bloc **AUTO LFNL** : la liste des terrains proches s'ouvre, avec distance et marge. Choisir un autre terrain : l'étiquette passe à « MANU », la route et la coupe suivent. Choisir **AUTO** pour revenir.
9. Toucher **DÉMO** pour quitter : retour aux capteurs du téléphone.

| Question | Réponse |
|---|---|
| Marge et coupe cohérentes avec ce que tu connais du terrain ? | |
| Vent retrouvé (≈ 300°/15) après combien de spirales ? | |
| Relief visible et plausible sur la coupe ? | |
| Planeurs réels visibles pendant la démo ? | |

## 2. Suivi & debug d'un planeur du club en vol (20 min, un jour de vol)
1. Prévol › Planeur du jour : activer **Suivi & debug** (sous « Détec. auto. décollage »).
2. Toucher **F-CGXB** ou **F-CEIQ**, ou saisir une autre immatriculation puis **Suivre**.
3. Tant que le planeur n'a pas été reçu, la ligne affiche « pas encore reçu ». Dès qu'il vole près de LFNL, elle passe en vert : vu il y a n s · altitude · montée · distance du terrain.
4. Pilotage : le bandeau devient « SUIVI F-CGXB · FLARM via OGN · n s ». Tout l'écran est calculé sur ce planeur :
   - position et trace ;
   - vario (source « Vario OGN ») ;
   - marge, terrain AUTO, coupe, vent estimé et projection à 2 min.
5. Les alertes vocales sont actives dans ce mode. Son du vario coupé conseillé : les données OGN arrivent avec quelques secondes de retard.
6. À comparer si possible avec le pilote au sol ou par radio : altitude, marge ressentie, moment où GLIDY aurait annoncé « Marge faible » ou « Sous la sécurité ».
7. Couper **Suivi & debug** en fin de test.

| Question | Réponse |
|---|---|
| Planeur reçu ? retard affiché ? | |
| Vent estimé cohérent avec la météo du jour ? | |
| Terrain AUTO basculé vers un autre terrain ? lequel, quand ? | |
| Alertes entendues et moment (heure) | |

Rien n'est enregistré en mode suivi : pas d'IGC, pour respecter les règles OGN.

## 3. Alertes (à vérifier en démo ou en suivi)

| Alerte | Déclenchement | Voix | Vibration |
|---|---|---|---|
| Marge nulle dans 2 minutes | la projection à 2 min passe sous 0 alors que la marge est encore positive | « Marge nulle dans deux minutes » | 2 courtes |
| Marge faible | marge sous 150 m (retour au calme au-dessus de 200 m) | « Marge faible, n mètres » | 2 courtes |
| Sous la sécurité | marge négative, **répétée toutes les 30 s** ; bandeau rouge « CAP TERRAIN » | « Sous la sécurité. Cap <terrain>, n degrés, n km » | 3 longues |
| Relief | relief sur la route plus contraignant que l'arrivée | « Relief sur la route du terrain à n km » | 3 longues |
| Terrain de repli | le terrain du club n'est plus rejoignable, un autre l'est | « Terrain de repli : <nom>… » | 2 courtes |
| Marge rétablie | marge remontée au-dessus de 200 m | « Marge rétablie » | 1 courte |

- Alertes armées seulement quand la marge a été confortable une première fois : rien au remorqué ni au treuil.
- Silence dans le circuit (terrain à moins de 1,5 km, ou à moins de 3 km et atteignable) et au sol.
- Hypothèses de calcul, affichées à l'écran :
  - vitesse de plané 90 km/h ;
  - arrivée +300 m au-dessus du terrain ;
  - relief franchi à +100 m ;
  - vent estimé en spirale ;
  - finesse choisie (F20 / F15 / F10).

**Rappel : GLIDY reste une aide secondaire. L'instrumentation de bord et la veille extérieure priment.**
