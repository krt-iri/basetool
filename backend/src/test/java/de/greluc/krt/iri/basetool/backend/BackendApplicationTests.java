package de.greluc.krt.iri.basetool.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class BackendApplicationTests {

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void contextLoads() {}
}
