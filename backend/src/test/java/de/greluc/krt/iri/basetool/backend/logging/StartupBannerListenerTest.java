package de.greluc.krt.iri.basetool.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Contract for {@link StartupBannerListener#sanitiseJdbcUrl(String)}.
 *
 * <p>The banner must never echo credentials. This test pins down the supported URL variants and
 * null-safety so a regression would be caught immediately.
 */
class StartupBannerListenerTest {

  @Test
  void null_ShouldReturnUnknown() {
    assertThat(StartupBannerListener.sanitiseJdbcUrl(null)).isEqualTo("unknown");
    assertThat(StartupBannerListener.sanitiseJdbcUrl("")).isEqualTo("unknown");
    assertThat(StartupBannerListener.sanitiseJdbcUrl("   ")).isEqualTo("unknown");
  }

  @Test
  void cleanUrl_ShouldBeReturnedUnchanged() {
    String url = "jdbc:postgresql://db:5432/krt_basetool";
    assertThat(StartupBannerListener.sanitiseJdbcUrl(url)).isEqualTo(url);
  }

  @Test
  void passwordInQueryString_ShouldBeMasked() {
    String url = "jdbc:postgresql://db:5432/krt?user=krt_user&password=s3cret";
    String sanitised = StartupBannerListener.sanitiseJdbcUrl(url);
    assertThat(sanitised).doesNotContain("s3cret", "krt_user");
    assertThat(sanitised).contains("password=***", "user=***");
  }

  @Test
  void inlineCredentials_ShouldBeMasked() {
    String url = "jdbc:postgresql://krt_user:s3cret@db:5432/krt";
    String sanitised = StartupBannerListener.sanitiseJdbcUrl(url);
    assertThat(sanitised).doesNotContain("s3cret", "krt_user:");
    assertThat(sanitised).contains("***@db:5432");
  }
}
