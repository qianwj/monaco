plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core"))
    api(platform(libs.reactor.bom))
    api(libs.reactor.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.reactor.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
