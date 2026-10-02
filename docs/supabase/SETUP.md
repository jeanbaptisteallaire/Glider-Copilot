# Sauvegarde des vols GLIDY — mise en place Supabase (≈ 15 min)

Tout le code est prêt dans l'app (V0.9.2) : la carte **Compte · sauvegarde en ligne** de l'onglet Mes vols
affiche « bientôt disponible » tant que les deux secrets ci-dessous n'existent pas. Dès qu'ils sont créés,
la prochaine CI construit un APK avec la connexion par code e-mail et la sauvegarde activées.

## 1. Créer le projet
1. Va sur https://supabase.com et crée un compte (gratuit), puis **New project**.
2. Nom : `glidy`. **Region : Europe (Paris `eu-west-3` ou Frankfurt `eu-central-1`)**. C'est obligatoire
   pour le RGPD.
3. Choisis un mot de passe de base de données et garde-le pour toi : l'app ne s'en sert pas.
4. Sur la page *Organization → Legal documents*, accepte le **DPA** (accord de traitement des données).

## 2. Créer les tables et la sécurité
1. Ouvre *SQL Editor → New query*.
2. Colle tout le fichier `docs/supabase/schema.sql` du dépôt, puis clique sur **Run**.
3. Vérifie le résultat :
   - *Table Editor* affiche `profiles` et `flights`, avec le cadenas « RLS enabled » ;
   - *Storage* affiche un bucket privé `igc`.

## 3. Connexion par code (sans mot de passe, sans lien)
1. *Authentication → Sign In / Providers → Email* : laisse **Enable Email provider** activé. Désactive
   *Confirm email* : le code sert déjà de confirmation.
2. *Authentication → Emails → Magic Link* : remplace le contenu du modèle par, par exemple :
   ```
   <h2>Votre code GLIDY</h2>
   <p>Saisissez ce code dans l'app : <strong>{{ .Token }}</strong></p>
   <p>Il expire dans une heure. Si vous n'avez rien demandé, ignorez ce message.</p>
   ```
   C'est `{{ .Token }}` qui envoie le code à 6 chiffres au lieu d'un lien.
3. *Authentication → Emails → SMTP* : l'envoi gratuit de Supabase est limité à quelques e-mails par heure
   (suffisant pour les tests). Pour 100 pilotes, branche plus tard un SMTP (Brevo, gratuit jusqu'à
   300 e-mails/jour).

## 4. Donner l'URL et la clé à la CI (jamais dans le code)
1. Dans Supabase, ouvre *Project Settings → API* et copie :
   - **Project URL**, de la forme `https://xxxx.supabase.co` ;
   - la clé **anon / public**. ⚠️ Pas la clé `service_role`.
2. Sur GitHub, ouvre *Glider-Copilot → Settings → Secrets and variables → Actions → New repository
   secret* et crée deux secrets :
   - `SUPABASE_URL` = l'URL ;
   - `SUPABASE_ANON_KEY` = la clé anon.
3. Relance la CI (*Actions → android → Run workflow*) ou pousse n'importe quel commit. L'APK suivant
   aura la sauvegarde active.

## 5. Tester
1. Installe l'APK, puis ouvre *Mes vols → Compte*.
2. **Se connecter**, saisis ton e-mail, **Recevoir un code**, saisis le code, puis **Valider**.
3. La sauvegarde part toute seule. La ligne « N / N vols sauvegardés » passe au vert.
4. Dans Supabase, *Table Editor → flights* affiche tes vols et *Storage → igc* tes fichiers `.igc.gz`.
5. **Test de restauration** : désinstalle l'app, réinstalle-la, reconnecte-toi. Tes vols reviennent.

## 6. Connexion « Continuer avec Google » (S18.2, ≈ 15 min)
Dans Mes vols, **Se connecter** propose *Continuer avec Google* puis, en dessous, le code par e-mail.
Le bouton Google n'apparaît que si le secret `GOOGLE_WEB_CLIENT_ID` existe. Les vols suivent le compte :
après une mise à jour, une réinstallation ou sur un nouveau téléphone, il suffit de se reconnecter.

