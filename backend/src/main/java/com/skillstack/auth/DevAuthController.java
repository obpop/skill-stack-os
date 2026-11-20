package com.skillstack.auth;

import com.skillstack.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("!prod")
public class DevAuthController {
    private final UserService users;
    private final DevTokenService tokens;

    public DevAuthController(UserService users, DevTokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    public record DevLoginRequest(
            @Size(max = 120) String displayName,
            @Pattern(regexp = "[a-zA-Z0-9:_-]{3,200}") String returningSubject,
            boolean demo
    ) {}

    public record SessionResponse(String accessToken, String tokenType, long expiresIn, String subject, String displayName) {}

    @PostMapping("/dev-login")
    public SessionResponse login(@Valid @RequestBody DevLoginRequest request) {
        var subject = request.demo()
                ? "demo-user"
                : request.returningSubject() != null ? request.returningSubject() : "local:" + UUID.randomUUID();
        var displayName = request.demo()
                ? "Olli"
                : request.displayName() == null || request.displayName().isBlank() ? "Explorer" : request.displayName().trim();
        var user = users.ensureUser(subject, displayName);
        return new SessionResponse(tokens.issue(subject), "Bearer", 43200, subject, user.getDisplayName());
    }
}
