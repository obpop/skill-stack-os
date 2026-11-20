package com.skillstack.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
@Profile("!prod")
public class DevTokenService {
    private final byte[] secret;

    public DevTokenService(@Value("${skillstack.dev-token-secret}") String secret) {
        if (secret.length() < 32) throw new IllegalStateException("DEV_TOKEN_SECRET must contain at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(String subject) {
        var payload = subject + "|" + Instant.now().plusSeconds(12 * 60 * 60).getEpochSecond();
        var encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public String verify(String token) {
        var parts = token.split("\\.", -1);
        if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) throw new IllegalArgumentException("Invalid bearer token");
        var payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
        if (payload.length != 2 || Instant.now().getEpochSecond() >= Long.parseLong(payload[1])) throw new IllegalArgumentException("Expired bearer token");
        if (!payload[0].matches("[a-zA-Z0-9:_-]{3,200}")) throw new IllegalArgumentException("Invalid subject");
        return payload[0];
    }

    private String sign(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign local token", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
}
