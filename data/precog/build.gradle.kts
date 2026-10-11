// iOS / KMP étape 1. Toujours sans parseur JSON tiers (Json.kt interne).
// HTTP : jvmMain garde HttpURLConnection (comportement Android inchangé), iosMain passe par Ktor (moteur Darwin).
// Cache disque : Okio (même format de fichier qu'avant).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(libs.kotlinx.coroutines.core)
            api(libs.okio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}
