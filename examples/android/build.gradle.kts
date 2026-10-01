plugins {
    id("com.android.library") version "9.4.1"
    // AGP 9 compiles Kotlin itself; this only pins the Kotlin Gradle plugin version it uses.
    kotlin("android") version "2.4.20" apply false
}
android {
    namespace = "com.classicchess.example"
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.classicchess:api-client:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
