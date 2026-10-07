import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plain Kotlin/JVM: no Android dependencies, so it can be unit-tested directly.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// LiveTest asks transport.opendata.ch, so it runs only on request: ./gradlew :core:test -Plive
tasks.test {
    if (providers.gradleProperty("live").isPresent) {
        testLogging.showStandardStreams = true
        outputs.upToDateWhen { false }
    } else {
        exclude("**/LiveTest*")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit)
}

// The timetable file for the full search from the Swiss GTFS (CLAUDE.md, The full search):
// ./gradlew :core:timetable -Pgtfs=<zip>[,<zip>…] -Pout=<file> [-Pfrom=yyyy-MM-dd, else today], paths from the repo's root
tasks.register<JavaExec>("timetable") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.buerlino.gleiswechsel.core.GtfsKt"
    workingDir = rootDir
    args(listOf("gtfs", "out", "from").mapNotNull { providers.gradleProperty(it).orNull })
}
