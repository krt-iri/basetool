package de.greluc.krt.iri.basetool.backend.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PiiMasker} — the regex-based PII / secret masker used by every log
 * appender. Previously had no dedicated test file (97% line / 25% branch). A regex bug here
 * silently leaks PII into the centralized log file, which is exactly the failure mode the project's
 * "Never log names, emails or tokens" rule is designed to prevent.
 *
 * <p>The masker is a pure static function: tests are framework-free.
 */
class PiiMaskerTest {

  // ---------------------------------------------------------------
  // Trivial inputs — early returns
  // ---------------------------------------------------------------

  @Nested
  class EarlyReturnTests {

    @Test
    void nullInput_returnedAsIs() {
      assertNull(PiiMasker.mask(null));
    }

    @Test
    void emptyInput_returnedAsIs() {
      assertEquals("", PiiMasker.mask(""));
    }

    @Test
    void inputWithoutPii_returnedAsIs() {
      // The whole branch where matcher.find() is false on the first call
      // is its own short-circuit — verify it's exercised AND that no
      // accidental allocation/copy happens by checking identity (the
      // implementation returns the same reference).
      String safe = "hello world without any sensitive content";
      assertSame(
          safe,
          PiiMasker.mask(safe),
          "PII-free input must short-circuit without allocating a new String");
    }
  }

  // ---------------------------------------------------------------
  // JWT pattern
  // ---------------------------------------------------------------

  @Nested
  class JwtMaskingTests {

    @Test
    void singleJwt_isReplacedByPlaceholder() {
      // Minimal valid-shaped JWT: eyJ<5+chars>.eyJ<5+chars>.<5+chars>
      String input = "Authorization header: eyJabcde.eyJfghij.signature1234";
      String masked = PiiMasker.mask(input);

      // Note the "Authorization" keyword ALSO triggers the keyword-token rule,
      // so the test must accept any masked output where the raw JWT no longer
      // appears. Use the assertion below.
      assertFalse(
          masked.contains("eyJabcde"), "raw JWT prefix must not survive masking: " + masked);
      assertFalse(
          masked.contains("signature1234"), "raw JWT signature must not survive: " + masked);
    }

    @Test
    void jwtAloneInString_replacedExactly() {
      String jwt = "eyJabcde.eyJfghij.signature1234";
      assertEquals("JWT_***", PiiMasker.mask(jwt));
    }

    @Test
    void shortJwtSegments_doNotMatch() {
      // Each segment must be at least 5 characters. "eyJ12.eyJ34.56" is below
      // the minimum-segment length and MUST NOT be matched as a JWT.
      String input = "eyJ12.eyJ34.56";
      assertEquals(
          input,
          PiiMasker.mask(input),
          "segments shorter than 5 chars must not match the JWT pattern");
    }

    @Test
    void jwtEmbeddedInSentence_replacedInPlace() {
      String masked = PiiMasker.mask("got token eyJabcde.eyJfghij.signature1234 OK");
      // The "token" keyword triggers keyword masking; the bare JWT past the keyword
      // is what we care about. The masked output must NOT contain the literal JWT.
      assertFalse(masked.contains("eyJabcde.eyJfghij.signature1234"));
    }
  }

  // ---------------------------------------------------------------
  // Email pattern
  // ---------------------------------------------------------------

  @Nested
  class EmailMaskingTests {

    @Test
    void simpleEmail_isReplaced() {
      assertEquals("contact: ***@***.***", PiiMasker.mask("contact: alice@example.com"));
    }

    @Test
    void emailWithPlusAndDot_isReplaced() {
      // The class explicitly supports the local-part characters in RFC 5322.
      assertEquals("***@***.***", PiiMasker.mask("alice.lid+notes@example.co.uk"));
    }

    @Test
    void multipleEmails_allReplaced() {
      String masked = PiiMasker.mask("from: a@b.co to: c@d.co");
      assertEquals("from: ***@***.*** to: ***@***.***", masked);
    }

    @Test
    void singleLetterTld_doesNotMatch() {
      // The pattern requires at least 2 letters in the TLD.
      String input = "wrong: alice@example.x";
      assertEquals(
          input,
          PiiMasker.mask(input),
          "TLDs shorter than 2 chars must not match the email pattern");
    }
  }

  // ---------------------------------------------------------------
  // Keyword + token pattern
  // ---------------------------------------------------------------

  @Nested
  class KeywordTokenTests {

    @Test
    void bearerToken_secretReplaced_keywordKept() {
      String masked = PiiMasker.mask("Bearer abcDEF123_-.value");
      assertEquals("Bearer ***", masked, "the 'Bearer ' prefix is kept, the value is wiped");
    }

    @Test
    void tokenColonEquals_secretReplaced() {
      assertEquals("token=***", PiiMasker.mask("token=abcDEF.123_-"));
      assertEquals("token: ***", PiiMasker.mask("token: abcDEF.123_-"));
      assertEquals("token  ***", PiiMasker.mask("token  abcDEF.123_-"));
    }

