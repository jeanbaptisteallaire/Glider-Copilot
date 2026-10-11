# Wind Glider iOS — plan de parité avec Android (édition complète)

Rédigé le 11/10/2026. Objectif : toutes les fonctions Android sur iOS (édition complète comprise : trafic OGN, comptes,
base de données, tutoriel), avec un code **le plus commun possible** pour que chaque évolution future se fasse une fois.

## 1. Principe d'architecture

```
            ┌──────────────── Android (Compose) ───────────────┐   ┌──────── iOS (SwiftUI) ────────┐
 Écrans     │ feature:prevol · feature:flight · feature:myflights│   │ ios/WindGlider/Sources/<onglet>│
            │ feature:feed · feature:checklist · app (AppRoot)  │   │                                │
            └───────────────┬───────────────────────────────────┘   └──────────────┬─────────────────┘
                            │       appelle                                        │ appelle (WindGliderShared)
            ┌───────────────▼───────────────────────────────────────────────────────▼─────────────────┐
 Logique    │  logic:*  (KMP, NOUVEAU) : états d'écran, textes français formatés, orchestration       │
 commune    │  logic:flight (session de vol) · logic:prevol · logic:account · logic:traffic · logic:feed │
            └───────────────┬────────────────────────────────────────────────────────────────────────┘
 Données    │  core:domain ✅ · data:precog ✅ · data:carto ✅ · data:ogn · core:flightarchive ·         │
 (KMP)      │  data:flightarchive (Room KMP) · data:flightcloud (Supabase) · core:social                │
            └──────────────────────────────────────────────────────────────────────────────────────────┘
 Plateforme : capteurs, son, voix, notifications, carte, connexion Google/Apple → minces adaptateurs par plateforme
```

Règles (à ajouter à CLAUDE.md) :
1. **Toute règle métier, tout calcul, tout texte affiché** vit en Kotlin commun (`core:*`, `data:*`, `logic:*`).
   Android et iOS ne font que dessiner un état et transmettre des actions.
2. Un écran = un **état commun** (`data class …Ui`) + des **actions** (`interface …Actions`) dans `logic:<onglet>`.
   Compose et SwiftUI l'affichent chacun à leur façon. Une modification de règle = un seul changement, testé une fois.
3. Comportement Android inchangé à chaque étape : tests JVM existants + tests de parité ; Pilotage reste sous
   garde-fou (`check_en_vol.py`), aucune modification des fichiers protégés sans accord de JB.
4. Ce qui reste par plateforme : capteurs (SensorManager / CoreMotion), GPS, son, voix, carte (MapLibre Android /
   MapLibre iOS), connexion Google (Credential Manager / GoogleSignIn iOS), Apple (AuthenticationServices),
   stockage sécurisé de la session (DataStore chiffré / Keychain).

## 2. Organisation des dossiers

```
glidy-repo/
├── core/, data/            modules communs (KMP) — inchangés de place
├── logic/                  NOUVEAU : logique d'écran commune (KMP)
│   ├── flight/  prevol/  account/  traffic/  feed/  myflights/  tutorial/
├── shared/                 framework iOS WindGliderShared (export de core/data/logic) — façades minces seulement
├── app/, feature/          Android (Compose)
├── ios/                    iOS
│   ├── project.yml         (XcodeGen)
│   └── WindGlider/
│       ├── Sources/
│       │   ├── App/          WindGliderApp, RootView, AppModel, éditions Lite/complète
│       │   ├── Common/       Theme (jetons), composants (WGCard, kpi, pastilles), Fmt
│       │   ├── Platform/     Sensors, Audio, Voice, Keychain, GoogleSignIn, AppleSignIn
│       │   ├── Welcome/      couverture animée + 3 boutons (Google, Apple, invité), avertissement
│       │   ├── Home/         menu d'accueil illustré
│       │   ├── Prevol/  Pilotage/  Carte/  MesVols/  Feed/  Checklists/  Tuto/  Compte/
│       ├── Resources/        Assets (icône, couleurs) ; le reste vient des dossiers Android (pas de copie)
│       └── Tests/            tests XCTest et captures (comme la CI Android)
└── docs/ios/               PLAN-IOS.md, ARCHITECTURE.md, rapports de phase
```

Pas de déplacement des modules Android existants (chemins Gradle stables, CI et garde-fous inchangés).
Le dossier `ios/` est réorganisé par onglet (phase 0).

## 3. Phases

