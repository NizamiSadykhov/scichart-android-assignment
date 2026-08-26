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
            url = uri("https://www.myget.org/F/abtsoftware/maven")
            content {
                includeGroup("com.scichart.library")
            }
        }
        maven {
            url = uri("https://www.myget.org/F/abtsoftware-bleeding-edge/maven")
            content {
                includeGroup("com.scichart.library")
            }
        }
    }
}

rootProject.name = "scichart"
include(":app")
include(":macrobenchmark")
