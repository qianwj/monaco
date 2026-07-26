import java.util.zip.ZipFile

plugins {
    `java-library`
    `java-test-fixtures`
}

repositories {
    mavenCentral()
}

dependencies {
    testFixturesApi(project(":protocol"))
    testFixturesApi(project(":core"))
    testFixturesImplementation(platform(libs.reactor.bom))
    testFixturesImplementation(libs.reactor.core)
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.reactor.test)

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

abstract class VerifyTestkitIsolation : DefaultTask() {

    @get:Classpath
    abstract val productionClasspath: ConfigurableFileCollection

    @get:InputFile
    abstract val productionJar: RegularFileProperty

    @TaskAction
    fun verify() {
        check(productionClasspath.files.isEmpty()) {
            "testkit production runtimeClasspath must be empty: ${productionClasspath.files}"
        }
        ZipFile(productionJar.get().asFile).use { jar ->
            val productionClasses = jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .map { it.name }
                .toList()
            check(productionClasses.isEmpty()) {
                "testkit production jar must not contain classes: $productionClasses"
            }
        }
    }
}

val verifyTestkitIsolation by tasks.registering(VerifyTestkitIsolation::class) {
    productionClasspath.from(configurations.named("runtimeClasspath"))
    productionJar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    dependsOn(tasks.named("jar"))
}

tasks.named("check") {
    dependsOn(verifyTestkitIsolation)
}
