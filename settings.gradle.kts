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
        maven("https://jitpack.io")  // usb-serial-for-android (adaptadores OBD2 USB)
    }
}

rootProject.name = "PriusLauncher"
include(":app")
include(":cargo")
