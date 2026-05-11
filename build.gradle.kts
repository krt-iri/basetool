plugins {
  id("idea")
  id("org.owasp.dependencycheck") version "12.2.0"
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

