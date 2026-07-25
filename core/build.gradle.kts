plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":protocol"))
    implementation(platform(libs.reactor.bom))
    implementation(libs.reactor.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(platform(libs.reactor.bom))
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
