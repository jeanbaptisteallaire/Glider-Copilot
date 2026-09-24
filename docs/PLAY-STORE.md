# Publication sur Google Play — procédure (session 8)

Ce document couvre la partie technique de la publication. Les textes de fiche Play Store et le
brouillon du formulaire « Sécurité des données » sont dans le projet claude.ai « Planneur App »
(`claude/play-store-listing.md`), à valider par JB avant toute soumission.

## Bloquant (dépend de JB)

- Compte Google Play Console **organisation** Neutron Star (D-U-N-S), pas un compte personnel : évite
  le test fermé obligatoire de 12 testeurs / 14 jours imposé aux comptes personnels créés depuis
  novembre 2023.
- Validation des textes de fiche et des captures d'écran (`claude/play-store-listing.md`).
- Génération et conservation de la **clé d'upload** (ci-dessous) — Claude ne génère pas de secrets
  cryptographiques de façon autonome ; cette étape se fait par JB, ou avec son accord explicite dans
  une session suivante.

## 1. Générer la clé d'upload (une seule fois, à conserver précieusement)

Avec Java installé (JDK 17, comme la CI) :

```sh
keytool -genkeypair -v \
  -keystore glidy-upload.jks -storetype PKCS12 \
  -alias glidy-upload -keyalg RSA -keysize 2048 -validity 10957 \
  -dname "CN=Neutron Star, OU=GLIDY, O=Neutron Star, C=FR"
```

`-validity 10957` (~30 ans) : Google exige que la clé reste valide au-delà du 22 octobre 2033.
Le mot de passe du keystore (`-storepass`, demandé interactivement si omis) et celui de la clé
(`-keypass`) doivent être forts et différents. **Ce fichier et ces mots de passe ne doivent jamais
être commités dans le dépôt (public)** — le garder dans un gestionnaire de mots de passe ou un
coffre-fort numérique, avec une copie de sauvegarde hors du Mac.

C'est une clé *d'upload* : avec Play App Signing (activé par défaut pour toute nouvelle app), Google
gère la vraie clé de signature. Si la clé d'upload est perdue, Google permet de la réinitialiser via
une procédure de support — ce n'est pas irrattrapable, contrairement à une clé de signature classique.

## 2. Configurer les secrets GitHub Actions

Dans les réglages du dépôt (`Settings > Secrets and variables > Actions`), ajouter 4 secrets :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i glidy-upload.jks \| tr -d '\n'` (contenu du fichier, encodé) |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `glidy-upload` |
| `KEY_PASSWORD` | mot de passe de la clé |

`app/build.gradle.kts` (câblé en session 8) lit ces 4 variables d'environnement ; si elles sont
absentes, la release reste signée avec la clé de debug (comme avant), donc la CI existante n'est
jamais cassée par leur absence.

## 3. Construire le bundle signé (AAB)

En local, une fois les 4 valeurs en variables d'environnement :

```sh
export KEYSTORE_BASE64=$(base64 -i glidy-upload.jks | tr -d '\n')
export KEYSTORE_PASSWORD=... KEY_ALIAS=glidy-upload KEY_PASSWORD=...
./gradlew bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

**Depuis S9, la CI construit l'AAB à chaque push** (`ci-data/build-report/release/glidy-release.aab`,
avec `mapping.txt.gz` à téléverser dans Play Console pour lire les plantages). Dès que les 4 secrets
GitHub existent, cet AAB est signé avec la clé d'upload et directement soumissible ; sinon il est signé
debug (refusé par Play, mais utile pour vérifier la construction).

Exigences techniques Play vérifiées en S9 : targetSdk 36 (obligatoire depuis le 31/08/2026), pages
mémoire 16 Ko (bibliothèques natives alignées), format AAB, 64 bits (arm64-v8a).

## 4. Politique de confidentialité

Page prête : `docs/privacy/index.html`. Pour la publier via GitHub Pages (impossible à activer depuis
le bac à sable — l'API `api.github.com` y est bloquée) :

1. `github.com/jeanbaptisteallaire/Glider-Copilot` → **Settings → Pages**.
2. Source : **Deploy from a branch**, branche `main`, dossier `/docs`.
3. URL obtenue (quelques minutes) : `https://jeanbaptisteallaire.github.io/Glider-Copilot/privacy/`

Cette URL est celle à indiquer dans la fiche Play Store et le formulaire Sécurité des données.

## 5. Service de premier plan — justification (formulaire Play Console)

Google demande de justifier tout usage de `FOREGROUND_SERVICE_LOCATION`. Réponse type (à coller telle
quelle dans le formulaire) :

> GLIDY est une aide de vol à voile. Le service de premier plan garde le baromètre, l'accéléromètre et
> le GPS actifs écran éteint pendant un vol, pour calculer en continu l'altitude, le vario et les
> alertes de sécurité (terrain, terrain de dégagement) et enregistrer la trace de vol — un vol de
> planeur dure plusieurs heures et le pilote garde le téléphone écran éteint la majorité du temps.
> Une notification permanente indique que le suivi est actif et permet de l'arrêter à tout moment.

## État à la fin de la session 8

- [x] `app/build.gradle.kts` câblé pour une release signée via secrets (repli sûr sur debug).
- [x] Politique de confidentialité rédigée (`docs/privacy/index.html`).
- [x] Justification du service de premier plan rédigée.
- [ ] Clé d'upload générée (bloquant : action volontaire de JB, cf. §1).
- [ ] Secrets GitHub configurés (bloquant : après §1).
- [ ] Pages activé (bloquant : action JB, cf. §4).
- [ ] Compte Play Console organisation (bloquant : D-U-N-S, hors du champ de cette session).
- [ ] Fiche Play Store et formulaire Sécurité des données validés (brouillon dans
      `claude/play-store-listing.md`) puis soumis.
