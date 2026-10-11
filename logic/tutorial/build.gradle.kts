// Logique commune (plan iOS, couche logic:*) : contenu et déroulé du tutoriel, identiques sur Android et iOS.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
