pluginManagement {
    repositories {
        google()
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

rootProject.name = "que-mobile-sdk"

include(":que-core")
include(":que-vision")
include(":que-actions")
include(":que-llm")
include(":que-platform-android")
include(":que-expo")
