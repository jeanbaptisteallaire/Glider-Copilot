pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "GliderCopilot"
include(":app")
include(":core:designsystem")
include(":core:domain")
include(":data:precog")
include(":data:ogn")
include(":data:carto")
include(":feature:checklist")
include(":feature:prevol")
include(":feature:flight")
// S10 — Mes vols (carnet IGC + rejeu 3D), importé de l'app autonome « GLIDY Mes vols »
include(":core:flightarchive")
include(":data:flightarchive")
include(":data:flightcloud")
include(":feature:myflights")
include(":feature:replay3d")
