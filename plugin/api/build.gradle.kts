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
