package de.greluc.krt.iri.basetool.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Audit L-1: pins the opt-in {@code aud}-claim validator built by {@link
 * SecurityConfig#audienceValidator(List)}. The resource server otherwise accepts any token the
 * realm signed (issuer/signature/expiry only); once an operator sets {@code
 * app.security.jwt.expected-audiences}, a token whose {@code aud} does not intersect the expected
 * set must be rejected (token-confusion hardening).
 */
class SecurityConfigAudienceValidatorTest {

  private static final OAuth2TokenValidator<Jwt> VALIDATOR =
      SecurityConfig.audienceValidator(List.of("basetool-backend", "account"));

  private static Jwt jwtWithAudience(List<String> audiences) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject("tester");
    if (audiences != null) {
      builder.audience(audiences);
    }
    return builder.build();
  }

  @Test
  void acceptsTokenWhoseAudienceIntersectsExpected() {
    OAuth2TokenValidatorResult result =
        VALIDATOR.validate(jwtWithAudience(List.of("basetool-backend")));
    assertFalse(result.hasErrors(), "a matching aud must pass: " + result.getErrors());
  }

  @Test
  void rejectsTokenWhoseAudienceDoesNotIntersectExpected() {
    OAuth2TokenValidatorResult result =
        VALIDATOR.validate(jwtWithAudience(List.of("some-other-client")));
    assertTrue(result.hasErrors(), "a foreign aud must be rejected (token confusion)");
  }

  @Test
  void rejectsTokenWithoutAudienceClaim() {
    OAuth2TokenValidatorResult result = VALIDATOR.validate(jwtWithAudience(null));
    assertTrue(result.hasErrors(), "a missing aud must be rejected when enforcement is enabled");
  }
}
