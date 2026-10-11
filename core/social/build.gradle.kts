// iOS / KMP : modèle du fil des pilotes (Feed) et du profil, commun Android / iOS.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { api(project(":core:common")) }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
