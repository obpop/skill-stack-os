package com.skillstack.project.api;

import com.skillstack.project.domain.ProjectStatus;
import com.skillstack.project.domain.ProofType;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateProjectRequest(
            @NotBlank @Size(max = 180) String title,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 60) @Pattern(regexp = "engineering|ai|business|economics|ideas|culture|world|health|communication") String domain,
            @Min(1) @Max(10) short importance,
            @NotNull ProjectStatus status,
            @Size(max = 12) List<UUID> conceptNodeIds
    ) {}

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 180) String title,
            @Size(max = 2000) String description,
            @NotNull ProjectStatus status,
            LocalDate startedAt,
            LocalDate completedAt,
            @URL @Pattern(regexp = "^https?://.+") @Size(max = 1000) String repositoryUrl,
            @URL @Pattern(regexp = "^https?://.+") @Size(max = 1000) String liveUrl,
            @PositiveOrZero long version
    ) {}

    public record ProjectResponse(
            UUID id, UUID graphNodeId, String title, String description, ProjectStatus status,
            LocalDate startedAt, LocalDate completedAt, String repositoryUrl, String liveUrl,
            List<UUID> conceptNodeIds, OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
    ) {}

    public record CreateProofRequest(
            @NotBlank @Size(max = 180) String title,
            @NotNull ProofType type,
            @Size(max = 2000) String description,
            @URL @Pattern(regexp = "^https?://.+") @Size(max = 1000) String url
    ) {}

    public record ProofResponse(
            UUID id, UUID projectId, String title, ProofType type, String description, String url,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
    ) {}
}
