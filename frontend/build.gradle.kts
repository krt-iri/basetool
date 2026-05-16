import org.cyclonedx.Version

plugins {
  java
  checkstyle
  id("jacoco")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.cyclonedx.bom)
  id("com.github.spotbugs-base") version "6.5.4"
  id("info.solidsoft.pitest") version "1.19.0"
  id("com.diffplug.spotless")
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
  // Jackson JSR-310 module — required by ThymeleafJavaScriptSerializerConfig so
  // [[${dto}]] inline expressions can render objects carrying java.time.* fields
  // (Instant/OffsetDateTime/LocalDateTime). Thymeleaf 3.1.x ships its own Jackson
  // ObjectMapper without modules, and Spring Boot 4 has moved its primary mapper to
  // Jackson 3 (tools.jackson.core), so neither path brings JSR-310 transitively for
  // the Jackson 2 (com.fasterxml) instance Thymeleaf still uses internally.
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  // WebSocket for the mission-detail presence/awareness feature: shows in real time which
  // section of a mission another user is currently editing. Native Spring WebSocket
  // (no STOMP) — minimal wire format, no broker required. The in-memory presence store
  // is single-instance only; running multiple frontend replicas would need a Redis-backed
  // fan-out (see MissionPresenceService javadoc).
  implementation("org.springframework.boot:spring-boot-starter-websocket")
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
  compileOnly(libs.jetbrains.annotations)
  // Optional: metadata for IDE assistance on configuration properties
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("org.springframework.security:spring-security-test")
  // MockWebServer for HTTP simulations in WebClient tests
  testImplementation(libs.okhttp3.mockwebserver)
  // ArchUnit core (no archunit-junit5 — that pulls in a clashing JUnit Platform
  // version; we invoke `.check(CLASSES)` from plain @Test methods). Enforces the
  // frontend's "no JpaRepository / no direct JDBC" rule.
  testImplementation(libs.archunit.core)
}

// Test, JavaCompile, BootRun and JaCoCo setup is shared with the backend module
// via the root build.gradle.kts `subprojects { plugins.withId(...) }` blocks.

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

tasks.cyclonedxBom {
  schemaVersion.set(Version.VERSION_16)
  jsonOutput.set(file("docs/${project.name}-bom.json"))
  xmlOutput.set(file("docs/${project.name}-bom.xml"))
  includeBomSerialNumber = true
  includeLicenseText = true
  includeBuildSystem = true
}

