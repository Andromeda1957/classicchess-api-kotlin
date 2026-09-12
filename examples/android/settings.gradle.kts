pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("apiRepository").getOrElse("../../build/repository")) }
        google()
        mavenCentral()
    }
}
rootProject.name = "classicchess-android-consumer"
