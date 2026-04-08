package de.greluc.krt.iri.basetool.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
public class TestSecurityConfig {
    // This could be used to disable CSRF in tests if needed, 
    // but better to use .with(csrf()) in MockMvc calls.
}
