package com.skillstack.auth;

import com.skillstack.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionControllerTest {
    private final OAuth2AuthorizedClientService authorizedClients = mock(OAuth2AuthorizedClientService.class);
    private final OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
    private final AuthSessionController controller = new AuthSessionController(
            authorizedClients,
            "https://tenant.example",
            "client-id",
            "http://localhost:3000");

    @Test
    void returnsTheApiTokenAndCsrfTokenWithoutCaching() {
        var accessToken = accessToken(Instant.now().plusSeconds(300));
        register(accessToken);

        var response = controller.session(authentication,
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "csrf-token"));

        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isEqualTo("access-token");
        assertThat(response.getBody().csrfHeaderName()).isEqualTo("X-CSRF-TOKEN");
        assertThat(response.getBody().csrfToken()).isEqualTo("csrf-token");
    }

    @Test
    void rejectsAnExpiredApiToken() {
        register(accessToken(Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> controller.session(authentication,
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "csrf-token")))
                .isInstanceOfSatisfying(ApiException.class, problem -> {
                    assertThat(problem.status().value()).isEqualTo(401);
                    assertThat(problem.code()).isEqualTo("AUTH0_SESSION_EXPIRED");
                });
    }

    @Test
    void removesTheAuthorizedClientAndBuildsAnAuth0LogoutUrl() {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("auth0");
        when(authentication.getName()).thenReturn("auth0|user");

        var response = controller.logout(authentication, new MockHttpServletRequest(), new MockHttpServletResponse());

        verify(authorizedClients).removeAuthorizedClient("auth0", "auth0|user");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().logoutUrl())
                .startsWith("https://tenant.example/v2/logout?")
                .contains("client_id=client-id")
                .contains("returnTo=http://localhost:3000");
    }

    private void register(OAuth2AccessToken accessToken) {
        var client = mock(OAuth2AuthorizedClient.class);
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("auth0");
        when(authentication.getName()).thenReturn("auth0|user");
        when(client.getAccessToken()).thenReturn(accessToken);
        when(authorizedClients.loadAuthorizedClient("auth0", "auth0|user")).thenReturn(client);
    }

    private OAuth2AccessToken accessToken(Instant expiresAt) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token",
                Instant.now().minusSeconds(30), expiresAt, Set.of("openid", "profile"));
    }
}
