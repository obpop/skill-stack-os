package com.skillstack.intelligence;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intelligence")
public class IntelligenceController {
    private final IntelligenceService intelligence;

    public IntelligenceController(IntelligenceService intelligence) { this.intelligence = intelligence; }

    @PostMapping("/recommendations")
    public IntelligenceDtos.Recommendation generate(Authentication authentication,
                                                      @Valid @RequestBody IntelligenceDtos.GenerateRequest request) {
        return intelligence.generate(authentication.getName(), request);
    }

    @GetMapping("/recommendations/{id}")
    public IntelligenceDtos.Recommendation get(Authentication authentication, @PathVariable UUID id) {
        return intelligence.get(authentication.getName(), id);
    }

    @GetMapping("/recommendations/latest")
    public ResponseEntity<IntelligenceDtos.Recommendation> latest(Authentication authentication) {
        return intelligence.latest(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping("/recommendations/{id}/decision")
    public IntelligenceDtos.Recommendation decide(Authentication authentication, @PathVariable UUID id,
                                                   @Valid @RequestBody IntelligenceDtos.DecisionRequest request) {
        return intelligence.decide(authentication.getName(), id, request);
    }

    @GetMapping("/status")
    public IntelligenceDtos.AiStatus status(Authentication authentication) { return intelligence.status(authentication.getName()); }
}

