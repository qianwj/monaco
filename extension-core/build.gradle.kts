import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.internal.declarativedsl.parsing.main
import org.gradle.internal.impldep.bsh.commands.dir
import java.io.File

/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.11.1/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    `java-library`
}

val generatedPath = "generated/sources/java"

sourceSets {
    main {
        java {
            srcDirs(
                layout.buildDirectory.dir(generatedPath),
                "${project.projectDir}/src/main/java"
            )
        }
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {

    implementation(libs.vertx.core)
    implementation(libs.flatbuffers)
    implementation(libs.log4j.api)

    implementation(project(":common"))

    runtimeOnly(libs.log4j.core)

    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.log4j.core)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    testLogging {
        events = setOf(PASSED, SKIPPED, FAILED)
    }
}

tasks.register<Exec>("generateProtocol") {
    commandLine("sh", "../extension-protocol/compile.sh", "--java", "${project.buildDir}/${generatedPath}")
}

tasks.compileJava {
    dependsOn("generateProtocol")
}
