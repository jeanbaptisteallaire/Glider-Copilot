plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(17) }
// Cartes hors ligne : catalogue des packs régionaux, téléchargement vérifié, données openAIP, style de carte.
// JVM pur (testable hors ligne) ; le rendu MapLibre reste dans feature:flight.
dependencies {
    api(project(":core:domain"))
    api(project(":data:precog")) // parseur JSON partagé
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
