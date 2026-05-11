import org.cyclonedx.Version


plugins {
  java
  id("application")
  id("idea")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.cyclonedx.bom)
  alias(libs.plugins.asciidoctor.convert)
}

group = "de.greluc.krt.iri.basetool"
version = "0.0.1-SNAPSHOT"
description = "backend"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

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
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:_")
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
  compileOnly("org.jetbrains:annotations:_")
  // PDF generation
  implementation("com.github.librepdf:openpdf:_")
  // Ensure MapStruct understands Lombok-generated accessors
  annotationProcessor("org.projectlombok:lombok-mapstruct-binding:_")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testRuntimeOnly("com.h2database:h2")
}

java {
  toolchain {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
  }
}

idea {
  module {
    inheritOutputDirs = true
    isDownloadJavadoc = true
    isDownloadSources = true
  }
}

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}


extra["snippetsDir"] = file("build/generated-snippets")

tasks.withType<Test> {
  useJUnitPlatform()
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("spring.profiles.active", "test")
  val mockitoCore = classpath.find { it.name.contains("mockito-core") }
  if (mockitoCore != null) {
    jvmArgs("-Xshare:off", "-javaagent:${mockitoCore.absolutePath}")
  }
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("spring.profiles.active", "dev")
}



tasks {
  withType(JavaCompile::class.java) {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
    options.compilerArgs.add("-Xlint:deprecation")
  }

  build {
  }

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

