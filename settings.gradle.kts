pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = java.net.URI.create("https://storage.googleapis.com/r8-releases/raw") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI.create("https://jitpack.io") }
    }
}

rootProject.name = "LastWave"
include(":app")
include(":core")
include(":wear")
include(":audio:decent-usb-audio-driver")
