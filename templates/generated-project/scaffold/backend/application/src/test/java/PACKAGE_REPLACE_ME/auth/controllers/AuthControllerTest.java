package PACKAGE_REPLACE_ME.auth.controllers;

import PACKAGE_REPLACE_ME.security.AppUserFactory;
import PACKAGE_REPLACE_ME.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth controller contract — exercises /api/v1/auth/me plus the public/private
 * path matrix from {@code SecurityConfig}. {@code @WebMvcTest} loads only the
 * web layer + the imported SSO security config + the real {@link AppUserFactory}.
 * A stub {@link JwtDecoder} satisfies SecurityConfig's
 * {@code @ConditionalOnMissingBean}; the {@code jwt()} post-processor injects
 * the authentication directly, so the decoder is never actually invoked.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, AppUserFactory.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

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
    void shouldRejectInvalidOrExpiredTokenTest() throws Exception {
        // Given: a presented token the decoder rejects. A real NimbusJwtDecoder
        // throws BadJwtException for a bad signature / expired / wrong-issuer
        // token (JwtValidationException extends it), which the resource-server
        // maps to InvalidBearerTokenException → 401.
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("invalid token"));

        // When:
        ResultActions response = mvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer not-a-real-token"));

        // Then: the resource-server chain validates the token and rejects it
        response.andExpect(status().isUnauthorized());
    }
}
