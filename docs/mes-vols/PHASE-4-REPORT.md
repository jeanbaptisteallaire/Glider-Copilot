# Rapport de livraison — phase 4

Date : 19 septembre 2026

## Résultat

La page **Mes vols** possède maintenant une logique de présentation indépendante de Compose et de l'activité Android. Le ViewModel charge l'archive, trie les vols du plus récent au plus ancien, conserve la sélection et traduit les problèmes locaux en états compréhensibles par l'interface.

Le projet reste autonome. Aucun réseau n'est nécessaire et aucun module de pilotage, d'OGN, de sécurité ou de cartographie appartenant au travail de Claude n'est référencé.

## Fonctions livrées

- ViewModel dédié avec flux d'état observable ;
- états explicites : chargement, contenu et erreur récupérable ;
- état vide avec accès direct à l'import IGC ;
- tri décroissant par date de départ ;
- navigation liste vers détail et retour ;
- conservation visible des entrées dont le fichier est manquant ou invalide ;
- message de réconciliation pour les fichiers manquants ou invalides ;
- relance manuelle après erreur de chargement ;
- import IGC piloté par le ViewModel avec fermeture sûre du flux ;
- libellé d'accessibilité donnant la position de chaque vol dans la liste ;
- liste Compose paresseuse vérifiée avec 500 vols.

## Vérifications de séance

- 5 tests unitaires du ViewModel : chargement différé, tri, fichier manquant, navigation, erreur puis reprise et corpus de 500 vols ;
- 6 tests Compose sur émulateur : chargement, erreur et relance, état vide, ordre et libellés, sélection et retour, défilement jusqu'au 500e vol ;
- capture petit écran en 1080 × 1920 ;
- capture grand écran en 1440 × 2560 ;
- fonctionnement entièrement local avec faux dépôt pour les scénarios de volume.

Les contrôles complets du projet couvrent également les 16 tests du domaine IGC, le test du gateway cloud inactif et les 6 tests Android de l'archive Room.

Au total, 34 scénarios distincts réussissent. La compilation de l'APK, Android Lint, le contrôle des différences Git et la vérification des frontières de 25 fichiers Kotlin ou Gradle réussissent également.

## Captures

- `docs/ui-phase4-small-screen.png`
- `docs/ui-phase4-large-screen.png`

## Livrable APK

- fichier : `dist/glidy-mes-vols-phase4-debug.apk`
- version : `0.4.0-phase4`
- SHA-256 : `e55d19c6fae71c3c6bfba81e943340fdeedc1cd544e612e1b9e024f7e7ccb651`

## Limites conservées

Les actions de partage et de suppression avec confirmation relèvent de la phase 5. Le raccordement à la fin d'enregistrement du pilotage relève de la phase 6. Supabase reste inactif jusqu'à la phase 7. Le rejeu 3D reste un chantier exploratoire séparé après les huit phases fonctionnelles.
