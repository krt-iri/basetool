import org.cyclonedx.Version


plugins {
  java
  checkstyle
  id("jacoco")
  id("application")
  id("idea")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.cyclonedx.bom)
  alias(libs.plugins.asciidoctor.convert)
  id("com.github.spotbugs-base") version "6.5.4"
  id("info.solidsoft.pitest") version "1.19.0"
  id("com.diffplug.spotless")
}

group = "de.greluc.krt.iri.basetool"
version = "0.0.1-SNAPSHOT"
description = "backend"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
  withSourcesJar()
}

// Lombok + the MapStruct annotation processor expose APIs we use at compile
// time as well. Extending compileOnly from annotationProcessor lets `javac`
// see them without dragging them onto the runtime classpath.
configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("com.github.ben-manes.caffeine:caffeine")
  // springdoc -api (NOT -ui): generates the OpenAPI document at /v3/api-docs without bundling the
  // Swagger UI webjar. The committed openapi.json is the single documentation artifact (produced by
  // OpenApiGeneratorTest); /v3/api-docs is additionally disabled in the prod profile so the spec is
  // never reachable from outside a deployed environment.
  implementation(libs.springdoc.openapi.starter.webmvc.api)
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation(libs.bucket4j.core)
  implementation(libs.semver4j.core)
  // Structured JSON logging (LogstashEncoder) for production profile in logback-spring.xml.
  implementation(libs.logstash.logback.encoder)

  // MapStruct for compile-time mappers
  implementation(libs.mapstruct.core)
  annotationProcessor(libs.mapstruct.processor)

  // Database
  runtimeOnly("org.postgresql:postgresql")
  implementation(libs.flyway.core)
  implementation(libs.flyway.postgresql)

  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  compileOnly(libs.jetbrains.annotations)
  // PDF generation
  implementation(libs.openpdf.core)
  // Ensure MapStruct understands Lombok-generated accessors
  annotationProcessor(libs.lombok.mapstruct.binding)

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  // ArchUnit core (no archunit-junit5: the latter brings its own JUnit Platform
  // version that clashes with Spring Boot 4's. We invoke `.check(CLASSES)` from
  // plain @Test methods, which is enough for our rule set).
  // Pin the architectural rules from CLAUDE.md (Controllers do not return JPA
  // entities, service-layer code does not touch SecurityContextHolder, REST
  // endpoints are authorisation-annotated, ...). See ArchitectureTest.
  testImplementation(libs.archunit.core)
  // MockWebServer for UexClient WebClient testing (already in version catalog
  // and used by the frontend module; backend gets the same shared version).
  testImplementation(libs.okhttp3.mockwebserver)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

idea {
  module {
    inheritOutputDirs = true
    isDownloadJavadoc = true
    isDownloadSources = true
  }
}

extra["snippetsDir"] = file("build/generated-snippets")

// Test, JavaCompile and BootRun task setup is shared with the frontend module via
// the `basetool.java-conventions` precompiled script plugin (see buildSrc/).

// SpotBugs task for the main source set. We use the `-base` variant of the
// plugin which does not auto-create tasks, so we register one explicitly and
// wire it into `check`. Initial introduction is non-blocking
// (`ignoreFailures = true`) — flip to false once the backlog has been triaged.
tasks.register<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
  group = "verification"
  description = "Runs SpotBugs analysis on the main source set."
  sourceDirs.from(sourceSets.main.get().allSource.sourceDirectories)
  classDirs.from(sourceSets.main.get().output.classesDirs)
  auxClassPaths.from(sourceSets.main.get().compileClasspath)
  // Suppress EI_EXPOSE_REP / EI_EXPOSE_REP2 on JPA entities — see the long
  // architectural justification in the filter file. Keeps the bot's
  // recurring per-commit re-flagging out of PR threads.
  excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
  effort.set(com.github.spotbugs.snom.Effort.DEFAULT)
  reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
  ignoreFailures = true
  reports.create("html") {
    required.set(true)
    outputLocation.set(layout.buildDirectory.file("reports/spotbugs/main.html"))
  }
  reports.create("xml") {
    required.set(true)
    outputLocation.set(layout.buildDirectory.file("reports/spotbugs/main.xml"))
  }
  dependsOn("classes")
}
tasks.named("check").configure { dependsOn("spotbugsMain") }

tasks {
  javadoc {
    options {
      (this as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
    }
    destinationDir = project.file("docs/javadoc")
  }

  cyclonedxBom {
    schemaVersion.set(Version.VERSION_16)
    jsonOutput.set(file("docs/${project.name}-bom.json"))
    xmlOutput.set(file("docs/${project.name}-bom.xml"))
    includeBomSerialNumber = true
    includeLicenseText = true
    includeBuildSystem = true
  }

  asciidoctor {
    inputs.dir(project.extra["snippetsDir"]!!)
    dependsOn(test)
  }
}

