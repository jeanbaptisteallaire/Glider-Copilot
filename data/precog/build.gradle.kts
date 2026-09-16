plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(17) }
// Module volontairement sans dépendance HTTP/JSON tierce : java.net + parseur JSON interne,
// pour rester testable hors ligne et léger dans l'APK.
dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
