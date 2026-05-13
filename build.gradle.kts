plugins {
  id("idea")
  id("org.owasp.dependencycheck") version "12.2.0"
  // Pulled in with `apply false` so the PitestPluginExtension type is on the
  // root build script's classpath for the `subprojects { plugins.withId(...) }`
  // configuration block below. Each subproject still applies the plugin itself.
  id("info.solidsoft.pitest") version "1.19.0" apply false
  // Same `apply false` pattern as pitest: declared here so SpotlessExtension is
  // on the root build script's classpath for the strongly-typed configuration
  // below. Each subproject applies the plugin itself.
  id("com.diffplug.spotless") version "7.0.4" apply false
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
// setup — centralising it here removes the previous duplication (see
// ANALYSIS.md 3.3) while still letting each module add its own specifics
// (frontend wires `finalizedBy(tasks.jacocoTestReport)` separately, for example).
subprojects {
  plugins.withId("java") {
    tasks.withType<Test>().configureEach {
      useJUnitPlatform()
      jvmArgs("--enable-native-access=ALL-UNNAMED")
      systemProperty("spring.profiles.active", "test")
      // Mockito 5+ on JDK 24+ insists on running as a Java agent. Attach it via
      // -javaagent so the inline mock-maker can self-attach; -Xshare:off avoids
      // the AppCDS warning under the Spring Boot DevTools class loader.
      val mockitoCore = classpath.find { it.name.contains("mockito-core") }
      if (mockitoCore != null) {
        jvmArgs("-Xshare:off", "-javaagent:${mockitoCore.absolutePath}")
      }
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
  }

  // JaCoCo coverage. Both modules want the same setup: emit XML + CSV + HTML
  // reports after every test run so CI / SonarQube / IDEs can consume the
  // data without re-running tests. Each subproject that opts in via
  // `plugins { jacoco }` automatically picks up this configuration.
  plugins.withId("jacoco") {
    tasks.withType<Test>().configureEach {
      finalizedBy(tasks.named("jacocoTestReport"))
    }
    tasks.named<JacocoReport>("jacocoTestReport") {
      reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
      }
      // Strip generated code from the JaCoCo report — coverage on Lombok /
      // MapStruct / Spring-Boot-Application boilerplate is noise, not signal.
      //
      // Lombok-generated methods are auto-excluded by JaCoCo because
      // `lombok.config` at the project root sets
      // `lombok.addLombokGeneratedAnnotation = true` (JaCoCo 0.8.2+ honours
      // any annotation named "Generated" with CLASS/RUNTIME retention).
      //
      // MapStruct's `@Generated` is `javax.annotation.processing.Generated`
      // which is SOURCE-retention — invisible to JaCoCo at bytecode time —
      // so the generated *MapperImpl classes have to be removed by class-
      // pattern excludes. The interface (`*Mapper`) is NOT excluded: it
      // carries default methods, comparators, `computeProfit(...)` etc. that
      // we explicitly test.
      //
      // Also exclude the auto-generated Spring Boot `*Application` main
      // class (one-line `SpringApplication.run(...)` stubs) so coverage is
      // not artificially lowered by code we have no meaningful way to
      // unit-test.
      classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
          fileTree(dir) {
            exclude(
              "**/*MapperImpl.class",
              "**/*MapperImpl\$*.class",
              "**/*Application.class"
            )
          }
        })
      )
    }
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
  // 13.4.2 release tag) which enforces 2-space indents, 100-char lines,
  // Google-style imports, naming conventions, Javadoc on public API, etc.
  // Initial introduction is non-blocking (`ignoreFailures = true`,
  // `maxWarnings = Int.MAX_VALUE`) so the existing codebase doesn't gate
  // the build before the team has a chance to triage. Reports land under
  // `<subproject>/build/reports/checkstyle/{main,test}.html`. The test
  // source set scan is disabled — test code intentionally uses different
  // conventions (long method names with underscores, longer lines for
  // BDD-style assertions) that Google's style flags as noise.
  plugins.withId("checkstyle") {
    extensions.configure<CheckstyleExtension>("checkstyle") {
      toolVersion = "13.4.2"
      configFile = rootProject.file("config/checkstyle/google_checks.xml")
      isIgnoreFailures = true
      maxWarnings = Int.MAX_VALUE
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
  // `enforceCheck = false` keeps `./gradlew check` green while the codebase is
  // still in its pre-reformat state. Run `./gradlew spotlessApply` once to
  // bulk-format, commit that as an isolated change, then flip `enforceCheck`
  // back to its default `true` so future style drift is caught by CI.
  plugins.withId("com.diffplug.spotless") {
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
      isEnforceCheck = false
      java {
        googleJavaFormat().reflowLongStrings()
        removeUnusedImports()
      }
    }
  }
}

// OWASP Dependency-Check (org.owasp.dependencycheck) 12.2.0. Aggregates over
// all subprojects via `./gradlew dependencyCheckAggregate`. CVSS gate stays
// wide open (`failBuildOnCVSS = 11`) for the first iteration so the team
// triages findings before the gate turns strict. The plugin's first invocation
// downloads the NVD feed (~500 MB cached under
// `~/.gradle/dependency-check-data`) and takes 5-15 minutes; subsequent runs
// are seconds. Set `-PnvdApiKey=<key>` to avoid public NVD rate limits.
dependencyCheck {
  failBuildOnCVSS = 11.0f
  formats = listOf("HTML", "SARIF")
  outputDirectory.set(layout.buildDirectory.dir("reports/dependency-check"))
  if (project.findProperty("nvdApiKey") != null) {
    nvd.apiKey = project.property("nvdApiKey") as String
  }
}