    @Test
    void sessionIdVariants_replaced() {
      assertEquals("session-id=***", PiiMasker.mask("session-id=qWeRtY12345"));
      assertEquals("session_id: ***", PiiMasker.mask("session_id: qWeRtY12345"));
      assertEquals("sessionid=***", PiiMasker.mask("sessionid=qWeRtY12345"));
    }

    @Test
    void authorizationKeyword_replaced() {
      assertEquals("Authorization: ***", PiiMasker.mask("Authorization: abcDEF12345_-"));
    }

    @Test
    void authorizationWithBearer_keywordAndPrefixKept_valueReplaced() {
      // The keyword group captures "authorization Bearer " (with trailing space),
      // and the value is the actual secret.
      String masked = PiiMasker.mask("Authorization: Bearer abcDEF12345");
      assertEquals(
          "Authorization: Bearer ***",
          masked,
          "both 'Authorization:' and 'Bearer ' must be preserved as the keyword");
    }

    @Test
    void caseInsensitive_keywordMatch() {
      assertEquals("BEARER ***", PiiMasker.mask("BEARER abc12345"));
      assertEquals("Bearer ***", PiiMasker.mask("Bearer abc12345"));
      assertEquals("bearer ***", PiiMasker.mask("bearer abc12345"));
      assertEquals("Token: ***", PiiMasker.mask("Token: abc12345"));
    }
  }

  // ---------------------------------------------------------------
  // Mixed input — multiple PII kinds in one string
  // ---------------------------------------------------------------

  @Nested
  class MixedTests {

    @Test
    void emailAndBearer_bothMasked() {
      String masked =
          PiiMasker.mask("user alice@example.com asked Bearer abcDEF12345 in the same call");
      assertFalse(masked.contains("alice@example.com"));
      assertFalse(masked.contains("abcDEF12345"));
      assertTrue(masked.contains("***@***.***"));
      assertTrue(masked.contains("Bearer ***"));
    }

    @Test
    void jwtPlusEmail_bothMasked() {
      String masked =
          PiiMasker.mask("log{jwt=eyJabcde.eyJfghij.signature1234, mail=bob@example.org}");
      assertFalse(masked.contains("eyJabcde.eyJfghij.signature1234"));
      assertFalse(masked.contains("bob@example.org"));
    }
  }

  // ---------------------------------------------------------------
  // JSON safety — must NOT break surrounding quotes/backslashes
  // ---------------------------------------------------------------

  @Nested
  class JsonSafetyTests {

    @Test
    void emailInsideJsonString_surroundingQuotesIntact() {
      // The class docstring promises the masker on top of serialized JSON
      // leaves the JSON syntactically valid. Verify by feeding a JSON
      // fragment and ensuring the surrounding quotes survive.
      String json = "{\"email\":\"alice@example.com\",\"id\":1}";
      String masked = PiiMasker.mask(json);

      assertEquals("{\"email\":\"***@***.***\",\"id\":1}", masked);
    }

    @Test
    void bearerInsideJsonString_surroundingQuotesIntact() {
      String json = "{\"authHeader\":\"Bearer abc123\"}";
      String masked = PiiMasker.mask(json);

      assertEquals("{\"authHeader\":\"Bearer ***\"}", masked);
    }

    @Test
    void backslashInInput_notInjectedByReplacement() {
      // Matcher.appendReplacement treats backslash specially. The Javadoc
      // promises NO backslashes in the replacement text. Confirm by feeding
      // a benign string with no PII and asserting it survives unchanged
      // even though the keyword + Matcher.quoteReplacement path is exercised.
      String input = "path = C:\\Users\\token: abc123";
      String masked = PiiMasker.mask(input);

      // The keyword "token: " triggers the keyword path. The value "abc123"
      // is replaced. The surrounding backslashes must be preserved.
      assertTrue(
          masked.contains("C:\\Users\\"),
          "preceding backslashes in the input must survive intact: " + masked);
      assertTrue(
          masked.contains("token: ***"), "expected 'token: ***' in masked output, got: " + masked);
    }
  }

  // ---------------------------------------------------------------
  // Whitespace insensitivity
  // ---------------------------------------------------------------

  @Nested
  class FuzzyMatchingTests {

    @Test
    void bearerWithLotsOfSpaces() {
      // The pattern allows variable whitespace between "bearer" and the value.
      // \s+ for bearer (at least one space).
      assertEquals("Bearer ***", PiiMasker.mask("Bearer abc"));
      assertEquals("Bearer  ***", PiiMasker.mask("Bearer  abc")); // 2 spaces
    }

    @Test
    void tokenWithoutSeparator_stillMatches() {
      // The keyword pattern is `token\s*[:=]?\s*` — all separators are
      // optional. So "tokenabc" matches with group(3)="token" and
      // group(4)="abc". This is an aggressive but documented behaviour;
      // any word starting with "token" followed by alphanumerics will be
      // masked. Locking this in.
      assertEquals(
          "token***",
          PiiMasker.mask("tokenabc"),
          "the [:=] and surrounding whitespace are all optional — "
              + "the masker masks even with zero separator characters");
    }
  }
}
