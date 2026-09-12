plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    `java-library`
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "com.classicchess"
version = "0.1.0"
repositories { mavenCentral() }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test-junit"))
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
}
dependencyLocking { lockAllConfigurations() }

val checkContract by tasks.registering(Exec::class) {
    commandLine("python3", "scripts/generate_models.py", "--check")
}
tasks.named("check") { dependsOn(checkContract) }
tasks.test {
    maxParallelForks = 1
    val fixtures = file("contracts/client-fixtures.json").takeIf { it.isFile }
        ?: file("../../docs/api/client-fixtures.json")
    inputs.file(fixtures)
    systemProperty("classicchess.fixtures", fixtures.absolutePath)
}

val documentationJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}
val releaseMetadata = file("contracts/sdk-release.json").takeIf { it.isFile }
    ?: file("../../docs/api/sdk-release.json")
val sdkRelease = groovy.json.JsonSlurper().parse(releaseMetadata) as Map<*, *>

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(documentationJar)
            pom {
                name.set("Classic Chess Kotlin API client")
                description.set("Typed public archive client for Kotlin JVM and Android")
                url.set("https://classicchess.com/api/")
                developers {
                    developer {
                        id.set("Andromeda1957")
                        name.set("Christian Walls")
                        url.set("https://classicchess.com/")
                    }
                }
                scm {
                    url.set("https://github.com/Andromeda1957/classicchess-api-kotlin")
                    connection.set("scm:git:https://github.com/Andromeda1957/classicchess-api-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Andromeda1957/classicchess-api-kotlin.git")
                }
                if (sdkRelease["license"] != null) {
                    licenses {
                        license {
                            name.set(sdkRelease["license"].toString())
                            url.set(sdkRelease["license_url"].toString())
                            distribution.set("repo")
                        }
                    }
                }
            }
        }
    }
    repositories {
        maven { name = "LocalPackage"; url = uri(layout.buildDirectory.dir("repository")) }
    }
}
signing {
    val key = providers.environmentVariable("SDK_SIGNING_KEY").orNull
    val password = providers.environmentVariable("SDK_SIGNING_PASSWORD").orNull
    if (key != null) {
        useInMemoryPgpKeys(key, password)
        sign(publishing.publications["maven"])
    }
}
tasks.register<Zip>("packageClient") {
    dependsOn("check", "publishMavenPublicationToLocalPackageRepository")
    archiveFileName.set("classicchess-kotlin-api-${project.version}.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts"))
    from(layout.buildDirectory.dir("repository")) { into("repository") }
    from("README.md")
    from("LICENSE")
}
tasks.register<JavaExec>("verifyPreview") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.classicchess.api.PreviewCheckKt")
    args(providers.gradleProperty("apiBaseUrl").getOrElse("http://127.0.0.1:8000"))
}
