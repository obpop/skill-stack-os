package com.skillstack.profile;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record UpsertRequest(
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 120) String city,
            @Size(max = 120) String country,
            CareerRole currentRole,
            CareerRole targetRole,
            boolean buildOnTheSide,
            @Min(1) @Max(10) Short timelineYears,
            SkillDirection skillDirection,
            @Size(max = 2) List<UUID> focusSkillIds,
            WorkPreference workPreference,
            HelpTarget helpTarget,
            boolean complete
    ) {}

    public record Response(
            String displayName, String city, String country, CareerRole currentRole,
            CareerRole targetRole, boolean buildOnTheSide, Short timelineYears,
            SkillDirection skillDirection, List<UUID> focusSkillIds,
            WorkPreference workPreference, HelpTarget helpTarget,
            OffsetDateTime onboardingCompletedAt, boolean complete, long version
    ) {}
}
