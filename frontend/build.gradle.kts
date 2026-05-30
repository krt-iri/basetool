import com.github.gradle.node.npm.task.NpxTask
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
  // Node toolchain for the web-asset linters (ESLint / Stylelint / HTMLHint). The
  // plugin downloads its own Node + npm under `.gradle/nodejs` (download = true
  // below), so neither the developer machine nor the CI runner needs a
  // pre-installed Node — consistent with the "only the Gradle wrapper" rule.
  id("com.github.node-gradle.node") version "7.1.0"
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
// Web-asset linting: ESLint (JS), Stylelint (CSS), HTMLHint (Thymeleaf HTML).
//
// The Gradle Node plugin downloads a private Node + npm under `.gradle/nodejs`
// (download = true) so no host Node install is required — consistent with the
// "only the Gradle wrapper" rule. `npmInstall` reads the committed
// package.json / package-lock.json and is incremental.
//
// The three lint tasks are wired into `check` and run STRICT
// (ignoreExitValue = false): any finding fails the build. Introduction
// followed the staged SpotBugs pattern — report-only until the existing
// backlog was cleared (ESLint 79 -> 0, Stylelint 348 -> 0, HTMLHint 0), then
// flipped to strict. The vendored, minified JS bundles are excluded in the
// tool configs (eslint.config.mjs / .stylelintrc.json).
// ---------------------------------------------------------------------------
node {
  version.set("24.16.0")
  download.set(true)
}

val lintCss =
    tasks.register<NpxTask>("lintCss") {
      group = "verification"
      description = "Lints CSS sources with Stylelint (strict; fails the build on findings)."
      dependsOn(tasks.named("npmInstall"))
      command.set("stylelint")
      args.set(listOf("src/main/resources/static/css/**/*.css"))
      ignoreExitValue.set(false)
      inputs.files(fileTree("src/main/resources/static/css") { include("**/*.css") })
      inputs.file("package.json")
      inputs.file(".stylelintrc.json")
    }

val lintHtml =
    tasks.register<NpxTask>("lintHtml") {
      group = "verification"
      description = "Lints Thymeleaf HTML templates with HTMLHint (strict; fails the build on findings)."
      dependsOn(tasks.named("npmInstall"))
      command.set("htmlhint")
      args.set(listOf("src/main/resources/templates/**/*.html"))
      ignoreExitValue.set(false)
      inputs.files(fileTree("src/main/resources/templates") { include("**/*.html") })
      inputs.file("package.json")
      inputs.file(".htmlhintrc")
    }

val lintJs =
    tasks.register<NpxTask>("lintJs") {
      group = "verification"
      description = "Lints hand-written browser scripts with ESLint (strict; fails the build on findings)."
      dependsOn(tasks.named("npmInstall"))
      command.set("eslint")
      args.set(listOf("src/main/resources/static/js/**/*.js"))
      ignoreExitValue.set(false)
      inputs.files(fileTree("src/main/resources/static/js") { include("**/*.js") })
      inputs.file("package.json")
      inputs.file("eslint.config.mjs")
    }

tasks.named("check").configure { dependsOn(lintCss, lintHtml, lintJs) }

