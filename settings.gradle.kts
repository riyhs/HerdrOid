pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google {
      content {
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
      }
    }
    mavenCentral()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "HerdrOid"

include(":app")
include(":benchmark")
include(":core:domain")
include(":core:data")
include(":core:protocol")
include(":core:mirror")
include(":core:ssh")

val termlibFork = providers.gradleProperty("termlibFork").orNull
if (termlibFork != null) {
  includeBuild(termlibFork) {
    dependencySubstitution {
      substitute(module("org.connectbot:termlib")).using(project(":lib"))
    }
  }
}
