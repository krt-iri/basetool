import org.cyclonedx.Version

plugins {
  java
  id("jacoco")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.cyclonedx.bom)
}

description = "frontend"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  // Jackson databind for RFC7807 Problem+JSON parsing in BackendServiceException
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
  // Validation for @ConfigurationProperties validation
  implementation("org.springframework.boot:spring-boot-starter-validation")
  // Caching
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("com.github.ben-manes.caffeine:caffeine")
  // Actuator for /actuator/health -- consumed by the Docker HEALTHCHECK
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  // Spring Session with Redis for persistent sessions across restarts
  implementation("org.springframework.session:spring-session-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  // Resilience4j for resilience patterns + Reactor operators
  implementation(libs.resilience4j.spring.boot3)
  implementation(libs.resilience4j.reactor)
  implementation(libs.logstash.logback.encoder)
  
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  compileOnly("org.jetbrains:annotations:_")
  // Optional: metadata for IDE assistance on configuration properties
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("org.springframework.security:spring-security-test")
  // MockWebServer for HTTP simulations in WebClient tests
  testImplementation("com.squareup.okhttp3:mockwebserver:_")
}

// Test, JavaCompile and BootRun task setup is shared with the backend module via
// the `basetool.java-conventions` precompiled script plugin (see buildSrc/).
// The frontend adds JaCoCo report wiring on top.
tasks.withType<Test>().configureEach {
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  reports {
    xml.required.set(true)
    csv.required.set(true)
  }
}

tasks.cyclonedxBom {
  schemaVersion.set(Version.VERSION_16)
  jsonOutput.set(file("docs/${project.name}-bom.json"))
  xmlOutput.set(file("docs/${project.name}-bom.xml"))
  includeBomSerialNumber = true
  includeLicenseText = true
  includeBuildSystem = true
}

