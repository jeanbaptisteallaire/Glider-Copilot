plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(17) }
// OGN : Device Database (DDB) pour l'appairage par immatriculation, trafic APRS à venir.
// Données ODbL ; respect des choix « tracked » / « identified » de la DDB ; pas de redistribution.
dependencies {
    api(project(":core:domain"))
    api(project(":data:precog")) // HttpClient, cache et parseur JSON partagés
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
