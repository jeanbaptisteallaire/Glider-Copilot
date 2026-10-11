// Cartes hors ligne : catalogue des packs régionaux, données openAIP, relief (PMTiles + PNG Terrarium).
// iOS / KMP : lecture des packs en commun (Okio) ; téléchargement et style MapLibre restent sur la JVM (Android).
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
            api(project(":core:domain"))
            api(project(":data:precog")) // parseur JSON partagé, Okio, verrou commun
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

// Tests communs (JVM + simulateur iOS) : chemin des extraits réels du dépôt, lus directement sur le disque du Mac.
val genTestPaths by tasks.registering {
    val out = layout.buildDirectory.dir("generated/testPaths")
    val fixtures = projectDir.resolve("src/jvmTest/resources").absolutePath
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/neutronstar/glidercopilot/carto/TestPaths.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.neutronstar.glidercopilot.carto\n\ninternal const val FIXTURES_DIR = \"${fixtures.replace("\\", "/")}\"\n")
    }
}
kotlin.sourceSets.commonTest { kotlin.srcDir(genTestPaths) }
