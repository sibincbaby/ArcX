pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "ArcX"

include(":app")

include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:data")
include(":core:ai")
include(":core:domain")

include(":feature:home")
include(":feature:workflow")
include(":feature:runner")
include(":feature:history")
include(":feature:settings")
include(":feature:discover")

include(":integration:entrypoints")

// Frame timing and startup, measured on a real device against a real build. Never a dependency
// of anything — it is the only module that depends on :app rather than the other way round.
include(":benchmark")
