plugins {
  java
  id("jacoco")
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
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
  implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
  // Validation for @ConfigurationProperties validation
  implementation("org.springframework.boot:spring-boot-starter-validation")
  // Caching
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("com.github.ben-manes.caffeine:caffeine")
  // Resilience4j for resilience patterns + Reactor operators
  implementation(libs.resilience4j.spring.boot3)
  implementation(libs.resilience4j.reactor)
  
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  compileOnly("org.jetbrains:annotations:26.0.2")
  // Optional: metadata for IDE assistance on configuration properties
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
  testImplementation("org.springframework.security:spring-security-test")
  // MockWebServer for HTTP simulations in WebClient tests
  testImplementation("com.squareup.okhttp3:mockwebserver:_")
}

tasks.withType<Test> {
  useJUnitPlatform()
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("spring.profiles.active", "test")
  val mockitoCore = classpath.find { it.name.contains("mockito-core") }
  if (mockitoCore != null) {
    jvmArgs("-Xshare:off", "-javaagent:${mockitoCore.absolutePath}")
  }
  finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  options.compilerArgs.add("-parameters")
  options.compilerArgs.add("-Xlint:unchecked")
  options.compilerArgs.add("-Xlint:deprecation")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("spring.profiles.active", "dev")
}

tasks.jacocoTestReport {
  reports {
    xml.required.set(true)
    csv.required.set(true)
  }
}

