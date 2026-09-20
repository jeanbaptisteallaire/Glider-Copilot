# Rapport de livraison — phase 6

Date : 20 septembre 2026

## Résultat

La page **Détail du vol** ouvre maintenant un rejeu 3D utilisable sur Android. Le moteur affiche une texture satellite sur un relief, la trace à son altitude réelle et un planeur 3D procédural. La caméra suit le vol ; un glissement à un doigt tourne autour du planeur, une pincée règle le zoom, la réglette change la position et les boutons proposent lecture/pause, 8×, 32× et 128×.

Le rejeu utilise MapLibre GL JS 5.15.0 et Three.js 0.169.0, embarqués dans le module. Les images Sentinel-2 cloudless sont servies par EOX et le relief Terrarium par AWS Open Data. Leur attribution apparaît dans la carte. Aucune clé d'API n'est nécessaire pour ce prototype. Une connexion Internet reste nécessaire pour charger les tuiles.

## Raccordement au Pilotage

`CompletedFlightGateway` est la seule frontière à appeler depuis le code de Pilotage :

```kotlin
val gateway = LocalArchiveModule.createCompletedFlightGateway(context, archiveRepository)
igcFile.inputStream().use { source ->
    gateway.submitCompletedIgc(igcFile.name, source)
}
```

L'appel doit avoir lieu après la fermeture complète du fichier par l'enregistreur. La passerelle :

- copie le flux dans le dossier privé Mes vols sans toucher à l'original ;
- utilise l'extension `.part` pendant l'écriture ;
- rend le fichier visible seulement après un renommage atomique ;
- valide, déduplique et indexe le fichier dans l'archive ;
- reprend un fichier complet après un redémarrage ;
- élimine un fragment incomplet abandonné.

Le ViewModel lance cette récupération avant sa réconciliation normale. Aucun capteur, service GPS, moteur de Pilotage, composant OGN ou écran de cartographie existant n'est importé.

## Rendu et interaction validés

Le vol synthétique de Saint-Martin-de-Londres contient 6 781 points, dure 1 h 53, parcourt 128 km et monte jusqu'à 2 133 m. Il a servi à vérifier le relief, la texture satellite, le suivi temporel, le planeur et la trajectoire.

Le rendu a été contrôlé dans le navigateur puis dans l'APK Android sur un émulateur Pixel Fold API 35. Un problème de hauteur nulle du conteneur WebView a été détecté durant ce contrôle et corrigé par un dimensionnement explicite au viewport. La rotation à un doigt a ensuite été rejouée et contrôlée sur Android.

Captures :

- `docs/ui-phase6-replay3d.png`
- `docs/ui-phase6-orbit-touch.png`

## Vérifications

- 25 tests Kotlin distincts : parseur et domaine, ViewModel et gateway cloud inactive ;
- 21 tests Android : 11 pour l'archive et la reprise atomique, 9 pour l'interface Mes vols et 1 pour la charge du rejeu ;
- 46 scénarios distincts réussis ;
- Android Lint, compilation de l'APK et contrôle des frontières de 29 fichiers Kotlin ou Gradle réussis ;
- contrôle visuel du rendu satellite/relief/planeur et du geste de rotation sur Android.

## Limites connues du prototype

Le rejeu lit les 512 points d'aperçu conservés par l'archive. Une phase ultérieure pourra charger tous les points du fichier IGC pour un mouvement plus fin et proposer un cache de tuiles hors ligne. Les services publics de tuiles conviennent au prototype ; une diffusion commerciale devra choisir un fournisseur et des conditions d'usage adaptés.

## Livrable APK

- version : `0.6.0-phase6`
- fichier : `dist/glidy-mes-vols-phase6-debug.apk`
- empreinte SHA-256 : `0ce2e2c1da73c0539212ee013f5d6d54bf72494465b9537e82cee21ae29a506e`
