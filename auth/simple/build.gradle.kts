plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":auth:credential"))
    implementation(project(":plugin:api"))

    testImplementation(project(":protocol"))
    testImplementation(platform(libs.reactor.bom))
    testImplementation(libs.reactor.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.test {
    useJUnitPlatform()
}
