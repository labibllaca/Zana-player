// Suppress benign AWT-EventQueue shutdown race exception from headless KSP/IntelliJ compiler plugins
val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
  val isAwtKspRace = (thread?.name?.startsWith("AWT-EventQueue") == true) &&
      throwable is NullPointerException &&
      throwable.stackTrace.any {
        it.className.contains("ksp.com.intellij") ||
        it.className.contains("BinaryFileTypeDecompilers") ||
        it.className.contains("FileDocumentManager")
      }
  if (!isAwtKspRace) {
    prevHandler?.uncaughtException(thread, throwable)
  }
}

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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Zana"

include(":app")
