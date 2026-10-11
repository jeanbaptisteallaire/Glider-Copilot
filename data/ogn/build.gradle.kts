// OGN : Device Database (DDB) pour l'appairage par immatriculation, trafic APRS en direct.
// Données ODbL ; respect des choix « tracked » / « identified » de la DDB ; pas de redistribution.
// iOS / KMP : logique commune ; seule la socket TCP APRS est propre à la plateforme (java.net / Ktor).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":data:precog")) // HttpClient, cache, verrou et parseur JSON partagés
            api(libs.kotlinx.coroutines.core)
        }
        iosMain.dependencies { implementation(libs.ktor.network) }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(libs.junit) }
    }
}
