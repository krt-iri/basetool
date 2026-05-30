package de.greluc.krt.iri.basetool.frontend.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that provisions the full Basetool stack (Postgres x2, Keycloak, Redis, backend,
 * frontend) for end-to-end tests and tears it down once at the end of the test run.
 *
 * <p>It drives the {@code docker compose} CLI directly rather than Testcontainers' {@code
 * ComposeContainer}: the Phase-0 spike (see {@code docs/E2E_TESTING_PLAN.md}) established that
 * {@code ComposeContainer} cannot express this stack's combination of {@code --profile dev}, four
 * stacked {@code -f} files, fixed published ports, and the {@code !override} isolation tags. The
 * exact compose invocation here mirrors the documented local test-stack flow plus the {@code
 * docker-compose.e2e.yml} isolation override.
 *
 * <p><b>Target-agnostic.</b> If the {@code E2E_BASE_URL} environment variable (or {@code
 * -De2e.baseUrl}) is set, the extension does <i>not</i> manage Docker at all — the tests run
 * against that already-running deployment (e.g. a staging environment). Otherwise it provisions an
 * ephemeral local stack and exposes {@value #EPHEMERAL_BASE_URL}.
 *
 * <p><b>Lifecycle.</b> Register once via a {@code @RegisterExtension static} field. The stack is
 * brought up exactly once on the first {@link #beforeAll} and stopped exactly once when the whole
 * JUnit test plan finishes, via a {@link ExtensionContext.Store.CloseableResource} stored in the
 * root context.
 *
 * <p>All credentials baked in here are throwaway values that must match {@code
 * realm-export.e2e.json} (the {@code backend-service} client secret) and the generated keystore
 * password — never reuse them anywhere, never substitute production values.
 */
public final class E2eStackExtension implements BeforeAllCallback {

  /** External origin the ephemeral frontend is reachable at (HTTPS, self-signed). */
  static final String EPHEMERAL_BASE_URL = "https://localhost:18081";

  /** Throwaway PKCS12 keystore password; must match {@code SERVER_SSL_KEY_STORE_PASSWORD} below. */
  private static final String KEYSTORE_PW = "keystore-e2e-pw-do-not-use-in-prod";

  /** Local image tag the compose build override tags the freshly built images with. */
  private static final String IMAGE_TAG = "e2e-local";

  /** Max time to wait for {@code docker compose up --build --wait} to finish. */
  private static final Duration UP_TIMEOUT = Duration.ofMinutes(12);

  /** Max time to wait for {@code docker compose down}. */
  private static final Duration DOWN_TIMEOUT = Duration.ofMinutes(3);

  /**
   * The four stacked compose files, in precedence order (base -> test -> build -> e2e isolation).
   */
  private static final List<String> COMPOSE_FILES =
      List.of(
          "docker-compose.yml",
          "docker-compose.test.yml",
          "docker-compose.build.yml",
          "docker-compose.e2e.yml");

  /** The dev-profile services the E2E stack needs (npm is intentionally excluded). */
  private static final List<String> SERVICES =
      List.of(
          "db-backend-dev",
          "db-keycloak-dev",
          "keycloak-dev",
          "redis-dev",
          "backend-dev",
          "frontend-dev");

  /** Guards one-time start across multiple test classes sharing this extension. */
  private static volatile boolean started = false;

  /**
   * Resolves the URL the tests should target: an explicit {@code E2E_BASE_URL} / {@code
   * -De2e.baseUrl} (staging mode) when provided, otherwise the ephemeral local frontend.
   *
   * @return the base URL of the system under test
   */
  public String baseUrl() {
    String external = externalBaseUrl();
    return external != null ? external : EPHEMERAL_BASE_URL;
  }

  /**
   * Reports whether this extension is responsible for the Docker lifecycle. {@code false} when an
   * external base URL was supplied (staging), in which case browser-side workarounds tied to the
   * local stack (e.g. the {@code host.docker.internal} resolver remap) should be skipped.
   *
   * @return {@code true} when an ephemeral local stack is being managed, {@code false} for an
   *     external target
   */
  public boolean managesStack() {
    return externalBaseUrl() == null;
  }

  /**
   * Brings the ephemeral stack up on the first invocation (no-op in external/staging mode, and a
   * no-op on subsequent invocations from other test classes). Registers the one-time teardown on
   * the JUnit root store so it runs after the entire test plan.
   *
   * @param context the JUnit extension context whose root store owns the teardown hook
   * @throws Exception if bootstrap or {@code docker compose up} fails
   */
  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    if (!managesStack() || started) {
      return;
    }
    synchronized (E2eStackExtension.class) {
      if (started) {
        return;
      }
      Path root = repoRoot();
      stageRealm(root);
      ensureKeystore(root);
      composeUp(root);
      // Seed UEX-owned catalog reference data (refinery-hosting location, ship type, refining
      // method) the admin REST API cannot create on a fresh DB — unblocks the Refinery/Hangar
      // flows.
      new BackendSeeder().seedCatalog();
      started = true;
      context
          .getRoot()
          .getStore(ExtensionContext.Namespace.GLOBAL)
          .put(
              "e2e-docker-stack",
              (ExtensionContext.Store.CloseableResource) () -> composeDown(root));
    }
  }

  /**
   * Returns the explicitly configured external base URL, or {@code null} when none was supplied
   * (the signal to manage an ephemeral stack). {@code E2E_BASE_URL} (environment) takes precedence
   * over {@code -De2e.baseUrl} (system property).
   *
   * @return the external base URL, or {@code null} to manage an ephemeral stack
   */
  private static String externalBaseUrl() {
    String env = System.getenv("E2E_BASE_URL");
    if (env != null && !env.isBlank()) {
      return env;
    }
    String prop = System.getProperty("e2e.baseUrl");
    return prop != null && !prop.isBlank() ? prop : null;
  }

  /**
   * Copies the checked-in synthetic realm from the e2e classpath to {@code <repoRoot>/
   * realm-export.json}, which the base compose file bind-mounts into Keycloak for {@code
   * --import-realm}.
   *
   * @param root the repository root containing the compose files
   * @throws IOException if the realm resource is missing or cannot be written
   */
  private void stageRealm(Path root) throws IOException {
    try (InputStream in = E2eStackExtension.class.getResourceAsStream("/realm-export.e2e.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "realm-export.e2e.json not found on the e2e classpath; expected under"
                + " frontend/src/e2e/resources");
      }
      Files.copy(in, root.resolve("realm-export.json"), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Generates a throwaway self-signed PKCS12 keystore at {@code <repoRoot>/keystore.p12} (alias
   * {@code basetool}, SANs covering localhost / the Docker network aliases) if one is not already
   * present. Uses the {@code keytool} shipped with the test JVM's own JDK so no {@code keytool} on
   * {@code PATH} is required.
   *
   * @param root the repository root the compose files bind-mount the keystore from
   * @throws Exception if {@code keytool} cannot be found or exits non-zero
   */
  private void ensureKeystore(Path root) throws Exception {
    Path keystore = root.resolve("keystore.p12");
    if (Files.exists(keystore)) {
      return;
    }
    boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    Path keytool =
        Paths.get(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
    if (!Files.exists(keytool)) {
      throw new IllegalStateException("keytool not found at " + keytool);
    }
    runProcess(
        root,
        "keytool",
        List.of(
            keytool.toString(),
            "-genkeypair",
            "-alias",
            "basetool",
            "-storetype",
            "PKCS12",
            "-keystore",
            keystore.toString(),
            "-storepass",
            KEYSTORE_PW,
            "-keypass",
            KEYSTORE_PW,
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "365",
            "-dname",
            "CN=localhost, OU=Test, O=KRT Basetool E2E, L=Test, ST=Test, C=DE",
            "-ext",
            "san=dns:localhost,ip:127.0.0.1,dns:backend,dns:frontend,dns:host.docker.internal"),
        Map.of(),
        Duration.ofMinutes(2));
  }

  /**
   * Builds the images from the current source and brings the dev-profile stack up, blocking until
   * every service reports healthy.
   *
   * @param root the repository root the compose files live in
   * @throws Exception if {@code docker compose up} exits non-zero or times out
   */
  private void composeUp(Path root) throws Exception {
    try {
      runProcess(
          root,
          "compose-up",
          composeCommand("up", "-d", "--build", "--wait", "--wait-timeout", "360"),
          throwawayEnv(),
          UP_TIMEOUT);
    } catch (Exception up) {
      // The stack failed to come up healthy (e.g. a service never passed its healthcheck). Dump the
      // container logs into build/e2e so the CI artifact shows *why* — `up --wait` itself only
      // reports "dependency failed to start", not the failing container's own output.
      captureComposeLogs(root);
      throw up;
    }
  }

  /**
   * Best-effort dump of the stack's container logs to {@code build/e2e/compose-logs.log} for
   * post-mortem diagnostics when {@link #composeUp} fails. Never throws — the original bring-up
   * failure is the one that should propagate.
   *
   * @param root the repository root the compose files live in
   */
  private void captureComposeLogs(Path root) {
    try {
      runProcess(
          root,
          "compose-logs",
          composeCommand("logs", "--no-color", "--tail", "300"),
          throwawayEnv(),
          Duration.ofMinutes(2));
    } catch (Exception ignored) {
      System.out.println("[E2E] could not capture compose logs: " + ignored.getMessage());
    }
  }

  /**
   * Tears the stack down and removes its named volumes. Best-effort: a failure here is logged but
   * does not fail the build (the test outcome has already been decided).
   *
   * @param root the repository root the compose files live in
   */
  private void composeDown(Path root) {
    try {
      runProcess(
          root,
          "compose-down",
          composeCommand("down", "--volumes", "--remove-orphans"),
          throwawayEnv(),
          DOWN_TIMEOUT);
    } catch (Exception e) {
      System.out.println("[E2E] stack teardown failed (ignored): " + e.getMessage());
    }
  }

  /**
   * Assembles a {@code docker compose -f ... --profile dev <verb> ...} command line for the given
   * verb and trailing arguments. {@code up} variants get the explicit service list appended.
   *
   * @param verbAndArgs the compose verb followed by its flags (e.g. {@code "up","-d","--build"})
   * @return the full argument vector to hand to {@link ProcessBuilder}
   */
  private List<String> composeCommand(String... verbAndArgs) {
    java.util.ArrayList<String> cmd = new java.util.ArrayList<>(List.of("docker", "compose"));
    for (String f : COMPOSE_FILES) {
      cmd.add("-f");
      cmd.add(f);
    }
    cmd.add("--profile");
    cmd.add("dev");
    cmd.addAll(List.of(verbAndArgs));
    // Scope `up` and `logs` to the explicit service list (npm is intentionally excluded).
    if ("up".equals(verbAndArgs[0]) || "logs".equals(verbAndArgs[0])) {
      cmd.addAll(SERVICES);
    }
    return cmd;
  }

  /**
   * The throwaway environment compose substitutes its {@code ${VAR}} placeholders from. Passed via
   * the subprocess environment instead of an {@code --env-file}, so no credentials file is written
   * to disk. {@code KEYCLOAK_ADMIN_CLIENT_SECRET} must match the {@code backend-service} secret in
   * {@code realm-export.e2e.json}; {@code SERVER_SSL_KEY_STORE_PASSWORD} must match the generated
   * keystore.
   *
   * @return the environment variable map for the compose subprocess
   */
  private Map<String, String> throwawayEnv() {
    Map<String, String> env = new LinkedHashMap<>();
    env.put("POSTGRES_DB", "krt_basetool_e2e");
    env.put("POSTGRES_USER", "basetool_e2e");
    env.put("POSTGRES_PASSWORD", "basetool-e2e-pw-do-not-use-in-prod");
    env.put("KC_POSTGRES_DB", "keycloak_e2e");
    env.put("KC_POSTGRES_USER", "keycloak_e2e");
    env.put("KC_POSTGRES_PASSWORD", "keycloak-e2e-pw-do-not-use-in-prod");
    env.put("KC_BOOTSTRAP_ADMIN_USERNAME", "admin");
    env.put("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-e2e-pw-do-not-use-in-prod");
    env.put("KEYCLOAK_ADMIN_CLIENT_SECRET", "e2e-client-secret-do-not-use-in-prod");
    env.put("REDIS_PASSWORD", "redis-e2e-pw-do-not-use-in-prod");
    env.put("SERVER_SSL_KEY_STORE_PASSWORD", KEYSTORE_PW);
    env.put("IRI_KEYSTORE_HOST_PATH", "./keystore.p12");
    env.put("IRI_BASETOOL_VERSION", IMAGE_TAG);
    return env;
  }

  /**
   * Walks up from the working directory until the directory containing {@code docker-compose.yml}
   * is found (the test runs with the {@code frontend} module as its working directory).
   *
   * @return the repository root path
   */
  private static Path repoRoot() {
    Path start = Paths.get("").toAbsolutePath();
    for (Path p = start; p != null; p = p.getParent()) {
      if (Files.exists(p.resolve("docker-compose.yml"))) {
        return p;
      }
    }
    throw new IllegalStateException("docker-compose.yml not found walking up from " + start);
  }

  /**
   * Runs an external process, streaming its combined output to {@code <workingDir>/build/e2e/
   * <label>.log}, and fails with the log tail if it exits non-zero or exceeds {@code timeout}.
   *
   * @param workingDir the process working directory
   * @param label short name used for the per-process log file and error messages
   * @param command the argument vector
   * @param extraEnv environment variables to add on top of the inherited environment
   * @param timeout how long to wait before treating the process as hung
   * @throws Exception if the process fails to start, times out, or exits non-zero
   */
  private void runProcess(
      Path workingDir,
      String label,
      List<String> command,
      Map<String, String> extraEnv,
      Duration timeout)
      throws Exception {
    Path logDir = Paths.get("build", "e2e").toAbsolutePath();
    Files.createDirectories(logDir);
    Path log = logDir.resolve(label + ".log");
    System.out.printf("[E2E] %s: %s%n", label, String.join(" ", command));
    ProcessBuilder pb =
        new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
    pb.environment().putAll(extraEnv);
    Process process = pb.start();
    if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException(
          label + " timed out after " + timeout.toMinutes() + " min; see " + log);
    }
    int exit = process.exitValue();
    if (exit != 0) {
      throw new IllegalStateException(
          label + " failed (exit " + exit + "). Last log lines:\n" + tail(log, 25));
    }
  }

  /**
   * Reads the last {@code maxLines} lines of a log file for inclusion in a failure message.
   *
   * @param log the log file
   * @param maxLines how many trailing lines to return
   * @return the trailing lines joined by newlines, or a diagnostic note if the file is unreadable
   */
  private static String tail(Path log, int maxLines) {
    try {
      List<String> lines = Files.readAllLines(log);
      return String.join("\n", lines.subList(Math.max(0, lines.size() - maxLines), lines.size()));
    } catch (IOException e) {
      return "(could not read " + log + ": " + e.getMessage() + ")";
    }
  }
}
