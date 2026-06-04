plugins {
  id("idea")
  id("org.owasp.dependencycheck") version "12.2.2"
  // Pulled in with `apply false` so the PitestPluginExtension type is on the
  // root build script's classpath for the `subprojects { plugins.withId(...) }`
  // configuration block below. Each subproject still applies the plugin itself.
  id("info.solidsoft.pitest") version "1.19.0" apply false
  // Same `apply false` pattern as pitest: declared here so SpotlessExtension is
  // on the root build script's classpath for the strongly-typed configuration
  // below. Each subproject applies the plugin itself.
  id("com.diffplug.spotless") version "8.6.0" apply false
}

allprojects {
  group = "de.greluc.krt.iri.basetool"
  version = "0.0.1-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}

// Shared Java conventions for the backend and frontend modules. Both subprojects
// apply the Spring Boot plugin and need an identical Test/BootRun/JavaCompile
// setup — centralising it here removes the previous duplication while still
// letting each module add its own specifics (frontend wires
// `finalizedBy(tasks.jacocoTestReport)` separately, for example).
subprojects {
  plugins.withId("java") {
    tasks.withType<Test>().configureEach {
      useJUnitPlatform()
      jvmArgs("--enable-native-access=ALL-UNNAMED")
      // Bump test heap: every @SpringBootTest spins its own ApplicationContext, and the full
      // backend suite (over 1000 tests, many context-loading) blows past the JVM 512 MiB default
      // with a Java heap-space OOM at ~250 tests in. 1.5 GiB keeps the suite green locally and
      // on CI without affecting the per-test memory budget materially.
      maxHeapSize = "1536m"
      systemProperty("spring.profiles.active", "test")
      // Mockito 5+ on JDK 24+ insists on running as a Java agent. Attach it via
      // -javaagent so the inline mock-maker can self-attach; -Xshare:off avoids
      // the AppCDS warning under the Spring Boot DevTools class loader.
      val mockitoCore = classpath.find { it.name.contains("mockito-core") }
      if (mockitoCore != null) {
        jvmArgs("-Xshare:off", "-javaagent:${mockitoCore.absolutePath}")
      }
      // Deliberately NOT setting `maxParallelForks > 1` here even though the
      // M-1 audit recommended it. A trial run with (cores / 2) destabilised
      // `WebClientResilienceTest.timeLimiter_ShouldTimeoutSlowResponses` — the
      // test asserts that a slow upstream times out within ~2 s, and under
      // forked CPU contention the JVM scheduler delay alone ate the budget.
      // Cross-module parallel (`org.gradle.parallel=true` in gradle.properties)
      // already runs backend and frontend test tasks concurrently, which is
      // most of the win for a 2-module build; revisit per-module forking once
      // the timing-sensitive resilience tests get refactored onto virtual time
      // (e.g. StepVerifier.withVirtualTime) so they no longer race against
      // wall-clock GC pauses.
    }

    tasks.withType<JavaCompile>().configureEach {
      options.encoding = "UTF-8"
      options.compilerArgs.addAll(
        listOf(
          "-parameters",
          "-Xlint:unchecked",
          "-Xlint:deprecation",
        )
      )
    }
  }

  // BootRun comes from the Spring Boot plugin. We avoid a hard reference to the
  // BootRun class (which would require this script to have the Spring Boot
  // plugin on its classpath) by configuring through `JavaExec` — BootRun is a
  // JavaExec subclass, so jvmArgs/systemProperty are available.
  plugins.withId("org.springframework.boot") {
    tasks.named<JavaExec>("bootRun") {
      jvmArgs("--enable-native-access=ALL-UNNAMED")
      systemProperty("spring.profiles.active", "dev")
    }

    // BOM-property overrides for transitive dependencies whose Spring Boot 4.0.6
    // pin is now vulnerable. The Spring Boot dependency-management plugin reads
    // these `ext` properties and substitutes them into the managed BOM so every
    // module (backend + frontend) inherits the patched version without having
    // to redeclare individual `implementation(...)` coordinates. Drop these
    // overrides as soon as the Spring Boot patch release that adopts the same
    // (or newer) versions lands and refreshVersions confirms parity.
    //
    //   tomcat 11.0.21    -> 11.0.22       (CVE-2026-41284, -41293, -42498,
    //                                       -43512, -43513, -43515)
    //   netty 4.2.12      -> 4.2.14.Final  (CVE-2026-42577, -42579, -42581,
    //                                       -42582, -42583, -42584, -42585,
    //                                       -42586, -42587, -44248; .14 is the
    //                                       latest 4.2.x patch — 5.0.0 is Alpha)
    //   postgresql 42.7.10 -> 42.7.11      (CVE-2026-42198 - SCRAM-SHA-256
    //                                       client-side DoS in pgjdbc)
    extra["tomcat.version"] = "11.0.22"
    extra["netty.version"] = "4.2.14.Final"
    extra["postgresql.version"] = "42.7.11"
  }

  // JaCoCo coverage. Both modules want the same setup: emit XML + CSV + HTML
  // reports after every test run so CI / SonarQube / IDEs can consume the
  // data without re-running tests. Each subproject that opts in via
  // `plugins { jacoco }` automatically picks up this configuration.
  plugins.withId("jacoco") {
    tasks.withType<Test>().configureEach {
      finalizedBy(tasks.named("jacocoTestReport"))
    }

    // Generated / untestable code excluded from BOTH the coverage report and
    // the coverage gate so the ratio reflects hand-written logic only:
    //  - MapStruct *MapperImpl: its `@Generated` is
    //    `javax.annotation.processing.Generated` (SOURCE retention), invisible
    //    to JaCoCo's bytecode-time annotation filter, so it must be removed by
    //    class pattern. The `*Mapper` interface is NOT excluded — it carries
    //    default methods / comparators / `computeProfit(...)` that we test.
    //  - The one-line Spring Boot `*Application` `SpringApplication.run(...)`
    //    stub, which has no meaningful unit test.
    // (Lombok-generated members are auto-excluded because lombok.config sets
    // `lombok.addLombokGeneratedAnnotation = true`, and JaCoCo 0.8.2+ skips any
    // CLASS/RUNTIME `@Generated`.)
    val generatedClassExcludes =
      listOf("**/*MapperImpl.class", "**/*MapperImpl\$*.class", "**/*Application.class")
    fun filterGenerated(classes: FileCollection): FileCollection =
      files(classes.files.map { dir -> fileTree(dir) { exclude(generatedClassExcludes) } })

    tasks.named<JacocoReport>("jacocoTestReport") {
      // L-1: only generate the JaCoCo report when actually running in CI.
      // Locally a developer running `./gradlew :backend:test` from the IDE pays
      // the JaCoCo instrumentation overhead twice (test-runtime + the report
      // task) for output nobody looks at — Codecov / SonarQube only consume it
      // from CI runs. The `CI` env var is set by GitHub Actions by default,
      // GitLab CI, CircleCI, Drone and every other major runner; setting it
      // locally via `CI=true ./gradlew test` opts in explicitly when needed.
      onlyIf { System.getenv("CI") != null }
      reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
      }
      classDirectories.setFrom(filterGenerated(classDirectories))
    }

    // Coverage ratchet, wired into `check`. A module-specific floor (set a few
    // points below the measured baseline — backend ~87% instr / ~71% branch,
    // frontend ~65% / ~52%) fails the build on a real coverage regression
    // without flapping on small fluctuations. Tighten these minimums upward
    // over time. Uses the same generated-code excludes as the report so the
    // denominator matches; runs on the test exec data that `check` already
    // produces (so no extra test run). Unlike the report it is NOT gated on
    // `CI`, so a regression fails fast on a local `./gradlew check` too.
    val instructionFloor = mapOf("backend" to "0.82", "frontend" to "0.60")[project.name] ?: "0.50"
    val branchFloor = mapOf("backend" to "0.65", "frontend" to "0.46")[project.name] ?: "0.40"
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
      dependsOn(tasks.named("test"))
      classDirectories.setFrom(filterGenerated(classDirectories))
      violationRules {
        rule {
          element = "BUNDLE"
          limit {
            counter = "INSTRUCTION"
            value = "COVEREDRATIO"
            minimum = instructionFloor.toBigDecimal()
          }
          limit {
            counter = "BRANCH"
            value = "COVEREDRATIO"
            minimum = branchFloor.toBigDecimal()
          }
        }
      }
    }
    tasks.named("check").configure { dependsOn(tasks.named("jacocoTestCoverageVerification")) }
  }

  // Pitest mutation testing (info.solidsoft.pitest). Runs on demand via
  // `./gradlew :<module>:pitest` — intentionally NOT wired into `check`
  // because a full mutation run takes O(minutes) per service and is far too
  // slow for the standard PR-build path. Scope defaults to the business-logic
  // service layer (`...basetool.<module>.service.*`); DTOs, mappers,
  // repositories and config classes are excluded because mutating them rarely
  // catches real test-suite gaps. Reports land under
  // `<module>/build/reports/pitest/index.html`.
  //
  // Tuning notes:
  // - `threads = 4` keeps a single-developer laptop responsive while still
  //   parallelising mutation runs across test classes.
  // - JVM args mirror the regular Test task (`--enable-native-access` plus
  //   `-Xshare:off -javaagent:<mockito-core>` so Mockito 5+ self-attaches in
  //   PIT's isolated minion processes; without that the green-suite
  //   prerequisite fails before any mutation is generated).
  // - The first invocation will almost certainly fail because some service
  //   tests rely on a Spring context that PIT's minion process does not
  //   start; the team should add the affected test classes to
  //   `excludedTestClasses` (or move them to `*IntegrationTest` naming) as a
  //   follow-up, then tighten `mutationThreshold` / `coverageThreshold` to
  //   make the gate strict.
  plugins.withId("info.solidsoft.pitest") {
    extensions.configure<info.solidsoft.gradle.pitest.PitestPluginExtension>("pitest") {
      junit5PluginVersion.set("1.2.3")
      targetClasses.set(listOf("de.greluc.krt.iri.basetool.${project.name}.service.*"))
      targetTests.set(listOf("de.greluc.krt.iri.basetool.${project.name}.service.*Test"))
      threads.set(4)
      outputFormats.set(listOf("HTML", "XML"))
      timestampedReports.set(false)

      // Build the same JVM-arg list the regular Test task uses, so Mockito 5+
      // can self-attach as a Java agent inside PIT's isolated minions and the
      // native-access warning is silenced. The mockito-core path is resolved
      // lazily from the test runtime classpath.
      val testClasspath = configurations.getByName("testRuntimeClasspath")
      jvmArgs.set(provider {
        val args = mutableListOf("--enable-native-access=ALL-UNNAMED")
        val mockitoCore = testClasspath.files.find { it.name.contains("mockito-core") }
        if (mockitoCore != null) {
          args += "-Xshare:off"
          args += "-javaagent:${mockitoCore.absolutePath}"
        }
        args
      })
    }
  }

  // Checkstyle (Gradle core plugin). Uses the Google Java Style config
  // (`config/checkstyle/google_checks.xml`, downloaded from the Checkstyle
  // 13.5.0 release tag) which enforces 2-space indents, 100-char lines,
  // Google-style imports, naming conventions, Javadoc on public API, etc.
  //
  // Phase 4 (this configuration): the gate is now STRICT.
  // `ignoreFailures = false` + `maxWarnings = 0` mean any new Checkstyle
  // warning or error fails `./gradlew check` — regressions are caught at
  // CI / PR time instead of accumulating silently. Reports land under
  // `<subproject>/build/reports/checkstyle/{main,test}.html`. The test
  // source set scan is disabled — test code intentionally uses different
  // conventions (long method names with underscores, longer lines for
  // BDD-style assertions) that Google's style flags as noise.
  plugins.withId("checkstyle") {
    extensions.configure<CheckstyleExtension>("checkstyle") {
      toolVersion = "13.5.0"
      configFile = rootProject.file("config/checkstyle/google_checks.xml")
      isIgnoreFailures = false
      maxWarnings = 0
    }
    tasks.matching { it.name == "checkstyleTest" }.configureEach { enabled = false }
  }

  // Spotless (com.diffplug.spotless). Auto-formats Java sources with
  // google-java-format — same 2-space indent / 100-char width / Google import
  // order that `config/checkstyle/google_checks.xml` enforces. Applying
  // Spotless therefore resolves the bulk of Checkstyle's Indentation,
  // LineLength, CustomImportOrder, AvoidStarImport, EmptyLineSeparator,
  // OperatorWrap and WhitespaceAround warnings in a single pass.
  //
  // Phase 4 (this configuration): `enforceCheck = true` wires `spotlessCheck`
  // into `./gradlew check` so any unformatted file fails the build. Run
  // `./gradlew spotlessApply` locally before pushing to auto-fix; CI then
  // re-runs `spotlessCheck` to verify the diff is clean.
  plugins.withId("com.diffplug.spotless") {
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
      isEnforceCheck = true
      java {
        // google-java-format is pinned to a version that supports JDK 25.
        // Older google-java-format (the default bundled by earlier Spotless
        // releases) reflects against
        // `com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()` —
        // the return type of that method changed in JDK 25 (Queue -> Deque) and the
        // reflection lookup explodes with `NoSuchMethodError`. CI runs JDK 25 Temurin
        // (see `.github/workflows/ci.yml`), so without an explicit pin the spotless
        // task fails 767 files with `google-java-format(java.lang.NoSuchMethodError)`.
        // 1.35.0 targets the new JDK 25 javac signature.
        googleJavaFormat("1.35.0").reflowLongStrings()
        removeUnusedImports()
        // GPLv3 file header, enforced on every Java source (main + test + e2e).
        // The project is GPL-3.0-only (LICENSE.md is the GPLv3 text); the SPDX
        // tag makes that machine-readable. Fixed year (no `$YEAR` token) so
        // `spotlessCheck` never churns the header across calendar years. Run
        // `./gradlew spotlessApply` to (re)stamp.
        licenseHeader(
            """
            /*
             * Profit Basetool - squadron-management web app.
             * Copyright (C) 2026 Lucas Greuloch
             *
             * SPDX-License-Identifier: GPL-3.0-only
             *
             * This program is free software: you can redistribute it and/or modify
             * it under the terms of the GNU General Public License as published by
             * the Free Software Foundation, version 3.
             *
             * This program is distributed in the hope that it will be useful,
             * but WITHOUT ANY WARRANTY; without even the implied warranty of
             * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
             * GNU General Public License for more details.
             *
             * You should have received a copy of the GNU General Public License
             * along with this program.  If not, see <https://www.gnu.org/licenses/>.
             */

            """
                .trimIndent() + "\n")
      }
    }
  }
}

