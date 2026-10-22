# Pilotage : langage visuel de LIGHT PFD (22/10/2026), iOS d'abord

Source : `LIGHT PFD/app/docs/PASSATION-UI-WINDGLIDER.md` (décisions de JB). Android viendra après validation de la maquette iOS.

## Couche facteurs humains

**Ce qu'il faut lire en moins d'une seconde, les yeux surtout dehors :**
1. la marge de sécurité ;
2. la montée ou la descente.

Tout le reste est secondaire.

**Revue du pilote et instructeur de planeur (FI(S)).** Il avait autorité pour modifier les propositions. Il a gardé :
- le ruban ±250 m ;
- le tambour par pas de 10 m ;
- le bloc rouge plein ;
- le vario de −3 à +4.

Il a modifié deux points :
- **l'écart est arrondi prudemment, vers le bas** : −3 m s'affiche « −10 m » et jamais « 0 m » à côté d'un bloc rouge ;
- **le chiffre du vario est calculé en Kotlin commun** : `VarioPalette.valueText`.

**Mode Essentiel** : seule la marge de sécurité reste à l'écran (ni ruban, ni barre vario). Les alertes des capteurs restent toujours visibles.

## Ce qui change dans Pilotage (iOS)

- **Ruban d'altitude « ouvert »**, en haut à gauche de la carte :
  - un filet et des traits tous les 50 m ;
  - un tambour avec les centaines fixes et les deux derniers chiffres qui défilent ;
  - le minimum de sécurité hachuré cyan, en butée s'il sort du ruban ;
  - un trait rouge sous le minimum, et un bloc rouge plein quand on passe dessous ;
  - l'écart affiché sous le tambour.
- **Vario en barre verticale**, juste à droite du ruban (un seul balayage de l'œil, comme sur un PFD) :
  - le gros chiffre au-dessus ;
  - une échelle de −3 à +4 : rouge, orange, jaune (0), vert, cyan, bleu ;
  - le curseur est lissé sur environ 1 s ; le chiffre et le son restent bruts.
- **Panneau vario du bas** : il ne garde que la source, les moyennes et le son.
- **Trace** : couleurs « flash » du même dégradé, 5 pt d'épaisseur, sur un liseré sombre de 8 pt.
- **Mode sombre** : fond satellite ombré.

## Contraste

Le texte du vario sur fond blanc utilise une version assombrie de la palette. Un test vérifie un contraste ≥ 4,5:1 sur toute l'échelle, ainsi que le contraste des couleurs flash sur le liseré.

## À arbitrer par JB

1. **Taille des textes de la carte** : la passation proposait 13 pt minimum, l'instructeur recommande **15 pt** (13 pt seulement pour les unités).
2. **Essentiel** : faut-il retirer aussi la distance, la finesse et le cap de l'en-tête ?
3. **Fond satellite** : seulement en mode sombre (choix actuel), ou aussi en clair ?
4. **Barre vario** : la garder à gauche à côté du ruban (choix actuel, qui libère les boutons à droite), ou la mettre au bord droit comme sur la capture de référence ?
