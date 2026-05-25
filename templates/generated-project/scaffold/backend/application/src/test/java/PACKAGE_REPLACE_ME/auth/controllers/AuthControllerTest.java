package PACKAGE_REPLACE_ME.auth.controllers;

import PACKAGE_REPLACE_ME.auth.MockTokenService;
import PACKAGE_REPLACE_ME.security.SecurityConfig;
import PACKAGE_REPLACE_ME.service.auth.models.MockLoginRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth controller contract — exercises both endpoints + the public/private
 * path matrix from {@code SecurityConfig}. Uses {@code @WebMvcTest} so the
 * full app context is not loaded; only the web layer + the imported
 * security config + the mocked service collaborator.
 */
@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private MockTokenService mockTokenService;

    @Test
    void shouldRejectUnauthenticatedRequestToAuthMeTest() throws Exception {
        // Given:

        // When:
        ResultActions response = mvc.perform(get("/api/v1/auth/me"));

        // Then:
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUserPayloadFromJwtClaimsTest() throws Exception {
        // Given:

        // When:
        ResultActions response = mvc.perform(get("/api/v1/auth/me")
            .with(jwt().jwt(j -> j
                .subject("user_123")
                .claim("email", "alice@example.com"))));

        // Then:
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user_123"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void shouldIssueTokenOnMockLoginTest() throws Exception {
        // Given:
        when(mockTokenService.issueToken(eq("alice@example.com")))
            .thenReturn(new MockLoginRecord(
                "mock-jwt-token",
                Instant.now().plus(1, ChronoUnit.HOURS)));

        // When:
        ResultActions response = mvc.perform(post("/api/v1/auth/mock/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"alice@example.com\"}"));

        // Then:
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"));
    }
}