// OWASP Dependency-Check (org.owasp.dependencycheck) 12.2.2. Aggregates over
// all subprojects via `./gradlew dependencyCheckAggregate`. CVSS gate now fails
// the build on findings with CVSS 7.0 or higher (audit finding L-8: previously
// the gate was wide open at CVSS 11 — triage-only). 7.0 covers the OWASP "HIGH"
// severity band; CRITICAL (>= 9.0) is included by definition. The plugin's
// first invocation downloads the NVD feed (~500 MB cached under
// `~/.gradle/dependency-check-data`) and takes 5-15 minutes; subsequent runs
// are seconds. Set `-PnvdApiKey=<key>` (CI: `NVD_API_KEY` repo secret) to
// bypass the public NVD rate limit — register a free key at
// https://nvd.nist.gov/developers/request-an-api-key.
dependencyCheck {
  failBuildOnCVSS = 7.0f
  formats = listOf("HTML", "SARIF")
  outputDirectory.set(layout.buildDirectory.dir("reports/dependency-check"))
  // Suppression file for findings that are either CPE confusion (the NVD
  // matcher attributes a CVE to a different product that shares a token with
  // ours) or sit on a build-time-only classpath that never reaches the
  // deployed Spring Boot fat JAR. Each entry carries a `<notes>` block
  // explaining the reason - real production CVEs are NEVER suppressed, they
  // are fixed by upgrading the dependency.
  suppressionFile = rootProject.file("config/owasp/dependency-check-suppressions.xml").absolutePath
  // Treat the on-disk NVD copy as valid for a full ISO-week. The
  // `.github/workflows/dependency-check.yml` actions/cache entry is keyed on
  // the ISO week as well and the weekly cron re-warms it, so within a week we
  // reuse the local DB and skip every NVD API call. Only the first run of
  // each week actually contacts the NVD endpoint, which is what keeps the
  // workflow inside the 5-req/30-s public rate limit when `NVD_API_KEY` is
  // unset. The plugin default of 4 hours forced an update on essentially
  // every invocation, which on github-hosted runners (shared IP pool with
  // every other CI job on github.com refreshing on Monday morning) led to
  // 429s mid-transaction and a corrupt H2 DB — see run 25933803540.
  nvd.validForHours = 168
  // Bump retries above the default of 10 so a transient 429 burst does not
  // abort the in-progress H2 update; the corruption-on-abort failure mode
  // referenced above only triggers once retries are exhausted while writes
  // are pending.
  nvd.maxRetryCount = 20
  val resolvedNvdApiKey = (project.findProperty("nvdApiKey") as String?)?.takeIf { it.isNotBlank() }
  if (resolvedNvdApiKey != null) {
    nvd.apiKey = resolvedNvdApiKey
    // ~30 req/min, comfortably inside NVD's authenticated 50/30-s budget.
    nvd.delay = 2000
  } else {
    // Public NVD limit is 5 req/30 s; 16 s between calls keeps us at
    // ~3.75 req/30 s with headroom for runner-pool contention on the same
    // source IP. Register and configure an API key (see header comment) to
    // drop this delay and make the first-of-week run finish in minutes
    // instead of tens of minutes.
    nvd.delay = 16000
  }
}

