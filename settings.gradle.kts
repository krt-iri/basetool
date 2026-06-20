plugins { id("de.fayard.refreshVersions") version "0.60.6" }

rootProject.name = "basetool"

include("backend")

include("frontend")

include("ingest")

// Custom Keycloak provider JAR (Discord federation + first-login membership gate,
// epic #720). A plain library module — NOT a Spring Boot app — compiled against the
// Keycloak 26 server SPIs (compileOnly) and emitting Java-21 bytecode so the
// Keycloak runtime JVM can load it. See keycloak-spi/build.gradle.kts.
include("keycloak-spi")
