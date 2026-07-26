plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":protocol"))
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

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions)
        .addBooleanOption("Xdoclint:all,-missing", true)
}
