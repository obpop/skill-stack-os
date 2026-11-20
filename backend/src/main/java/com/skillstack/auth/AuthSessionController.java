package com.skillstack.auth;

import com.skillstack.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

@RestController
@Profile("prod")
@RequestMapping("/api/v1/auth/session")
public class AuthSessionController {
    private final OAuth2AuthorizedClientService authorizedClients;
    private final String issuerUri;
    private final String clientId;
    private final String frontendUri;

    public AuthSessionController(
            OAuth2AuthorizedClientService authorizedClients,
            @Value("${AUTH0_ISSUER_URI}") String issuerUri,
            @Value("${AUTH0_CLIENT_ID}") String clientId,
            @Value("${skillstack.auth.frontend-uri}") String frontendUri) {
        this.authorizedClients = authorizedClients;
        this.issuerUri = issuerUri.endsWith("/") ? issuerUri : issuerUri + "/";
        this.clientId = clientId;
        this.frontendUri = frontendUri;
    }

    public record SessionResponse(String accessToken, Instant expiresAt, String csrfHeaderName, String csrfToken) {}
    public record LogoutResponse(String logoutUrl) {}

    @GetMapping
    public ResponseEntity<SessionResponse> session(OAuth2AuthenticationToken authentication, CsrfToken csrfToken) {
        var client = authorizedClients.loadAuthorizedClient(authentication.getAuthorizedClientRegistrationId(), authentication.getName());
        if (client == null || client.getAccessToken() == null || isExpired(client.getAccessToken().getExpiresAt())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH0_SESSION_EXPIRED", "Sign in with Auth0 again");
        }
        var token = client.getAccessToken();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(new SessionResponse(token.getTokenValue(), token.getExpiresAt(), csrfToken.getHeaderName(), csrfToken.getToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        authorizedClients.removeAuthorizedClient(authentication.getAuthorizedClientRegistrationId(), authentication.getName());
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);

        var logoutUrl = UriComponentsBuilder.fromUriString(issuerUri)
                .path("v2/logout")
                .queryParam("client_id", clientId)
                .queryParam("returnTo", frontendUri)
                .build()
                .encode()
                .toUriString();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new LogoutResponse(logoutUrl));
    }

    private boolean isExpired(Instant expiresAt) {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }
}
