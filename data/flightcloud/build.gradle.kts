// iOS / KMP : client Supabase commun (Auth par code e-mail ou jeton Google/Apple, PostgREST, Storage), sans SDK.
// Transport HTTP par plateforme : HttpURLConnection (Android, inchangé), Ktor Darwin (iOS).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:flightarchive"))
            api(libs.kotlinx.coroutines.core)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
