package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.context.annotation.Import;

@SpringBootTest
@ActiveProfiles("test")
@Import(ApiDeprecationTest.TestDeprecationController.class)
class ApiDeprecationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @RestController
    @RequestMapping("/api/test-deprecation")
    static class TestDeprecationController {

        @GetMapping("/v1/resource")
        @ApiDeprecation(sunset = "2026-12-31", replacement = "/api/test-deprecation/v2/resource")
        public String getResourceV1() {
            return "v1";
        }

        @GetMapping("/v2/resource")
        public String getResourceV2() {
            return "v2";
        }
        
        @GetMapping("/v1/deprecated-java")
        @Deprecated
        public String getDeprecatedJava() {
            return "deprecated-java";
        }
    }

    @Test
    void testDeprecatedEndpoint_ReturnsSunsetAndLinkHeaders() throws Exception {
        mockMvc.perform(get("/api/test-deprecation/v1/resource").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Sunset", "Thu, 31 Dec 2026 00:00:00 GMT"))
                .andExpect(header().string("Link", "</api/test-deprecation/v2/resource>; rel=\"alternate\""));
    }

    @Test
    void testNewEndpoint_DoesNotReturnDeprecationHeaders() throws Exception {
        mockMvc.perform(get("/api/test-deprecation/v2/resource").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Sunset"))
                .andExpect(header().doesNotExist("Link"));
    }
    
    @Test
    void testJavaDeprecatedEndpoint_ReturnsDeprecationHeader() throws Exception {
        mockMvc.perform(get("/api/test-deprecation/v1/deprecated-java").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().doesNotExist("Sunset"))
                .andExpect(header().doesNotExist("Link"));
    }
}
