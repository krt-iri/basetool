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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
 * {@code https://localhost:11261} (self-signed dev cert — the HTTP client trusts only that cert).
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
            .sslContext(backendCertContext())
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
   * Creates a Job Order via {@code POST /api/v1/orders} with a single material line and returns its
   * id, so the handover flow has an order to record a handover against. The material line's {@code
   * minQuality} must be at least 700 ({@code CreateJobOrderMaterialDto} constraint).
   *
   * <p>The given org unit is named as BOTH the responsible (processing) and the requesting
   * (customer) unit — these flows model a squadron that owns and fulfils its own order. The backend
   * requires a {@code responsibleOrgUnitId} that resolves to a <em>profit-eligible</em> unit; the
   * canonical IRIDIUM Squadron is opted in once during stack bootstrap ({@link E2eStackExtension}),
   * so passing it here succeeds. A squadron-responsible order is private to that squadron + admins,
   * which is exactly the ownership these flows assert.
   *
   * @param username the Keycloak username of the (admin) test user
   * @param password the Keycloak password of the test user
   * @param orgUnitId the org unit named as both the responsible (processing) and requesting
   *     (customer) unit of the order; must be a profit-eligible squadron (see bootstrap seeding)
   * @param handle the free-text contact handle of the order
   * @param materialId the id of the (job-order) material to request
   * @param minQuality the minimum acceptable quality of the requested material ({@code >= 700})
   * @param amount the requested amount of the material
   * @return the created job order's id
   */
  public String createJobOrder(
      String username,
      String password,
      String orgUnitId,
      String handle,
      String materialId,
      int minQuality,
      double amount) {
    String body =
        "{\"responsibleOrgUnitId\":\""
            + orgUnitId
            + "\",\"requestingOrgUnitId\":\""
            + orgUnitId
            + "\",\"handle\":\""
            + handle
            + "\",\"materials\":[{\"materialId\":\""
            + materialId
            + "\",\"minQuality\":"
            + minQuality
            + ",\"amount\":"
            + amount
            + "}]}";
    return seedEntity(username, password, "/api/v1/orders", body);
  }

  /**
   * Creates an inventory item linked to a job order via {@code POST /api/v1/inventory} and returns
   * its id, so it surfaces in the order's handover item dropdown (populated from {@code
   * findByJobOrderIdOrdered}). The item is non-personal (personal items may not carry a job-order
   * link) and stored at the given location; its quality should meet the order material's {@code
   * minQuality} to be a valid fulfillment.
   *
   * @param username the Keycloak username of the test user
   * @param password the Keycloak password of the test user
   * @param materialId the id of the material the item holds (matching the order's material)
   * @param locationId the id of the storage location of the item
   * @param jobOrderId the id of the job order to link the item to
   * @param quality the quality of the held material ({@code 0..1000})
   * @param amount the amount held (available for handover)
   * @return the created inventory item's id
   */
  public String createInventoryItemForJobOrder(
      String username,
      String password,
      String materialId,
      String locationId,
      String jobOrderId,
      int quality,
      double amount) {
    String body =
        "{\"materialId\":\""
            + materialId
            + "\",\"locationId\":\""
            + locationId
            + "\",\"quality\":"
            + quality
            + ",\"amount\":"
            + amount
            + ",\"jobOrderId\":\""
            + jobOrderId
            + "\",\"personal\":false}";
    return seedEntity(username, password, "/api/v1/inventory", body);
  }

  /**
   * Creates a Mission via {@code POST /api/v1/missions} as the given user and returns its id, so
   * cross-Staffel visibility flows have a mission owned by the caller's Staffel. {@code
   * owningOrgUnitId} is omitted, so the resolver auto-stamps the caller's home Staffel (the caller
   * must have exactly one membership). The planned start is set a week out to clear the
   * not-in-the-past check.
   *
   * @param username the Keycloak username of the creating user (a member of the owning Staffel)
   * @param password the Keycloak password
   * @param name the mission name (used to find its row in the list)
   * @param isInternal {@code true} for an internal (staffel-private) mission, {@code false} for a
   *     public one visible cross-Staffel
   * @return the created mission's id
   */
  public String createMission(String username, String password, String name, boolean isInternal) {
    String plannedStart = Instant.now().plus(Duration.ofDays(7)).toString();
    String body =
        "{\"name\":\""
            + name
            + "\",\"status\":\"PLANNED\",\"isInternal\":"
            + isInternal
            + ",\"plannedStartTime\":\""
            + plannedStart
            + "\"}";
    return seedEntity(username, password, "/api/v1/missions", body);
  }

  /**
   * Attempts {@code POST /api/v1/orders} naming the given org unit as the responsible (processing)
   * unit and returns the HTTP status WITHOUT throwing, so a test can assert the documented 400 when
   * the named unit is not profit-eligible. Only profit-eligible squadrons / Spezialkommandos may
   * process orders (V128); a freshly created SK is not profit-eligible by default, so naming it as
   * the responsible unit is rejected with 400 ("not profit-eligible").
   *
   * @param username the Keycloak username (an admin, to create in all-squadrons scope)
   * @param password the Keycloak password
   * @param responsibleOrgUnitId the responsible (processing) OrgUnit id under test (a
   *     non-profit-eligible SK, to trigger the 400)
   * @param requestingOrgUnitId the requesting (customer) OrgUnit id
   * @param handle the order contact handle
   * @param materialId the requested material id
   * @param minQuality the minimum quality ({@code >= 700})
   * @param amount the requested amount
   * @return the HTTP status code of the create attempt
   */
  public int attemptCreateJobOrderStatus(
      String username,
      String password,
      String responsibleOrgUnitId,
      String requestingOrgUnitId,
      String handle,
      String materialId,
      int minQuality,
      double amount) {
    try {
      String token = passwordGrant(username, password);
      String body =
          "{\"responsibleOrgUnitId\":\""
              + responsibleOrgUnitId
              + "\",\"requestingOrgUnitId\":\""
              + requestingOrgUnitId
              + "\",\"handle\":\""
              + handle
              + "\",\"materials\":[{\"materialId\":\""
              + materialId
              + "\",\"minQuality\":"
              + minQuality
              + ",\"amount\":"
              + amount
              + "}]}";
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(BACKEND_BASE_URL + "/api/v1/orders"))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      return http.send(request, BodyHandlers.ofString()).statusCode();
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.attemptCreateJobOrderStatus failed", e);
    }
  }

  /**
   * Issues an authenticated {@code GET} as the given user and returns the raw response body, so a
   * test can assert on its contents — e.g. that a foreign-Staffel inventory item id does NOT appear
   * in this user's org-scoped Lager-View. Throws on a non-2xx status.
   *
   * @param username the Keycloak username to authenticate as
   * @param password the Keycloak password
   * @param path the backend path beginning with {@code /}
   * @return the raw response body
   */
  public String getBody(String username, String password, String path) {
    try {
      String token = passwordGrant(username, password);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(BACKEND_BASE_URL + path))
              .header("Authorization", "Bearer " + token)
              .GET()
              .build();
      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "GET " + path + " failed: HTTP " + response.statusCode() + " " + response.body());
      }
      return response.body();
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.getBody(" + path + ") failed", e);
    }
  }

  /**
   * Logs in as the given user and returns their {@code app_user} id (the JWT {@code sub}). The call
   * also triggers {@code UserService.syncUser}, so invoking it once materialises the user's row
   * before an admin assigns memberships to it.
   *
   * @param username the Keycloak username
   * @param password the Keycloak password
   * @return the user's app_user id
   */
  public String getUserId(String username, String password) {
    try {
      String token = passwordGrant(username, password);
      return getJson("/api/v1/users/me", token).get("id").getAsString();
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.getUserId(" + username + ") failed", e);
    }
  }

  /**
   * Creates a second {@code SQUADRON} OrgUnit via {@code POST /api/v1/squadrons} (admin-only) and
   * returns its id, so cross-Staffel flows have a Staffel B alongside the canonical IRIDIUM.
   *
   * @param adminUser an admin Keycloak username
   * @param adminPassword the admin password
   * @param name the squadron name (unique across all OrgUnits)
   * @param shorthand the squadron shorthand (unique across all OrgUnits)
   * @return the created squadron's id
   */
  public String createSquadron(
      String adminUser, String adminPassword, String name, String shorthand) {
    return seedEntity(
        adminUser,
        adminPassword,
        "/api/v1/squadrons",
        "{\"name\":\""
            + name
            + "\",\"shorthand\":\""
            + shorthand
            + "\",\"isPromotionEnabled\":true}");
  }

  /**
   * Opts a squadron into (or out of) Job-Order processing by setting its {@code is_profit_eligible}
   * flag via {@code PATCH /api/v1/squadrons/{id}/profit-eligible} (admin-only, body {@code
   * {"eligible": …}}). Only profit-eligible org units may be a job order's responsible (processing)
   * unit and appear in the create form's responsible picker (V128), so this is a precondition for
   * seeding any order owned by the squadron. Throws on a non-2xx status.
   *
   * @param adminUser an admin Keycloak username (the endpoint is ADMIN-gated)
   * @param adminPassword the admin password
   * @param squadronId the squadron OrgUnit id to toggle
   * @param eligible the new {@code is_profit_eligible} value
   */
  public void setSquadronProfitEligible(
      String adminUser, String adminPassword, String squadronId, boolean eligible) {
    try {
      String token = passwordGrant(adminUser, adminPassword);
      int status =
          patch(
              token,
              "/api/v1/squadrons/" + squadronId + "/profit-eligible",
              "{\"eligible\":" + eligible + "}");
      if (status < 200 || status >= 300) {
        throw new IllegalStateException("Profit-eligibility PATCH failed: HTTP " + status);
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.setSquadronProfitEligible failed", e);
    }
  }

  /**
   * Creates a {@code SPECIAL_COMMAND} OrgUnit (SK) via {@code POST /api/v1/special-commands}
   * (admin-only) and returns its id.
   *
   * @param adminUser an admin Keycloak username
   * @param adminPassword the admin password
   * @param name the SK name (unique across all OrgUnits)
   * @param shorthand the SK shorthand (unique across all OrgUnits)
   * @return the created SK's id
   */
  public String createSpecialCommand(
      String adminUser, String adminPassword, String name, String shorthand) {
    return seedEntity(
        adminUser,
        adminPassword,
        "/api/v1/special-commands",
        "{\"name\":\"" + name + "\",\"shorthand\":\"" + shorthand + "\"}");
  }

  /**
   * Assigns {@code targetUserId} to a Staffel via {@code PATCH /api/v1/users/{id}/squadron}
   * (admin-only, body {@code {squadronId, version}}), re-reading the user's version on a 409 like
   * {@link #ensureIridiumMembership}. A user has at most one Staffel membership, so this also
   * re-homes them. Optional role flags are then set via the dedicated {@code /logistician} and
   * {@code /mission-manager} query-param toggles.
   *
   * @param adminUser an admin Keycloak username
   * @param adminPassword the admin password
   * @param targetUserId the app_user id to assign (see {@link #getUserId})
   * @param squadronId the Staffel OrgUnit id
   * @param isLogistician whether to set the {@code is_logistician} flag
   * @param isMissionManager whether to set the {@code is_mission_manager} flag
   */
  public void assignStaffelMembership(
      String adminUser,
      String adminPassword,
      String targetUserId,
      String squadronId,
      boolean isLogistician,
      boolean isMissionManager) {
    try {
      String token = passwordGrant(adminUser, adminPassword);
      for (int attempt = 1; attempt <= MAX_VERSION_RETRIES; attempt++) {
        long version = getJson("/api/v1/users/" + targetUserId, token).get("version").getAsLong();
        String body = "{\"squadronId\":\"" + squadronId + "\",\"version\":" + version + "}";
        int status = patch(token, "/api/v1/users/" + targetUserId + "/squadron", body);
        if (status >= 200 && status < 300) {
          if (isLogistician) {
            patchFlag(token, targetUserId, "logistician", "isLogistician");
          }
          if (isMissionManager) {
            patchFlag(token, targetUserId, "mission-manager", "isMissionManager");
          }
          return;
        }
        if (status != 409) {
          throw new IllegalStateException("Squadron assignment PATCH failed: HTTP " + status);
        }
      }
      throw new IllegalStateException(
          "Staffel membership assignment exhausted retries on HTTP 409");
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("BackendSeeder.assignStaffelMembership failed", e);
    }
  }

  /**
   * Sets a boolean user flag to {@code true} via its admin-only query-param PATCH endpoint (e.g.
   * {@code PATCH /api/v1/users/{id}/logistician?isLogistician=true}). Throws on a non-2xx status.
   *
   * @param token a bearer token for an admin
   * @param userId the target user id
   * @param pathSegment the endpoint path segment ({@code logistician} or {@code mission-manager})
   * @param paramName the boolean query parameter name to set to {@code true}
   * @throws Exception on transport failure
   */
  private void patchFlag(String token, String userId, String pathSegment, String paramName)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    BACKEND_BASE_URL
                        + "/api/v1/users/"
                        + userId
                        + "/"
                        + pathSegment
                        + "?"
                        + paramName
                        + "=true"))
            .header("Authorization", "Bearer " + token)
            .method("PATCH", HttpRequest.BodyPublishers.noBody())
            .build();
    int status = http.send(request, BodyHandlers.ofString()).statusCode();
    if (status < 200 || status >= 300) {
      throw new IllegalStateException("Flag PATCH " + pathSegment + " failed: HTTP " + status);
    }
  }

  /**
   * Issues an authenticated {@code PATCH} and returns the HTTP status, so callers can react to a
   * 409.
   *
   * @param token bearer token
   * @param path backend path beginning with {@code /}
   * @param jsonBody the JSON request body
   * @return the HTTP status code
   * @throws Exception on transport failure
   */
  private int patch(String token, String path, String jsonBody) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(BACKEND_BASE_URL + path))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
    return http.send(request, BodyHandlers.ofString()).statusCode();
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
   * Builds an {@link SSLContext} that trusts ONLY the e2e backend's self-signed dev certificate,
   * loaded from the keystore {@link E2eStackExtension} generates at the repository root. The cert's
   * SAN covers {@code localhost}, so the JDK's default hostname verification still applies — this
   * is a scoped trust anchor, not a trust-all manager.
   *
   * @return a TLS context trusting only the backend dev cert
   */
  private static SSLContext backendCertContext() {
    try {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      try (InputStream in = Files.newInputStream(locateKeystore())) {
        keyStore.load(in, E2eStackExtension.KEYSTORE_PW.toCharArray());
      }
      // The generated store holds a private-key entry; its certificate is not a trust anchor until
      // copied into a trust store as a trusted-certificate entry.
      KeyStore trustStore = KeyStore.getInstance("PKCS12");
      trustStore.load(null, null);
      for (String alias : Collections.list(keyStore.aliases())) {
        Certificate cert = keyStore.getCertificate(alias);
        if (cert != null) {
          trustStore.setCertificateEntry(alias, cert);
        }
      }
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trustStore);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmf.getTrustManagers(), new SecureRandom());
      return context;
    } catch (Exception e) {
      throw new IllegalStateException("Could not build the backend-cert SSLContext", e);
    }
  }

  /**
   * Locates the throwaway {@code keystore.p12} that {@link E2eStackExtension} generated, by walking
   * up from the working directory (the e2e tests run with the {@code frontend} module as CWD; the
   * keystore sits at the repository root).
   *
   * @return the path to the e2e keystore
   * @throws IllegalStateException if no {@code keystore.p12} is found up to the filesystem root
   */
  private static Path locateKeystore() {
    for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
      Path candidate = p.resolve("keystore.p12");
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "keystore.p12 not found walking up from " + Paths.get("").toAbsolutePath());
  }
}
