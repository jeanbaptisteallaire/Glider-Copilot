# Architecture de la brique Mes vols

## Décision

La brique est conçue comme une application autonome qui ne reçoit que des fichiers IGC. Elle peut aussi fournir ses modules à GLIDY. Aucun module ne lit les capteurs, ne suit OGN et ne calcule la sécurité du vol.

Le fichier IGC original sera la source de vérité. Les métadonnées locales seront reconstructibles. La synchronisation distante restera inactive tant qu'un compte utilisateur et des règles Supabase sûres ne sont pas disponibles.

## Couches

1. `core:flightarchive` contient le langage du domaine et les ports.
2. `data:flightarchive` implémente l'accès aux fichiers, l'empreinte, l'index local et la réception atomique d'un IGC terminé.
3. `feature:myflights` présente la liste, le détail et le départ vers le rejeu.
4. `data:flightcloud` contiendra la file d'attente et le futur adaptateur Supabase.
5. `feature:replay3d` contient le moteur de rejeu 3D expérimental, sans dépendre de la carte de Pilotage.
6. `app` assemble les modules pour produire un APK indépendant.

## Flux prévu

À la fermeture d'un IGC, Pilotage peut le remettre à `CompletedFlightGateway`. La passerelle écrit d'abord un fichier `.part` privé, le renomme seulement après la copie complète, puis demande à `FlightArchiveRepository` de le valider et de l'indexer. Au démarrage, les fichiers complets restés en attente sont repris et les fragments abandonnés sont supprimés. La page Mes vols lit uniquement le dépôt. Le cloud reçoit plus tard des tâches idempotentes ; il ne lit jamais directement les dossiers Android.

## Compatibilité GLIDY

L'intégration future exige seulement :

- un appel à `CompletedFlightGateway.submitCompletedIgc` après fermeture complète de l'IGC ;
- l'ajout d'une destination de navigation ;
- la construction des dépendances dans le module `app` de GLIDY.

Le moteur d'enregistrement existant reste l'unique producteur du fichier. La brique ne crée pas un deuxième abonnement GPS.

## Rejeu 3D

Le rejeu reçoit uniquement un `ArchivedFlight`. Il transforme son aperçu géographique et son profil d'altitude en une charge JSON privée, chargée dans un WebView par `WebViewAssetLoader`. MapLibre GL JS dessine le relief et Three.js dessine la trajectoire aérienne et le planeur procédural. Les bibliothèques sont embarquées ; les tuiles satellite et d'altitude sont chargées sur Internet.

## Mode application autonome

L'application autonome recevra un IGC par le sélecteur de documents Android ou par partage depuis une autre application. Ce chemin permettra de tester Mes vols et le rejeu sans dépendre de GLIDY.
