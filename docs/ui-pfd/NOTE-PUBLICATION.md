# Nouveau design iOS de Wind Glider : note pour la session « Publication »

Demande de JB : intégrer ce design au prochain build après le refus App Review, si c'est raisonnable.

## Où trouver le travail

- **Branche** : `ui-pfd-windglider` du dépôt glidy-repo.
- **Dossier de travail** : `/Users/jeanbaptisteallaire/Planneur APP/glidy-ui-pfd`.
- **Point de départ** : branche `ios-v4-i18n`, commit `8219722`.
- **Périmètre** : iOS seulement ; Android est inchangé.
- **État** : rien n'est poussé. Testé au simulateur et installé sur l'iPhone 15 de JB (build Debug).
- **Captures** : `docs/ui-pfd/captures/`.

## Ce qui change

Aucune nouvelle donnée n'est collectée.

1. **Charte « Godspeed » sur toute l'app** : thème sombre lavande, police SF Pro (Inter est retirée des écrans), app forcée en mode sombre.
2. **Pilotage recomposé** :
   - marge de sécurité et tambour d'altitude en haut ;
   - vario vertical au bord droit ;
   - réglages regroupés dans un menu « ⋯ » ;
   - coupe terrain repliable ;
   - fond satellite (`MKHybridMapConfiguration`) en option ;
   - zones aériennes redessinées ;
   - autres planeurs en flèches blanches.
3. **Prévol** :
   - score météo du jour sur 100, indicatif, calculé sur le téléphone ;
   - choix Aujourd'hui / Demain / Après-demain (même API PRECOG, autre date) ;
   - réglage de la finesse (10, 20 ou 30) et de la marge d'arrivée au terrain.
4. **Option « fréquence radio du terrain »** : fréquence lue dans les données openAIP du pack, jamais inventée.
5. **Nouveau comportement réseau** : au premier lancement, si aucune carte hors ligne n'est installée, l'app télécharge seule le pack régional. Seulement en Wi-Fi (`NWPathMonitor`), jamais sur forfait mobile.
6. **Argument de lancement `-demoFlight`** : actif seulement dans les builds Debug (`#if DEBUG`).

## Ce qui ne change pas

- Entitlements, dont Sign in with Apple.
- Info.plist : autorisation de localisation, modes d'arrière-plan `UIBackgroundModes` (audio et localisation).
- Supabase.
- Mentions de sécurité : « non certifié » et avertissement de prévol.
- Bundle id.

## Questions pour la session « Publication »

1. **Quel était le motif exact du refus ?** Est-ce que ce design le corrige, l'aggrave, ou n'y touche pas ?
2. **Quels risques App Review vois-tu ?** Points à vérifier :
   - captures et fiche App Store à refaire, puisque l'interface change beaucoup (guideline 2.3.3) ;
   - téléchargement automatique du pack en Wi-Fi au lancement ;
   - formulation du score météo, qui doit rester « indicatif » (guideline 1.4, sécurité physique) ;
   - mode sombre forcé ;
   - quelques nouveaux libellés encore en français quand l'app est en anglais.
3. **Que te faut-il de la session « Porter l'UI LIGHT PFD dans Wind Glider » ?** Par exemple : un merge dans ta branche, un numéro de build, ou des captures 6,9" et 6,5" en mode sombre.

Rien n'est mergé ni poussé sans l'accord de JB.
