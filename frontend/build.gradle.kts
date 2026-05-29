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

// Resolve the version string that ends up in `META-INF/build-info.properties`
// (consumed by `AppVersionAdvice` to render the sidebar's discreet version
// chip). Priority chain — first non-blank wins:
//
//   1. `-PappVersion=<value>` on the Gradle command line. Used by the CI
//      Docker build, where `.git` is excluded from the build context via
//      `.dockerignore`: the GitHub Actions workflow computes the version on
//      the runner and forwards it as a `--build-arg APP_VERSION=...` which
//      the Dockerfile relays to Gradle via this property.
//   2. `git describe --tags --always --dirty` against the worktree's `.git`.
//      Used by local developer builds, where the host has both `.git` and
//      a `git` binary on PATH. `--tags` matches lightweight tags (the
//      project uses `vX.Y.Z` tags); `--always` falls back to a short SHA
//      when no tag is reachable; `--dirty` appends a marker if the worktree
//      has uncommitted changes so a half-committed build never claims to
//      be the clean tag. Drains stderr to `Redirect.DISCARD` so a chatty
//      git binary cannot block on a full pipe buffer.
//   3. `project.version` (currently `0.0.1-SNAPSHOT`) as the final fallback
//      for Docker builds without an injected `-PappVersion` and for hosts
//      without git installed.
//
// The leading `v` from a canonical tag (e.g. `v0.2.3`) is stripped at the
// end so the sidebar's i18n template `v{0}` does not produce `vv0.2.3`. The
// SNAPSHOT fallback has no `v` prefix and surfaces as `v0.0.1-SNAPSHOT`,
// SHA-only fallback surfaces as `vabc1234` — both consistent with the
// canonical-tag rendering.
val resolvedAppVersion: String by lazy {
  val override = (findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() }
  val gitDescribed: String? =
      runCatching {
            val proc =
                ProcessBuilder("git", "describe", "--tags", "--always", "--dirty")
                    .directory(rootDir)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            val stdout = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0 && stdout.isNotBlank()) stdout else null
          }
          .getOrNull()
  val raw = override ?: gitDescribed ?: project.version.toString()
  raw.removePrefix("v")
}

