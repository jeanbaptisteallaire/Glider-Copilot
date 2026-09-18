# Architecture de la brique Mes vols

## Décision

La brique est conçue comme une application autonome qui ne reçoit que des fichiers IGC. Elle peut aussi fournir ses modules à GLIDY. Aucun module ne lit les capteurs, ne suit OGN et ne calcule la sécurité du vol.

Le fichier IGC original sera la source de vérité. Les métadonnées locales seront reconstructibles. La synchronisation distante restera inactive tant qu'un compte utilisateur et des règles Supabase sûres ne sont pas disponibles.

## Couches

1. `core:flightarchive` contient le langage du domaine et les ports.
2. `data:flightarchive` implémentera l'accès aux fichiers, l'empreinte et l'index local.
3. `feature:myflights` présentera la liste et le détail.
4. `data:flightcloud` contiendra la file d'attente et le futur adaptateur Supabase.
5. `feature:replay3d` restera séparé et expérimental.
6. `app` assemble les modules pour produire un APK indépendant.

## Flux prévu

Un fichier IGC terminé est découvert par `RecordedFlightSource`. `FlightArchiveRepository` le valide, calcule son empreinte et l'indexe. La page Mes vols lit uniquement le dépôt. Le cloud reçoit plus tard des tâches idempotentes ; il ne lit jamais directement les dossiers Android.

## Compatibilité GLIDY

L'intégration future exige seulement :

- l'accès en lecture au dossier des IGC terminés ou un événement de fin de fichier ;
- l'ajout d'une destination de navigation ;
- la construction des dépendances dans le module `app` de GLIDY.

Le moteur d'enregistrement existant reste l'unique producteur du fichier. La brique ne crée pas un deuxième abonnement GPS.

## Mode application autonome

L'application autonome recevra un IGC par le sélecteur de documents Android ou par partage depuis une autre application. Ce chemin permettra de tester Mes vols et le rejeu sans dépendre de GLIDY.

