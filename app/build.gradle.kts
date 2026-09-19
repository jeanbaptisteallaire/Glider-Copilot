plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.neutronstar.glidy.flights"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neutronstar.glidy.flights"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-phase3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:flightarchive"))
    implementation(project(":data:flightarchive"))
    implementation(project(":data:flightcloud"))
    implementation(project(":feature:myflights"))
    implementation(project(":feature:replay3d"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