| Phase | Contenu | Livrable vérifiable |
|---|---|---|
| **0. Fondations** | Réorganisation `ios/` par onglet ; `docs/ios/ARCHITECTURE.md` ; règles dans CLAUDE.md ; éditions Lite / complète iOS (configurations Xcode, équivalent `BuildConfig.LITE`) ; CI iOS avec tests XCTest. | build Lite et complète, CI verte |
| **1. Données en KMP** | `data:ogn` (APRS en Ktor sockets, `java.time` → kotlinx), `core:flightarchive`, `data:flightarchive` (Room KMP, même schéma que l'APK), `data:flightcloud` (Supabase via Ktor, gzip Okio), `core:social`. Tests JVM existants verts + tests communs sur simulateur. | Android inchangé (tests, lint, APK), tests iOS verts |
| **2. Logique commune `logic:*`** | `logic:flight` : session de vol commune (aujourd'hui dupliquée entre `FlightEngine.kt` et `IosFlightEngine`), avec test de parité sur le vol de démonstration (mêmes marges, mêmes annonces). Puis `logic:prevol`, `logic:myflights`, `logic:account`. Android bascule module par module, sans toucher aux fichiers Pilotage protégés. | même résultat Android/iOS sur le rejeu de démo |
| **3. Comptes et base de données** | Couverture animée (page HTML d'Android) + **3 boutons** : Continuer avec Google, Se connecter avec Apple, Continuer en invité. Même projet Supabase qu'Android : `signInWithIdToken` (Google et Apple, nonce), connexion par code e-mail, session dans le Keychain, sauvegarde des vols, suppression du compte dans l'app (exigée par Apple). | connexion Google et Apple réelles sur iPhone, vols retrouvés entre Android et iOS |
| **4. Trafic OGN** | Pilotage : planeurs autour (immatriculation DDB, cap, montée), pompes du réseau, pastille « OGN n aéronefs · n pompes », vario OGN de secours ; Prévol : appairage de son planeur FLARM ; respect ODbL, choix DDB, pas de conservation > 24 h. | trafic réel affiché autour de LFNL |
| **5. Onglets de l'édition complète** | Accueil (menu illustré), **Tuto** (visite guidée sur les vraies pages, mêmes captures `tour_*` et textes FR/EN), Feed (suivre des pilotes), Check-lists, Carte (MapLibre iOS hors ligne avec les mêmes packs et le même style), Mes vols complet (partage, sauvegarde en ligne), « Donner mon avis ». | parcours complet sur simulateur + iPhone |
| **6. Conformité et publication** | Politique de confidentialité et fiche « Confidentialité » App Store (position envoyée à OGN et à la météo, compte, vols), Sign in with Apple, suppression du compte, mention des sources ; TestFlight. | build TestFlight |

Ordre conseillé : 0 → 1 → 3 → 2 → 4 → 5 → 6 (les comptes avant la logique commune, car ils débloquent les
essais réels de la sauvegarde ; la logique commune avant le trafic, car OGN se branche sur la session de vol).

## 4. Ce dont j'ai besoin de JB

1. **Base de données Supabase** : l'accès en lecture au projet (schéma, réglages Auth) — le connecteur Supabase de
   cette session suffit si tu m'autorises à l'utiliser. Toute modification (activer Apple, ajouter l'identifiant
   client Google iOS) te sera demandée avant.
   Les valeurs publiques `SUPABASE_URL` et `SUPABASE_ANON_KEY` (déjà dans les secrets GitHub pour Android).
2. **Compte Apple Developer** (payant, 99 €/an) : Team ID, identifiant d'app définitif (proposé :
   `com.neutronstar.glidercopilot`, comme Android), capacité « Sign in with Apple ». Indispensable pour Apple Login,
   l'essai sur iPhone et TestFlight.
3. **Google Cloud** (projet du login Android) : création d'un identifiant OAuth « iOS » pour l'identifiant d'app
   ci-dessus, puis ajout de cet identifiant dans Supabase (Auth → Google → Authorized Client IDs).
4. **Clé PRECOG** pour les essais réels de la météo.
5. Un **iPhone de test** branché au Mac (baromètre, arrière-plan, son, connexions réelles).

## 5. Risques et points d'attention

- **Pilotage protégé** : extraire la session de vol commune ne touche pas `FlightScreen.kt` ; si un fichier protégé
  doit bouger, je demande d'abord.
- **Room KMP** : même base sur les deux plateformes, migration du schéma existant à vérifier (tests de migration).
- **Apple** : Sign in with Apple obligatoire dès qu'un login tiers existe ; suppression du compte dans l'app ;
  justification de la localisation en arrière-plan à la revue.
- **OGN en arrière-plan** : connexion TCP maintenue par la localisation active pendant le vol (à valider sur iPhone).
