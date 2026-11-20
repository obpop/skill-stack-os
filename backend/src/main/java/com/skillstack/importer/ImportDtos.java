package com.skillstack.importer;

import com.skillstack.graph.domain.NodeType;
import com.skillstack.graph.domain.SkillLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ImportDtos {
    private ImportDtos() {}
    public record Proposal(String id, NodeType type, String title, SkillLevel suggestedLevel,
                           String evidence, String provenance, String uncertainty, boolean duplicate) {}
    public record Job(UUID id, ImportJob.SourceType sourceType, ImportJob.Status status,
                      List<Proposal> proposals, String errorCode, OffsetDateTime createdAt,
                      OffsetDateTime updatedAt, OffsetDateTime acceptedAt) {}
    public record AcceptedItem(
            @NotBlank String id,
            @NotBlank @Size(max = 180) String title,
            @NotNull NodeType type,
            SkillLevel level,
            boolean monetizable,
            boolean wouldDoForFree
    ) {}
    public record AcceptRequest(@NotNull @Size(max = 40) List<@Valid AcceptedItem> items) {}
    public record AcceptResponse(UUID jobId, int added, int merged, List<UUID> nodeIds, boolean alreadyAccepted) {}
}
