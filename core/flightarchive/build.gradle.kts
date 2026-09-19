plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    inputs.file(rootProject.file("samples/saint-martin-de-londres-vol-synthetique.igc"))
    systemProperty(
        "glidy.generated.sample",
        rootProject.file("samples/saint-martin-de-londres-vol-synthetique.igc").absolutePath,
    )
}
