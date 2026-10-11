// iOS / KMP : contrats du carnet Mes vols et parseur IGC, communs Android / iOS.
// Frontière Mes vols : jamais de dépendance au moteur de vol (core:domain), aux cartes ni à OGN —
// seulement core:common (formats, dates) et Okio (flux d'octets).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    compilerOptions { optIn.add("kotlin.uuid.ExperimentalUuidApi") }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(libs.okio)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(libs.junit) }
    }
}

tasks.named<Test>("jvmTest") {
    inputs.file(rootProject.file("samples/saint-martin-de-londres-vol-synthetique.igc"))
    systemProperty(
        "glidy.generated.sample",
        rootProject.file("samples/saint-martin-de-londres-vol-synthetique.igc").absolutePath,
    )
}