### 6.1 Google Cloud
1. https://console.cloud.google.com → nouveau projet `GLIDY`.
2. *Google Auth Platform → Branding* : nom `GLIDY`, e-mail d'assistance, logo facultatif.
   *Audience* : **External**. Les portées par défaut suffisent (`openid`, `email`, `profile`).
3. *Clients → Create client → **Web application*** :
   - nom : `GLIDY Supabase` ;
   - *Authorized redirect URIs* : `https://xxxx.supabase.co/auth/v1/callback` (ton URL Supabase) ;
   - garde l'**ID client** (`….apps.googleusercontent.com`) et le **code secret du client**.
4. *Clients → Create client → **Android*** (un client par clé de signature) :
   - package : `com.neutronstar.glidercopilot` ;
   - SHA-1 : l'empreinte de la clé qui signe l'APK installé. La CI l'écrit dans
     `build-report/signing-sha.txt` (branche `ci-data`) pour l'APK debug et l'APK release.
   - ⚠️ L'APK **debug** de la CI change de clé à chaque build : son SHA-1 ne reste pas valable. Teste avec
     l'APK **release** (`build-report/release/glidy-release.apk`) une fois les secrets `KEYSTORE_*` créés
     (docs/PLAY-STORE.md) : il est signé par ta clé d'upload, dont le SHA-1 ne change plus.
   - Pour le Play Store, ajoute aussi un client Android avec le SHA-1 de la **clé de signature
     d'application** : *Play Console → Test et publication → Intégrité de l'application*.

### 6.2 Supabase
*Authentication → Sign In / Providers → Google* :
- **Enable** ;
- *Client IDs* : l'ID client **Web** ;
- *Client Secret* : le code secret du client Web. Il reste dans Supabase, jamais dans l'app ni le dépôt ;
- laisse *Skip nonce checks* **désactivé** : l'app envoie un nonce à Google et le revérifie côté Supabase.

### 6.3 GitHub
Nouveau secret `GOOGLE_WEB_CLIENT_ID` = l'ID client **Web** (pas l'Android). Le build suivant affiche le
bouton Google.

### 6.4 Tester
1. *Mes vols → Se connecter → Continuer avec Google*, puis choisis ton compte.
2. La sauvegarde part aussitôt : « N / N vols sauvegardés ».
3. Désinstalle, réinstalle, reconnecte-toi avec le même compte Google : les vols reviennent.
4. Erreur « Developer console is not set up correctly » ou code 10 : le SHA-1 de l'APK installé n'est pas
   déclaré dans un client Android (6.1, étape 4).

## Ce que fait l'app (et ne fait pas)
- Le compte est **optionnel**. Sans compte, rien ne change : tout reste sur le téléphone.
- Aucun envoi **pendant un vol enregistré**. La sauvegarde part à l'ouverture de Mes vols, une fois au
  sol, ou avec le bouton *Sauvegarder*.
- Seuls les vrais vols sont envoyés. Le vol d'exemple ne l'est jamais.
- **Supprimer mon compte et mes vols en ligne** efface les fichiers, les lignes et le compte. Les vols
  restent sur le téléphone. Google Play exige aussi une page web de suppression : à faire avant la
  publication (formulaire ou e-mail de contact dans la politique de confidentialité).
- La session du compte n'est jamais sauvegardée dans le cloud de Google, ni transférée vers un nouveau
  téléphone.

## Avant la publication Play Store
- Politique de confidentialité : ajouter le compte (e-mail), les vols sauvegardés (traces = localisation),
  l'hébergeur (Supabase, UE), la durée de conservation (jusqu'à suppression du compte) et les droits
  d'accès et d'effacement.
- Formulaire « Sécurité des données » :
  - e-mail : collecté (code e-mail ou compte Google : e-mail et nom fournis par Google) ;
  - position précise : collectée (traces des vols sauvegardés), optionnelle, liée au compte ;
  - fichiers : collectés ;
  - chiffrement en transit : oui ;
  - suppression possible : oui.
