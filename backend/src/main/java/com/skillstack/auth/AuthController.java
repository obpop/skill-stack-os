package com.skillstack.auth;

import com.skillstack.user.UserService;
import com.skillstack.user.AccountDataExportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService users;
    private final AccountDataExportService exports;

    public AuthController(UserService users, AccountDataExportService exports) {
        this.users = users;
        this.exports = exports;
    }

    public record MeResponse(UUID id, String subject, String displayName, boolean onboardingComplete, String defaultDomain) {}
    public record UpdatePreferencesRequest(
            @NotBlank @Size(max = 60)
            @Pattern(regexp = "engineering|ai|business|economics|ideas|culture|world|health|communication")
            String defaultDomain) {}

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        var user = users.ensureUser(authentication.getName(), suggestedDisplayName(authentication));
        return response(user);
    }

    @PostMapping("/onboarding/complete")
    public MeResponse completeOnboarding(Authentication authentication) {
        var user = users.completeOnboarding(authentication.getName());
        return response(user);
    }

    @PatchMapping("/preferences")
    public MeResponse updatePreferences(Authentication authentication, @Valid @RequestBody UpdatePreferencesRequest request) {
        return response(users.updateDefaultDomain(authentication.getName(), request.defaultDomain()));
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(Authentication authentication) {
        users.deleteAccount(authentication.getName());
    }

    @GetMapping("/account/export")
    public ResponseEntity<JsonNode> exportAccount(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"skill-stack-export.json\"")
                .body(exports.export(authentication.getName()));
    }

    private MeResponse response(com.skillstack.user.AppUser user) {
        return new MeResponse(user.getId(), user.getSubject(), user.getDisplayName(), user.isOnboardingComplete(), user.getDefaultDomain());
    }

    private String suggestedDisplayName(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return firstPresent(jwt.getToken().getClaimAsString("name"), jwt.getToken().getClaimAsString("nickname"),
                    jwt.getToken().getClaimAsString("email"), authentication.getName());
        }
        if (authentication.getPrincipal() instanceof OidcUser oidc) {
            return firstPresent(oidc.getFullName(), oidc.getNickName(), oidc.getEmail(), authentication.getName());
        }
        return authentication.getName();
    }

    private String firstPresent(String... values) {
        for (var value : values) if (value != null && !value.isBlank()) return value;
        return "Skill Stack user";
    }
}