// Generate `META-INF/build-info.properties` at build time so Spring Boot's
// `ProjectInfoAutoConfiguration` auto-wires a `BuildProperties` bean. The bean
// feeds the sidebar's discreet version label (rendered by `AppVersionAdvice`)
// without forcing every Thymeleaf template to read a Gradle-substituted token
// directly. `bootBuildInfo` is wired into `processResources`, so the file is on
// the test/runtime classpath without an extra task dependency.
springBoot {
  buildInfo {
    properties {
      // Override the default `project.version` value with the chain resolved
      // above so the deployed image's sidebar reflects the actual git tag of
      // the commit it was built from.
      version.set(resolvedAppVersion)
    }
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
  // Reactor ThreadLocal propagation across WebClient worker threads — required so the active-OrgUnit
  // pin and the correlation id flow from the servlet thread into the WebClient exchange filter.
  // Version is resolved by the Spring Boot BOM (no version.ref here).
  implementation(libs.micrometer.context.propagation)
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

// Keep the e2e (Playwright / Testcontainers) source-set dependencies out of the shipped SBOM:
// they are test-only and never enter the bootJar or the published image. In cyclonedx-gradle 3.x
// the dependency scan runs in the `cyclonedxDirectBom` task (CyclonedxDirectTask), which exposes
// `skipConfigs` (regexes matched against configuration names); the aggregate `cyclonedxBom` task
// only formats the output. Skipping every `e2e*` configuration there stops Playwright from
// polluting `frontend-bom.*`.
tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
  skipConfigs.set(listOf("^e2e.*"))
}

// L-2 from the performance audit: minify CSS files inside the built jar so the
// shipped payload is smaller than the readable sources under
// `src/main/resources/static/css/`. The source files stay untouched (so editing
// and diffing remain pleasant) — `minifyStaticCss` runs after `processResources`
// has copied them into `build/resources/main/static/css/` and overwrites those
// copies in place. Wired as a dependency of `classes` so it always runs before
// the jar is assembled and before `bootRun` serves the resources.
//
// The minifier is deliberately conservative: it strips `/* ... */` block
// comments, drops blank lines, and trims leading/trailing whitespace per line.
// It does NOT touch whitespace around selectors or values, because CSS treats a
// space as the descendant combinator (`a :hover` vs `a:hover`) and an
// over-eager regex would silently change selector semantics. The savings target
// is ~25–30 % on `styles.css` (the only big file), which is what the audit
// estimated.
//
// No new build-time dependency was added — a battle-tested minifier
// (yuicompressor / closure-stylesheets) would compress more aggressively, but
// the LOW-priority finding does not justify a new classpath entry. Revisit if
// the CSS surface grows beyond ~200 KB.
tasks.register("minifyStaticCss") {
  group = "build"
  description = "Strips comments and blank lines from CSS files in the resources output (L-2)."
  dependsOn("processResources")

  val resourcesOutputDir = layout.buildDirectory.dir("resources/main/static/css")
  inputs.files(fileTree("src/main/resources/static/css").matching { include("**/*.css") })
  outputs.dir(resourcesOutputDir)

  doLast {
    val cssDir = resourcesOutputDir.get().asFile
    if (!cssDir.exists()) {
      logger.lifecycle("minifyStaticCss: no CSS directory at ${cssDir}; skipping")
      return@doLast
    }
    val blockComment = Regex("""/\*[\s\S]*?\*/""")
    var totalBefore = 0L
    var totalAfter = 0L
    cssDir.walkTopDown().filter { it.isFile && it.extension == "css" }.forEach { file ->
      val original = file.readText(Charsets.UTF_8)
      val withoutComments = blockComment.replace(original, "")
      val minified =
          withoutComments
              .lineSequence()
              .map { it.trim() }
              .filter { it.isNotEmpty() }
              .joinToString(separator = "\n")
              .plus("\n")
      file.writeText(minified, Charsets.UTF_8)
      totalBefore += original.toByteArray(Charsets.UTF_8).size.toLong()
      totalAfter += minified.toByteArray(Charsets.UTF_8).size.toLong()
    }
    if (totalBefore > 0) {
      val pct = 100.0 * (totalBefore - totalAfter) / totalBefore
      logger.lifecycle(
          "minifyStaticCss: ${totalBefore} -> ${totalAfter} bytes (-${"%.1f".format(pct)}%)")
    }
  }
}
tasks.named("classes").configure { dependsOn("minifyStaticCss") }

// ---------------------------------------------------------------------------
// E2E (Playwright) source set + task — Phase 0 spike (docs/E2E_TESTING_PLAN.md).
//
// Deliberately NOT wired into `check` / `test` / `build`: the suite needs a
// running stack and a downloaded Chromium, so it only runs on an explicit
// `./gradlew :frontend:e2eTest`. The `e2e` source set reuses the test
// dependencies (JUnit 5 + assertions from spring-boot-starter-test) and adds
// the Playwright Java binding on top.
// ---------------------------------------------------------------------------
sourceSets { create("e2e") }

configurations["e2eImplementation"].extendsFrom(configurations["testImplementation"])
configurations["e2eRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
  "e2eImplementation"(libs.playwright)
  // PostgreSQL driver for the JDBC catalog seeding (UEX-owned reference data the admin API can't
  // create); version is managed by the Spring Boot BOM.
  "e2eImplementation"("org.postgresql:postgresql")
  // JUnit Platform launcher so the custom Test task can discover Jupiter tests.
  "e2eRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// Checkstyle auto-creates `checkstyleE2e` and wires it into `check`. E2E code,
// like test code (see the root build's `checkstyleTest` disable), uses
// conventions Google style flags as noise — disable it to keep `check` green.
tasks.matching { it.name == "checkstyleE2e" }.configureEach { enabled = false }

// Installs the Playwright-managed Chromium into the per-user browser cache
// (~/.cache/ms-playwright). Cached in CI; a no-op once present.
val playwrightInstall by tasks.registering(JavaExec::class) {
  group = "verification"
  description = "Installs the Playwright browsers (Chromium, Firefox, WebKit) for e2eTest/smokeTest."
  classpath = sourceSets["e2e"].runtimeClasspath
  mainClass.set("com.microsoft.playwright.CLI")
  args("install", "chromium", "firefox", "webkit")
}

// Shared wiring for the two Playwright Test tasks below. Both run from the `e2e` source set with a
// provisioned Chromium and forward the same `e2e.*` knobs; they differ only in the JUnit tag they
// select (and therefore the target they assume). E2E_BASE_URL is read straight from the inherited
// environment by E2eStackExtension, so it needs no forwarding; -Pe2e.baseUrl switches to
// external/staging mode.
val playwrightSuiteConfig: Test.() -> Unit = {
  group = "verification"
  testClassesDirs = sourceSets["e2e"].output.classesDirs
  classpath = sourceSets["e2e"].runtimeClasspath
  dependsOn(playwrightInstall)
  // Chromium is provisioned by playwrightInstall; stop Playwright.create() from auto-downloading
  // the full browser set (Firefox + WebKit) on first run.
  environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
  // CI passes credentials through the environment (masked in logs) rather than on the command line;
  // map them onto the e2e.* system properties the tests read. An explicit -P value (below) wins.
  // E2E_BASE_URL needs no mapping — E2eStackExtension reads it straight from the environment.
  mapOf("E2E_USERNAME" to "e2e.username", "E2E_PASSWORD" to "e2e.password").forEach { (env, prop) ->
    System.getenv(env)?.takeIf { it.isNotBlank() }?.let { systemProperty(prop, it) }
  }
  listOf("e2e.baseUrl", "e2e.browser", "e2e.username", "e2e.password", "e2e.hostResolverRules")
      .forEach { key -> (findProperty(key) as String?)?.let { systemProperty(key, it) } }
}

// Full functional flows incl. destructive CRUD; assumes an isolated stack (ephemeral by default).
tasks.register<Test>("e2eTest") {
  description = "Runs the destructive Playwright e2e flows against an isolated stack (JUnit tag: e2e)."
  playwrightSuiteConfig()
  useJUnitPlatform { includeTags("e2e") }
}

// Non-destructive login + core-page checks; target-agnostic, safe to run against staging.
tasks.register<Test>("smokeTest") {
  description =
    "Runs the non-destructive Playwright smoke checks (JUnit tag: smoke); set E2E_BASE_URL for staging."
  playwrightSuiteConfig()
  useJUnitPlatform { includeTags("smoke") }
}

