package de.greluc.krt.iri.basetool.backend.config;

import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TestSecurityConfig {
  // This could be used to disable CSRF in tests if needed,
  // but better to use .with(csrf()) in MockMvc calls.
}
