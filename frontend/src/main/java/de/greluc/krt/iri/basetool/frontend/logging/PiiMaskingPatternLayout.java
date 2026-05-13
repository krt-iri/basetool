package de.greluc.krt.iri.basetool.frontend.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Logback layout customised for Pii Masking Pattern. */
public class PiiMaskingPatternLayout extends PatternLayout {

  private static final String JWT_PATTERN =
      "(eyJ[a-zA-Z0-9_-]{5,}\\.eyJ[a-zA-Z0-9_-]{5,}\\.[a-zA-Z0-9_-]{5,})";
  private static final String EMAIL_PATTERN =
      "([a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})";
  private static final String KEYWORD_TOKEN_PATTERN =
      "(?i)(bearer\\s+|token\\s*[:=]?\\s*|session[-_]?id\\s*[:=]?\\s*|authorization\\s*[:=]?\\s*(?:bearer\\s+)?)([a-zA-Z0-9\\-_\\.]+)";

  private static final Pattern PII_PATTERN =
      Pattern.compile(JWT_PATTERN + "|" + EMAIL_PATTERN + "|" + KEYWORD_TOKEN_PATTERN);

  @Override
  public String doLayout(ILoggingEvent event) {
    String result = super.doLayout(event);
    if (result == null || result.isEmpty()) {
      return result;
    }

    Matcher matcher = PII_PATTERN.matcher(result);
    if (!matcher.find()) {
      return result;
    }

    matcher.reset();
    StringBuilder sb = new StringBuilder(result.length());

    while (matcher.find()) {
      if (matcher.group(1) != null) {
        matcher.appendReplacement(sb, "JWT_***");
      } else if (matcher.group(2) != null) {
        matcher.appendReplacement(sb, "***@***.***");
      } else if (matcher.group(3) != null && matcher.group(4) != null) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(3)) + "***");
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }
}
