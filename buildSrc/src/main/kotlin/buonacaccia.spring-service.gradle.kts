// Convenzioni comuni a tutti i servizi Spring Boot del progetto.
plugins {
    java
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = "it.buonacaccia"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
    implementation(platform(libs.findLibrary("spring-cloud-dependencies").get()))
    testImplementation(platform(libs.findLibrary("spring-boot-dependencies").get()))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
