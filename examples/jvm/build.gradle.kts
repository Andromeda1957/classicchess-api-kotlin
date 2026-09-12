plugins {
    kotlin("jvm") version "2.1.20"
    application
}
repositories {
    maven { url = uri(providers.gradleProperty("apiRepository").getOrElse("../../build/repository")) }
    mavenCentral()
}
dependencies { implementation("com.classicchess:api-client:0.1.0") }
application { mainClass.set("com.classicchess.example.MainKt") }
tasks.named<JavaExec>("run") { standardInput = System.`in` }
