plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":plugin:api"))
    implementation(platform(libs.reactor.bom))
    implementation(libs.reactor.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.reactor) {
        exclude(group = "io.github.resilience4j", module = "resilience4j-bulkhead")
        exclude(group = "io.github.resilience4j", module = "resilience4j-micrometer")
        exclude(group = "io.github.resilience4j", module = "resilience4j-ratelimiter")
        exclude(group = "io.github.resilience4j", module = "resilience4j-retry")
        exclude(group = "io.github.resilience4j", module = "resilience4j-timelimiter")
    }

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

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions)
        .addBooleanOption("Xdoclint:all,-missing", true)
}
