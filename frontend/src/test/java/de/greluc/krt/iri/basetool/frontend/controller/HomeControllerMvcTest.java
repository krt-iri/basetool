package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Renders the full {@code index} view (including {@code fragments/sidebar} and the transitively
 * included {@code fragments/toast}) end-to-end through MockMvc, so any breakage in the toast
 * fragment's SpEL expressions surfaces as a failed test rather than a 500 in production.
 *
 * <p>Regression context: {@code fragments/toast} previously called
 * {@code #strings.matches(param.error[0], '...')}, which throws
 * {@code SpelEvaluationException: EL1004E: Method matches(java.lang.String,java.lang.String)
 * cannot be found on type org.thymeleaf.expression.Strings}. Thymeleaf's {@code Strings}
 * utility has no {@code matches} method &mdash; {@code matches} is a native SpEL infix
 * operator. The fix switched both branches to {@code param.X[0] matches '...'}. The original
 * crash happened on plain {@code GET /} for anonymous users after a failed Keycloak callback
 * (re)appended {@code ?error=...} to the URL; the tests below cover exactly that path.
 *
 * <p>The pre-fix code reached the broken {@code Strings.matches} call only when
 * {@code param.error != null} (resp. {@code param.success != null}) due to SpEL's
 * short-circuiting {@code and}, so the regression cases here intentionally supply
 * a matching query parameter.
 */
@SpringBootTest
class HomeControllerMvcTest {

    private static final String ERROR_TOAST_ID = "errorNotificationParam";
    private static final String SUCCESS_TOAST_ID = "successNotificationParam";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BackendApiClient backendApiClient;

    @MockitoBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Anonymous home() path: backendApiClient.get(uri, typeRef, isPublic=true).
        // Returning null is a valid "no upcoming mission" response and keeps the
        // template's th:if branches simple.
        when(backendApiClient.get(eq("/api/v1/missions/next"),
                any(ParameterizedTypeReference.class), anyBoolean()))
                .thenReturn(null);
    }

    @Test
    void home_ShouldRenderIndex_WithoutQueryParams() throws Exception {
        // Given: no toast-controlling query parameters
        // When: anonymous GET /
        // Then: index renders normally; the toast fragment's param-gated branches stay
        //       inactive, but the rest of fragments/toast (script + style block) still
        //       runs through Thymeleaf and SpEL.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    /**
     * Direct regression: pre-fix this exact request crashed the template with
     * {@code EL1004E: Method matches(String,String) cannot be found on type
     * org.thymeleaf.expression.Strings}. After the fix, the SpEL infix
     * {@code param.error[0] matches '...'} evaluates cleanly and the param-toast div is
     * emitted in the response body.
     */
    @Test
    void home_ShouldRenderIndex_WhenErrorParamMatchesKeyPattern() throws Exception {
        // Given: ?error= with a value that matches '^[A-Za-z][A-Za-z0-9._-]{0,79}$'
        // When: anonymous GET /
        // Then: 200, view "index", and the param-error toast div is in the HTML.
        mockMvc.perform(get("/").param("error", "notification.error.title"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString(ERROR_TOAST_ID)));
    }

    /**
     * Same regression as the error variant above, but for the symmetric {@code ?success=}
     * branch (toast.html line 38). Both branches used the broken {@code #strings.matches}
     * call and both must now route through the SpEL {@code matches} operator.
     */
    @Test
    void home_ShouldRenderIndex_WhenSuccessParamMatchesKeyPattern() throws Exception {
        mockMvc.perform(get("/").param("success", "notification.success.title"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString(SUCCESS_TOAST_ID)));
    }

    /**
     * The regex gate exists to prevent arbitrary translation-key enumeration via
     * {@code ?error=any.key}; a value that fails the pattern must simply skip the toast
     * div (no crash, no leakage). This test pins both halves of the contract:
     * (a) the request does not 500, and (b) the param-error toast is NOT rendered.
     */
    @Test
    void home_ShouldRenderIndex_WithoutParamErrorToast_WhenErrorParamFailsKeyPattern() throws Exception {
        // Given: a value that violates the key pattern (contains spaces, starts with digit)
        // When: anonymous GET /
        // Then: 200, view "index", and the param-error toast div is absent.
        mockMvc.perform(get("/").param("error", "9 invalid value with spaces"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(not(containsString(ERROR_TOAST_ID))));
    }

    /**
     * Empty {@code ?success=} satisfies {@code param.success != null} (the param is present,
     * just empty) but fails the key pattern (which requires a leading letter). Must not crash
     * and must not emit the success-param toast.
     */
    @Test
    void home_ShouldRenderIndex_WithoutParamSuccessToast_WhenSuccessParamIsEmpty() throws Exception {
        mockMvc.perform(get("/").param("success", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(not(containsString(SUCCESS_TOAST_ID))));
    }
}
