# spiral — conformité Google Play (V19, 05/10/2026)

Éditeur : Jean-Baptiste Allaire (EI), compte Google Play Developer **organisation** ·
paquet `com.neutronstar.glidercopilot` · édition publiée : **Lite** (versionName `1.9.0-lite`).

## 1. Adresses à saisir dans la Play Console

| Champ de la Play Console | Adresse |
|---|---|
| Politique de confidentialité (Contenu de l'appli + fiche) | https://jeanbaptisteallaire.github.io/Glider-Copilot/confidentialite.html |
| Suppression des comptes (Sécurité des données → lien web) | https://jeanbaptisteallaire.github.io/Glider-Copilot/suppression-compte.html |
| Site Web (fiche) | https://jeanbaptisteallaire.github.io/Glider-Copilot/ |
| Adresse e-mail de contact (fiche) | jb.allaire91@gmail.com |

Les pages viennent du dossier `site/` du dépôt et sont publiées par `.github/workflows/pages.yml`
(GitHub → Settings → Pages → Source : **GitHub Actions**, à activer une fois).

## 2. Audit du code (V19)

| Règle Google Play | État |
|---|---|
| Niveau d'API cible (Android 16 / API 36 exigé pour les nouvelles applis et mises à jour à partir du 31/08/2026) | ✅ `targetSdk = 36`, `compileSdk = 36` |
| Format AAB, signature par clé d'importation (Play App Signing) | ✅ `glidy-release.aab` produit par la CI, clé d'upload `glidy-upload` |
| 64 bits + pages mémoire 16 Ko (cible ≥ Android 15) | ✅ arm64-v8a et x86_64, segments ELF alignés sur 16 Ko (MapLibre, AndroidX) |
| Permissions | ✅ Localisation précise/approximative, service de premier plan « location », notifications, Internet, état réseau/Wi-Fi, vibreur. **Pas** de localisation en arrière-plan, ni SMS/Contacts/Stockage/Caméra/QUERY_ALL_PACKAGES/plein écran |
| Service de premier plan typé (Android 14+) | ✅ `FlightService` `foregroundServiceType="location"`, démarré app visible, notification permanente, arrêté en quittant Pilotage hors vol enregistré. V19 : jamais démarré sans autorisation de localisation |
| Divulgation de la localisation avant la demande système | ✅ V19 : boîte « Position GPS » (usage, lecture écran éteint en vol, données restant sur le téléphone) puis demande Android |
| Suppression du compte dans l'appli | ✅ Mes vols → « ⋯ » → « Supprimer le compte » ; ou « Voir le compte » → « Supprimer mon compte et mes vols en ligne » (fichiers IGC, lignes, compte Supabase) |
| Suppression du compte sur le Web | ✅ `suppression-compte.html` (e-mail prérempli, délais, données supprimées/conservées) |
| Politique de confidentialité accessible dans l'appli | ✅ V19 : page d'accueil (« Conditions · Confidentialité »), avertissement de premier lancement, bas de Mes vols |
| Information avant la collecte de données personnelles (compte) | ✅ V19 : encart au-dessus des boutons de connexion |
| Identifiants de l'appareil | ✅ Pas d'ANDROID_ID, pas d'identifiant publicitaire. V19 : l'indicatif OGN « GLIDYnnnn » est tiré à chaque lancement et n'est plus enregistré |
| Publicité, mesure d'audience, SDK de suivi | ✅ Aucun |
| Sauvegarde Android | ✅ `allowBackup=false` + règles d'extraction |
| Chiffrement en transit des données utilisateur | ✅ HTTPS (Supabase, Google). Le flux OGN APRS (port 14580, en clair) est public, en lecture seule et ne transporte aucune donnée de l'utilisateur (filtre centré sur le terrain choisi) |
| Fonction de connexion opérationnelle pour les testeurs Google | ⚠️ voir § 6 : écran de consentement Google en production + SMTP |
| Contenu généré par les utilisateurs | ✅ Lite : aucun (le fil social n'existe que dans l'édition complète, en démo) |
| Sécurité aérienne / allégations | ✅ Avertissement au premier lancement, CGU § 3 ; aucune allégation de certification |

## 3. Formulaire « Sécurité des données » (Data safety)

- L'appli collecte-t-elle ou partage-t-elle des données ? **Oui**
- Toutes les données collectées sont-elles chiffrées en transit ? **Oui**
- Les utilisateurs peuvent-ils demander la suppression de leurs données ? **Oui** (lien : page de suppression)
- Création de compte : **Oui** — par « Connexion avec Google » (et par e-mail une fois le SMTP configuré). Lien de suppression : `suppression-compte.html`.

| Type de données | Collectée | Partagée | Éphémère | Obligatoire ? | Finalités |
|---|---|---|---|---|---|
| Position → **Position précise** | Oui (traces des vols sauvegardés en ligne) | Non | Non | Facultative | Fonctionnalités de l'appli |
| Infos personnelles → **Adresse e-mail** | Oui | Non | Non | Facultative | Fonctionnalités de l'appli, Gestion du compte |
| Infos personnelles → **Nom** | Oui (nom Google, nom du pilote dans l'IGC) | Non | Non | Facultative | Fonctionnalités de l'appli, Gestion du compte |
| Infos personnelles → **ID utilisateur** | Oui (identifiant du compte) | Non | Non | Facultative | Gestion du compte |
| Fichiers et documents → **Fichiers et documents** | Oui (fichiers IGC) | Non | Non | Facultative | Fonctionnalités de l'appli |

Non déclarés (et pourquoi) : la position approximative, les capteurs, les vols non sauvegardés et le profil local restent sur
le téléphone (non « collectés » au sens de Google) ; aucune donnée d'activité, de diagnostic, d'identifiant d'appareil,
de contact, de photo, de santé ou de paiement n'est transmise. Supabase et Google agissent comme prestataires de services
(pas un « partage »).

## 4. Déclaration « Services de premier plan » (Play Console → Contenu de l'appli)

- Type : **Localisation** (`FOREGROUND_SERVICE_LOCATION`)
- Cas d'usage : *Navigation / suivi d'activité initié par l'utilisateur* (« user-initiated location tracking for navigation and activity recording »).
- Description à coller :
  > spiral is a gliding flight computer. When the pilot opens the flight screen (Pilotage) or records a flight, a foreground
  > service with a persistent notification reads GPS, barometer and accelerometer to compute variometer, safety margin and
  > glide to the home airfield, give audio and vibration alerts and record the IGC flight log while the screen is off.
  > Interrupting or deferring the task would silence safety alerts in flight and lose the flight recording.
  > The service stops when the pilot leaves the flight screen unless a flight is being recorded, and from the notification.
- Vidéo : capture d'écran du téléphone (30–60 s) : ouvrir l'appli → Pilotage → notification « Suivi du vol actif » visible →
  écran éteint/rallumé → retour au menu, la notification disparaît. Mettre la vidéo en « non répertoriée » sur YouTube.

## 5. Autres rubriques « Contenu de l'appli »

- **Accès à l'appli** : « Toutes les fonctionnalités sont disponibles sans accès particulier » (mode invité). Préciser :
  « Le compte est facultatif (sauvegarde en ligne des vols, connexion Google). »
- **Annonces** : Non.
- **Public cible** : 18 ans et plus (ou 16–17 + 18+). Ne pas cocher de tranche de moins de 13 ans.
- **Classification du contenu (IARC)** : catégorie « Utilitaire, productivité, communication ou autre » ; aucune violence,
  aucun contenu sexuel, aucun jeu d'argent, pas d'échange entre utilisateurs (Lite), localisation partagée : **non**.
- **Appli d'actualité / santé / finance / VPN / gouvernement** : non.
- **Catégorie** : Cartes et navigation (ou Sports). Tags : vol à voile, planeur.

## 6. Points à régler AVANT d'envoyer en examen

1. **Écran de consentement OAuth Google** : passer de « Test » à « En production » (Google Cloud → API et services → Écran de
   consentement → Publier l'application). Portées non sensibles (openid, e-mail, profil) : pas de vérification Google requise.
   Renseigner page d'accueil, politique de confidentialité et conditions avec les adresses du § 1. Sans cela, seuls les
   utilisateurs test peuvent se connecter avec Google → fonction cassée pour les testeurs de Google.
2. **Client OAuth Android pour la clé Play App Signing** : après le premier envoi de l'AAB, copier le SHA-1 de
   Play Console → Intégrité de l'appli → Signature, et créer un second client Android (même paquet) dans Google Cloud.
   Sans cela, « Continuer avec Google » échoue sur les installations depuis le Play Store.
3. **Connexion par code e-mail** : masquée en V19 (`EMAIL_LOGIN=false`) car le SMTP par défaut de Supabase n'envoie qu'aux
   membres de l'équipe (2 e-mails/heure). Pour la réactiver : SMTP (Brevo, Resend…) dans Supabase + modèle Magic Link avec
   `{{ .Token }}`, puis variable GitHub `EMAIL_LOGIN=true`.
4. **Compte organisation** : l'obligation de test fermé (12 testeurs pendant 14 jours) ne vise, d'après Google, que les comptes
   personnels créés après le 13/11/2023 ; une diffusion directe en production est possible. Recommandé quand même : un test
   interne rapide pour vérifier la connexion Google avec la clé Play.
5. **Mentions légales** : nom de développeur Google Play (IcarusOne) et adresse ajoutés aux pages (V19). SIREN à ajouter si souhaité.
