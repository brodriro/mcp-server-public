import org.gradle.internal.classpath.Instrumented.systemProperty
import java.io.FileInputStream
import java.util.Properties

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    id("com.github.johnrengelman.shadow") version "8.1.1"
    alias(libs.plugins.kotlin.serialization)
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")


    // This dependency is used by the application.
    implementation(libs.guava)
    implementation(libs.mcp.server.kt)
    implementation(libs.ktor.negotiation)
    implementation(libs.ktor.serialization)
    implementation(libs.slf4j)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(libs.resend)
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

application {
    // Define the main class for the application.
    mainClass = "im.brodriro.AppKt"
}


tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}