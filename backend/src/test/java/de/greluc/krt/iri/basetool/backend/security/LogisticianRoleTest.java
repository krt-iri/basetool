package de.greluc.krt.iri.basetool.backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.config.CustomJwtGrantedAuthoritiesConverter;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LogisticianRoleTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @Autowired private UserRepository userRepository;

  @Autowired private SquadronRepository squadronRepository;

  @Autowired private OrgUnitMembershipRepository orgUnitMembershipRepository;

  @Autowired private CustomJwtGrantedAuthoritiesConverter converter;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void converterShouldAddLogisticianRole() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setUsername("logistician_user");
    user.setLogistician(true);
    userRepository.save(user);
    userRepository.flush();

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", userId.toString())
            .claim("preferred_username", "logistician_user")
            .build();

    Collection<GrantedAuthority> authorities = converter.convert(jwt);
    assertTrue(
        authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN")),
        "Should have ROLE_LOGISTICIAN");
  }

  /**
   * R6.d — happy path of the new membership-driven authority resolution: a user whose Staffel
   * membership row carries {@code is_logistician = true} gets the flat {@code ROLE_LOGISTICIAN},
   * even if the legacy {@code User.isLogistician} column is {@code false}. Before R6.d the
   * converter looked only at the legacy column and missed the membership flag entirely.
   */
  @Test
  void converterPromotesLogistician_whenMembershipFlagSet_evenIfLegacyColumnFalse() {
    UUID userId = UUID.randomUUID();
    Squadron iridium = squadronRepository.findById(Squadron.IRIDIUM_ID).orElseThrow();

    User user = new User();
    user.setId(userId);
    user.setUsername("membership_logistician");
    user.setLogistician(false); // Legacy column intentionally false — the test pins R6.d.
    user.setSquadron(iridium);
    userRepository.save(user);
    userRepository.flush();

    OrgUnitMembership membership = new OrgUnitMembership();
    membership.setId(new OrgUnitMembershipId(userId, Squadron.IRIDIUM_ID));
    membership.setUser(user);
    membership.setKind(OrgUnitKind.SQUADRON);
    membership.setJoinedAt(java.time.Instant.now());
    membership.setLogistician(true);
    orgUnitMembershipRepository.save(membership);
    orgUnitMembershipRepository.flush();

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", userId.toString())
            .claim("preferred_username", "membership_logistician")
            .build();

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertTrue(
        authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN")),
        "Membership-level is_logistician=true must promote to ROLE_LOGISTICIAN regardless of"
            + " the legacy User.isLogistician column.");
  }

  /**
   * R6.d — inverse case: when the user HAS membership rows, the membership-level flags are the
   * single source of truth. A user whose legacy {@code User.isLogistician = true} but whose Staffel
   * membership carries {@code is_logistician = false} does NOT get the flat role. The membership
   * table is authoritative because the legacy column is on the destructive-cleanup drop list —
   * keeping the legacy column as an OR-source would re-introduce ghost authorities after admin
   * actions revoke them through the membership UI.
   */
  @Test
  void converterDoesNotPromote_whenMembershipFlagFalse_evenIfLegacyColumnTrue() {
    UUID userId = UUID.randomUUID();
    Squadron iridium = squadronRepository.findById(Squadron.IRIDIUM_ID).orElseThrow();

    User user = new User();
    user.setId(userId);
    user.setUsername("stale_legacy_flag");
    user.setLogistician(true); // Legacy column stale — must NOT leak past the new authority.
    user.setSquadron(iridium);
    userRepository.save(user);
    userRepository.flush();

    OrgUnitMembership membership = new OrgUnitMembership();
    membership.setId(new OrgUnitMembershipId(userId, Squadron.IRIDIUM_ID));
    membership.setUser(user);
    membership.setKind(OrgUnitKind.SQUADRON);
    membership.setJoinedAt(java.time.Instant.now());
    membership.setLogistician(false);
    orgUnitMembershipRepository.save(membership);
    orgUnitMembershipRepository.flush();

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", userId.toString())
            .claim("preferred_username", "stale_legacy_flag")
            .build();

    Collection<GrantedAuthority> authorities = converter.convert(jwt);

    assertFalse(
        authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN")),
        "Membership-level flag is authoritative once any membership row exists — the stale"
            + " legacy User column must not re-promote a revoked role.");
  }

  @Test
  void adminShouldAccessInventory() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk());
  }

  @Test
  void officerShouldAccessInventory() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"))))
        .andExpect(status().isOk());
  }

  @Test
  void memberWithLogisticianRoleShouldAccessInventory() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LOGISTICIAN"))))
        .andExpect(status().isOk());
  }

  @Test
  void realRequestShouldHaveLogisticianRole() throws Exception {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setUsername("test_logistician");
    user.setLogistician(true);
    userRepository.save(user);
    userRepository.flush();

    mockMvc
        .perform(
            get("/api/v1/inventory/aggregated")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(userId.toString())
                                    .claim("preferred_username", "test_logistician"))
                        .authorities(converter)))
        .andDo(print())
        .andExpect(status().isOk());
  }

  @Test
  void memberWithoutFlagShouldAccessInventory() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
        .andExpect(status().isOk());
  }

  @Test
  void memberWithoutFlagShouldAccessMaterialInventory() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inventory/material/" + UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
        .andExpect(
            status().isNotFound()); // NotFound because material ID doesn't exist, but NOT 403
  }

  @Test
  void memberWithoutFlagShouldAccessAllInventory() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/inventory/all")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
        .andExpect(status().isOk());
  }
}
