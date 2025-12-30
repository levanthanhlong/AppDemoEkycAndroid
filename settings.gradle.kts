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
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
        maven { url = uri("https://raw.githubusercontent.com/iProov/android/master/maven/") }
        maven {
            name = "GitHubPackagesGeneral"
            url = uri("https://maven.pkg.github.com/levanthanhlong/CmcEkyc-Sdk")
            // credentials
            credentials {
                username = "levanthanhlong"

            }
        }
    }
}

rootProject.name = "app_demo"
include(":app")
 