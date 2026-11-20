package com.skillstack.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimitPolicy {
    private final boolean enabled;
    private final int authPerMinute;
    private final int importPerMinute;
    private final int analysisPerMinute;
    private final int exportPerMinute;
    private final int publicReadPerMinute;

    public RateLimitPolicy(
            @Value("${skillstack.rate-limit.enabled:true}") boolean enabled,
            @Value("${skillstack.rate-limit.auth-per-minute:30}") int authPerMinute,
            @Value("${skillstack.rate-limit.import-per-minute:6}") int importPerMinute,
            @Value("${skillstack.rate-limit.analysis-per-minute:10}") int analysisPerMinute,
            @Value("${skillstack.rate-limit.export-per-minute:2}") int exportPerMinute,
            @Value("${skillstack.rate-limit.public-read-per-minute:120}") int publicReadPerMinute) {
        this.enabled = enabled;
        this.authPerMinute = clamp(authPerMinute);
        this.importPerMinute = clamp(importPerMinute);
        this.analysisPerMinute = clamp(analysisPerMinute);
        this.exportPerMinute = clamp(exportPerMinute);
        this.publicReadPerMinute = clamp(publicReadPerMinute);
    }

    private int clamp(int value) { return Math.max(1, Math.min(10_000, value)); }

    public boolean enabled() { return enabled; }
    public int limit(String category) {
        return switch (category) {
            case "auth" -> authPerMinute;
            case "import" -> importPerMinute;
            case "analysis" -> analysisPerMinute;
            case "export" -> exportPerMinute;
            case "public" -> publicReadPerMinute;
            default -> 1;
        };
    }
}
