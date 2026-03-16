import org.gradle.api.GradleException

val mapboxDownloadsToken = providers
    .gradleProperty("MAPBOX_DOWNLOADS_TOKEN")
    .orElse(providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN"))

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
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                val token = mapboxDownloadsToken.orNull
                    ?: throw GradleException("Mapbox downloads token missing. Set MAPBOX_DOWNLOADS_TOKEN in gradle.properties or environment.")
                password = token
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

rootProject.name = "My Application"
include(":app")
 