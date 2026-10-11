// iOS / KMP : utilitaires communs sans dépendance au métier (formats, dates, maths « comme java »).
// Partagé par core:domain et par les modules Mes vols (qui ne doivent pas dépendre du moteur de vol).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { api(libs.kotlinx.datetime) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
