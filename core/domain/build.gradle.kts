// iOS / KMP étape 1 : métier commun (commonMain), cible jvm consommée telle quelle par Android, cibles iOS pour SwiftUI.
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
            api(project(":core:common"))
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}
