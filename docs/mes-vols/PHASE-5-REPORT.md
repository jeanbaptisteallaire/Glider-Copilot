# Rapport de livraison — phase 5

Date : 19 septembre 2026

## Résultat

Le détail d'un vol permet maintenant de consulter les informations IGC utiles, de partager le fichier et de supprimer sa copie locale après confirmation. Ces actions passent par des ports dédiés et restent indépendantes du pilotage, d'OGN, de la sécurité et de la cartographie de Claude.

## Détail du vol

La page présente la trace, le profil d'altitude, la durée, la distance, les altitudes minimale et maximale, le gain positif, le pilote, le planeur, le nombre de points, le nom du fichier, sa taille, une empreinte courte et son état local.

Un fichier marqué invalide reste consultable. Sa fiche conserve le diagnostic et autorise son partage tant que le fichier existe. Un fichier manquant reste visible, mais son partage est désactivé.

## Partage Android

Le partage est défini par `FlightShareGateway`. Son implémentation Android :

- vérifie que le vol et le fichier existent ;
- refuse tout chemin sortant du dossier d'archive ;
- prépare dans le cache une copie portant le nom IGC lisible ;
- expose cette copie par `FileProvider`, jamais par un chemin absolu ;
- utilise le type `application/vnd.fai.igc` ;
- accorde uniquement une lecture temporaire à l'application choisie ;
- ouvre le sélecteur Android.

Le menu de partage a été ouvert et contrôlé sur l'émulateur Android. Le téléphone physique n'était pas connecté à cette séance ; l'essai sur téléphone reste donc un contrôle matériel de livraison.

## Suppression contrôlée

Le bouton **Supprimer de cet appareil** ouvre une confirmation qui nomme le fichier et précise que son index local sera également retiré. Annuler ferme la boîte sans appel au dépôt. Confirmer supprime la copie privée et l'entrée Room, puis revient à la liste. Les autres vols restent inchangés. En cas d'échec du fichier, l'entrée n'est pas retirée silencieusement et un message est affiché.

## Vérifications

- 8 tests du ViewModel : les 5 scénarios de phase 4, partage, annulation et suppression ciblée ;
- 8 tests Compose : les 6 scénarios de phase 4, confirmation/annulation et diagnostic d'un fichier invalide ;
- 8 tests Android de l'archive et du partage : import, reconstruction, doublons, absence, invalidité, suppression, URI de partage et refus d'un fichier manquant ;
- 16 tests du domaine et du parseur IGC ;
- 1 test du gateway cloud inactif ;
- 41 scénarios distincts réussis ;
- Android Lint, compilation de l'APK, contrôle Git et frontières de 26 fichiers Kotlin ou Gradle : réussis.

## Captures

- `docs/ui-phase5-detail-actions.png`
- `docs/ui-phase5-share-sheet.png`
- `docs/ui-phase5-delete-confirmation.png`

## Livrable APK

- fichier : `dist/glidy-mes-vols-phase5-debug.apk`
- version : `0.5.0-phase5`
- SHA-256 : `90ccbe6a493ee655d21328c4e6a7792c54c94900435971e9d6f49800a18dc5de`

## Suite prévue

La phase 6 raccordera l'archive à la fin d'enregistrement du pilotage par un événement étroit ou un scan de repli, sans créer un second enregistreur GPS.

