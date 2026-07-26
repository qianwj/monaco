plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":transport-reactor"))
    implementation(project(":store:memory"))
    implementation(project(":security-default"))
    implementation(project(":plugin:runtime"))
    implementation(project(":observability-micrometer"))

    implementation(platform(libs.reactor.bom))
    implementation(libs.reactor.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.reactor.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "cn.elvis.monaco.broker.MonacoApplication"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
