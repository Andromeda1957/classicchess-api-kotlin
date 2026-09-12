pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
rootProject.name = "classicchess-jvm-example"
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.classicchess:api-client")).using(project(":"))
    }
}
