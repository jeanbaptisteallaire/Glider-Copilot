import java.io.File
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
/** Édition construite : « lite » par défaut (S18), « full » avec -Pglidy.edition=full. */
val lite = (findProperty("glidy.edition")?.toString() ?: "lite") != "full"

android {
    namespace = "com.neutronstar.glidercopilot"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.neutronstar.glidercopilot"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        // S18 Lite (lancement Play Store rapide) : Prévol (météo + cartes), Pilotage, Mes vols. Édition complète :
        // ./gradlew -Pglidy.edition=full …  (Feed, Check-lists, Carte, planeur FLARM/OGN dans Prévol).
        versionName = if (lite) "1.9.1-lite" else "0.14.1"
        buildConfigField("boolean", "LITE", lite.toString())
        // MapLibre embarque du code natif : téléphones arm64 et émulateurs x86_64 uniquement
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        // S12 — sauvegarde cloud (Supabase) : URL + clé publique « anon » lues dans les secrets GitHub
        // SUPABASE_URL / SUPABASE_ANON_KEY. Vides (dépôt public, forks, poste local) = cloud inactif.
        buildConfigField("String", "SUPABASE_URL", "\"${System.getenv("SUPABASE_URL").orEmpty().trim()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${System.getenv("SUPABASE_ANON_KEY").orEmpty().trim()}\"")
        // Clé d'API météo precog (secret GitHub PRECOG_API_KEY ; poste local : variable d'environnement). Jamais dans le dépôt.
        // S18.2 — « Continuer avec Google » : ID client OAuth de type « Application Web » (secret GOOGLE_WEB_CLIENT_ID,
        // voir docs/supabase/SETUP.md). Vide = bouton Google masqué, connexion par code e-mail seulement.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${System.getenv("GOOGLE_WEB_CLIENT_ID").orEmpty().trim()}\"")
        buildConfigField("String", "PRECOG_API_KEY", "\"${System.getenv("PRECOG_API_KEY").orEmpty().trim()}\"")
        // V19 — connexion par code e-mail masquée tant qu'un SMTP n'est pas configuré dans Supabase (le SMTP par
        // défaut n'écrit qu'aux membres de l'équipe : refus Google Play pour fonction cassée). Variable EMAIL_LOGIN=true
        // (variable GitHub) pour la réactiver. Sans Google configuré, le code e-mail reste proposé (forks, essais).
        buildConfigField("boolean", "EMAIL_LOGIN", (System.getenv("EMAIL_LOGIN")?.trim() == "true").toString())
    }
    // Clé d'upload Play App Signing (S8) : jamais dans le dépôt (public), lue via 4 secrets d'environnement
    // (KEYSTORE_BASE64 encodé en base64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). Tant qu'ils sont absents
    // (poste local, forks, dépôt tant que JB n'a pas configuré les secrets GitHub), la release reste signée
    // debug pour ne jamais casser la CI existante — voir docs/PLAY-STORE.md pour la procédure complète.
    // S18.2 : valeurs nettoyées (retour à la ligne final souvent collé avec le secret).
    val uploadKeystoreB64 = System.getenv("KEYSTORE_BASE64")?.takeIf { it.isNotBlank() }
    val uploadKeystoreFile = uploadKeystoreB64?.let { b64 ->
        File.createTempFile("glidy-upload", ".jks").apply {
            deleteOnExit()
            writeBytes(Base64.getMimeDecoder().decode(b64.trim()))
        }
    }
    signingConfigs {
        if (uploadKeystoreFile != null) {
            create("release") {
                storeFile = uploadKeystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")?.trim()
                keyAlias = System.getenv("KEY_ALIAS")?.trim()
                keyPassword = System.getenv("KEY_PASSWORD")?.trim()
            }
        }
    }
    buildTypes {
        release {
            // R8 (S9) : code réduit et obscurci pour la version publiée ; ressources inutilisées retirées.
            // Un test de fumée CI installe l'APK release sur émulateur pour vérifier qu'aucune règle ne manque.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (uploadKeystoreFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // S9 : toute erreur lint bloque la CI (0 erreur à ce jour) ; les avertissements restent consignés.
        abortOnError = true
        checkReleaseBuilds = true
        xmlReport = true
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":data:precog"))
    implementation(project(":data:ogn"))
    implementation(project(":data:carto"))
    implementation(libs.maplibre.android)
    implementation(project(":feature:checklist"))
    implementation(project(":feature:prevol"))
    implementation(project(":feature:flight"))
    // S10 — Mes vols
    implementation(project(":core:flightarchive"))
    implementation(project(":data:flightarchive"))
    implementation(project(":feature:myflights"))
    implementation(project(":core:social"))
    implementation(project(":feature:feed"))
    implementation(project(":feature:replay3d"))
    implementation(project(":data:flightcloud"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials.core)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
