package com.skillstack.profile;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    public ProfileDtos.Response get(Authentication authentication) { return profiles.get(authentication.getName()); }

    @PutMapping
    public ProfileDtos.Response upsert(Authentication authentication, @Valid @RequestBody ProfileDtos.UpsertRequest request) {
        return profiles.upsert(authentication.getName(), request);
    }
}
