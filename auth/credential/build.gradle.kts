plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(platform(libs.reactor.bom))
    api(libs.reactor.core)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
