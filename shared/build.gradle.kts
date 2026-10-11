// iOS / KMP — framework unique « WindGliderShared » pour l'app SwiftUI (ios/). Aucune cible Android :
// Android consomme directement core:domain et data:precog. Modules à ajouter plus tard : data:ogn,
// core:flightarchive (voir CLAUDE.md, section iOS / KMP).
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}
kotlin {
    jvmToolchain(17)
    val xcf = XCFramework("WindGliderShared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "WindGliderShared"
            isStatic = true
            export(project(":core:domain"))
            export(project(":data:precog"))
            export(project(":data:carto"))
            xcf.add(this)
        }
    }
    sourceSets {
        iosMain.dependencies {
            api(project(":core:domain"))
            api(project(":data:precog"))
            api(project(":data:carto"))
        }
    }
}
