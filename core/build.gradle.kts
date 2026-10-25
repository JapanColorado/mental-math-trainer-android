// Pure-Kotlin core: content generators, spaced repetition, session engine. No Android
// dependencies, so its tests run on the plain JVM (SQLite via sqlite-jdbc).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}

tasks.test {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
