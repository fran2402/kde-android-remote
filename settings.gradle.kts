pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Google's Home APIs SDK is not on a public Maven repository: unzip the downloaded SDK into
// home-sdk/ (any folder depth) and the Home tab is built in. See README.
val homeSdkRepo: File? = file("home-sdk").takeIf { it.isDirectory }
    ?.walkTopDown()
    ?.firstOrNull { it.isDirectory && it.invariantSeparatorsPath.endsWith("com/google/android/gms/play-services-home") }
    ?.parentFile?.parentFile?.parentFile?.parentFile?.parentFile

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (homeSdkRepo != null) maven { url = homeSdkRepo.toURI() }
    }
}

rootProject.name = "KDE Remote"
include(":app")
