package de.greluc.krt.iri.basetool.frontend.e2e;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Seeds the minimal backend state the ephemeral-stack create-flows need, via the backend REST API
 * with a bearer token for the synthetic test user.
 *
 * <p>{@code UserService.syncUser} creates only an {@code app_user} row on first login, never an
 * {@code org_unit_membership}, so staffel-scoped creates (Mission, Ship, RefineryOrder) would
 * otherwise 400 with "user has no org-unit membership". This helper assigns the test user to the
 * seeded IRIDIUM Squadron via {@code PATCH /api/v1/users/{id}/squadron} so those flows can run.
 *
 * <p>Local-stack only: it talks to Keycloak on {@code http://localhost:18080} and the backend on
 * {@code https://localhost:11261} (self-signed dev cert — the HTTP client trusts all certificates).
 * The bearer token is minted with the Keycloak password grant on the public {@code
 * basetool-frontend} client; the backend resource server accepts it on issuer + signature alone (no
 * audience check).
 */
public final class BackendSeeder {

  private static final String KEYCLOAK_TOKEN_URL =
      "http://localhost:18080/realms/iri/protocol/openid-connect/token";
  private static final String BACKEND_BASE_URL = "https://localhost:11261";
  private static final String CLIENT_ID = "basetool-frontend";
  private static final String IRIDIUM_SQUADRON_ID = "00000000-0000-0000-0000-000000000001";

  /** JDBC coordinates of the ephemeral backend Postgres (published on the host loopback). */
  private static final String JDBC_URL = "jdbc:postgresql://localhost:15432/krt_basetool_e2e";

  private static final String DB_USER = "basetool_e2e";
  private static final String DB_PASSWORD = "basetool-e2e-pw-do-not-use-in-prod";

  /**
   * How many times to re-read the user and retry the squadron PATCH on a 409. The per-request
   * {@code syncUser} can bump the {@code @Version} between our read and write; each retry re-reads
   * the fresh version, so a small bound converges.
   */
  private static final int MAX_VERSION_RETRIES = 4;

  private final HttpClient http;

  /** Builds a seeder whose HTTP client trusts the backend's self-signed dev certificate. */
  public BackendSeeder() {
    this.http =
        HttpClient.newBuilder()
            .sslContext(trustAllContext())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
  }

  /**
   * Ensures the given test user is a member of the IRIDIUM Squadron so staffel-scoped create
   * endpoints accept its requests. Idempotent: a no-op when the user already has a squadron.
   *
   * @param username the Keycloak username of the test user
   * @param password the Keycloak password of the test user
   */
  public void ensureIridiumMembership(String username, String password) {
    try {
      String token = passwordGrant(username, password);
      for (int attempt = 1; attempt <= MAX_VERSION_RETRIES; attempt++) {
        JsonObject me = getJson("/api/v1/users/me", token);
        if (me.has("squadron") && !me.get("squadron").isJsonNull()) {
          return; // already a member — nothing to seed
        }
        String userId = me.get("id").getAsString();
        long version = me.get("version").getAsLong();
        int status = patchSquadron(token, userId, version);
        if (status >= 200 && status < 300) {
          return;
        }
        if (status != 409) {
          throw new IllegalStateException("Squadron PATCH failed: HTTP " + status);
        }
        // 409: the optimistic-lock version moved (syncUser bumped it) — re-read and retry.
      }
      throw new IllegalStateException("Squadron seeding exhausted retries on HTTP 409");
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.ensureIridiumMembership failed", e);
    }
  }

  /**
   * Seeds UEX-owned catalog reference data (a refinery-hosting location, a ship type, a refining
   * method) directly into the backend Postgres over JDBC, from the {@code /uex-catalog-seed.sql}
   * classpath fixture. These rows are normally UEX-synced and cannot be created via the admin REST
   * API on a fresh DB. Idempotent (fixed UUIDs + {@code ON CONFLICT}). Run once after the ephemeral
   * stack is healthy.
   */
  public void seedCatalog() {
    try {
      String body =
          readResource("/uex-catalog-seed.sql")
              .lines()
              .filter(line -> !line.strip().startsWith("--"))
              .collect(Collectors.joining("\n"));
      try (Connection connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
          Statement statement = connection.createStatement()) {
        for (String rawStatement : body.split(";")) {
          String sql = rawStatement.strip();
          if (!sql.isEmpty()) {
            statement.execute(sql);
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.seedCatalog failed", e);
    }
  }

  /**
   * Reads a UTF-8 classpath resource into a string.
   *
   * @param path absolute classpath path (leading {@code /})
   * @return the resource contents
   * @throws IOException if the resource is missing or unreadable
   */
  private String readResource(String path) throws IOException {
    try (InputStream in = BackendSeeder.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException(path + " not found on the e2e classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * Creates a job-order-pickable material via {@code POST /api/v1/materials} and returns its id, so
   * the job-order create form's material dropdown (filtered to {@code isJobOrder=true}) has an
   * entry to select. {@code categoryId} is optional and omitted.
   *
   * @param username the Keycloak username of the (admin) test user
   * @param password the Keycloak password of the test user
   * @param name the material name to create
   * @return the created material's id
   */
  public String ensureJobOrderMaterial(String username, String password, String name) {
    try {
      String token = passwordGrant(username, password);
      String body =
          "{\"name\":\""
              + name
              + "\",\"type\":\"RAW\",\"quantityType\":\"SCU\",\"isJobOrder\":true}";
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(BACKEND_BASE_URL + "/api/v1/materials"))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Material create failed: HTTP " + response.statusCode() + " " + response.body());
      }
      return JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.ensureJobOrderMaterial failed", e);
    }
  }

  /**
   * Creates an entity via an authenticated {@code POST} and returns its id — used to seed the
   * reference data (locations, refining methods, materials) that the create-flows select from.
   *
   * @param username Keycloak username of the (admin) test user
   * @param password Keycloak password
   * @param path backend path beginning with {@code /}
   * @param jsonBody the JSON request body
   * @return the created entity's id
   */
  public String seedEntity(String username, String password, String path, String jsonBody) {
    try {
      String token = passwordGrant(username, password);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(BACKEND_BASE_URL + path))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();
      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "POST " + path + " failed: HTTP " + response.statusCode() + " " + response.body());
      }
      return JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.seedEntity(" + path + ") failed", e);
    }
  }

  /**
   * Creates a refinery {@code Location} the create form's location dropdown can select.
   *
   * @param username admin username
   * @param password admin password
   * @param name location name
   * @return the created location id
   */
  public String createLocation(String username, String password, String name) {
    // LocationDto has a primitive `boolean hidden`; omitting it fails Jackson deserialization.
    return seedEntity(
        username, password, "/api/v1/locations", "{\"name\":\"" + name + "\",\"hidden\":false}");
  }

  /**
   * Creates a {@code RefiningMethod} the create form's method dropdown can select.
   *
   * @param username admin username
   * @param password admin password
   * @param name refining-method name
   * @return the created refining-method id
   */
  public String createRefiningMethod(String username, String password, String name) {
    return seedEntity(
        username, password, "/api/v1/refining-methods", "{\"name\":\"" + name + "\"}");
  }

  /**
   * Creates a RAW material flagged {@code isManualRawMaterial=true} so it appears in the refinery
   * input-material dropdown (filtered to {@code type=='RAW' or isManualRawMaterial==true}).
   *
   * @param username admin username
   * @param password admin password
   * @param name material name
   * @return the created material id
   */
  public String createRefineryMaterial(String username, String password, String name) {
    return seedEntity(
        username,
        password,
        "/api/v1/materials",
        "{\"name\":\""
            + name
            + "\",\"type\":\"RAW\",\"quantityType\":\"SCU\",\"isManualRawMaterial\":true}");
  }

  /**
   * Performs a Keycloak Resource-Owner-Password-Credentials grant on the public {@code
   * basetool-frontend} client and returns the access token.
   *
   * @param username Keycloak username
   * @param password Keycloak password
   * @return the raw JWT access token
   * @throws Exception if the token endpoint is unreachable or returns a non-200
   */
  private String passwordGrant(String username, String password) throws Exception {
    String form =
        "grant_type=password&client_id="
            + CLIENT_ID
            + "&username="
            + enc(username)
            + "&password="
            + enc(password)
            + "&scope=openid";
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(KEYCLOAK_TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Keycloak password grant failed: HTTP " + response.statusCode() + " " + response.body());
    }
    return JsonParser.parseString(response.body())
        .getAsJsonObject()
        .get("access_token")
        .getAsString();
  }

  /**
   * Issues an authenticated {@code GET} against a backend path and returns the parsed JSON object.
   *
   * @param path backend path beginning with {@code /}
   * @param token bearer token
   * @return the response body as a {@link JsonObject}
   * @throws Exception on transport failure or a non-200 status
   */
  private JsonObject getJson(String path, String token) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(BACKEND_BASE_URL + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "GET " + path + " failed: HTTP " + response.statusCode() + " " + response.body());
    }
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  /**
   * Sends the squadron-assignment PATCH and returns the HTTP status so the caller can react to a
   * 409 optimistic-lock conflict.
   *
   * @param token bearer token
   * @param userId the app_user id to assign
   * @param version the optimistic-lock version last read for that user
   * @return the HTTP status code of the PATCH
   * @throws Exception on transport failure
   */
  private int patchSquadron(String token, String userId, long version) throws Exception {
    String body = "{\"squadronId\":\"" + IRIDIUM_SQUADRON_ID + "\",\"version\":" + version + "}";
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(BACKEND_BASE_URL + "/api/v1/users/" + userId + "/squadron"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .build();
    return http.send(request, BodyHandlers.ofString()).statusCode();
  }

  /**
   * URL-encodes a form value.
   *
   * @param value raw value
   * @return the {@code application/x-www-form-urlencoded} encoding
   */
  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /**
   * Builds an {@link SSLContext} that trusts every certificate — acceptable here because the only
   * target is the local ephemeral backend's self-signed dev cert.
   *
   * @return a trust-all TLS context
   */
  private static SSLContext trustAllContext() {
    TrustManager[] trustAll = {
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
          // test-only client: trust everything
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
          // test-only client: trust the backend's self-signed dev cert
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }
    };
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustAll, new SecureRandom());
      return context;
    } catch (Exception e) {
      throw new IllegalStateException("Could not build trust-all SSLContext", e);
    }
  }
}
