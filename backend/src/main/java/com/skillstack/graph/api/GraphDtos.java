package com.skillstack.graph.api;

import com.skillstack.graph.domain.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GraphDtos {
    private GraphDtos() {}

    public record Layout(Double x, Double y) {}

    public record NodeResponse(
            UUID id,
            String externalKey,
            NodeType type,
            String title,
            String description,
            String domain,
            short importance,
            short depth,
            NodeStatus status,
            Provenance provenance,
            boolean proofOfWork,
            SkillLevel level,
            boolean monetizable,
            LearnOrOutsource learnOrOutsource,
            boolean wouldDoForFree,
            String effectiveLevel,
            List<UUID> provenByProjectIds,
            short year,
            Layout layout,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            long version
    ) {
        public static NodeResponse from(GraphNode node) {
            return from(node, List.of());
        }

        public static NodeResponse from(GraphNode node, List<UUID> provenByProjectIds) {
            var effectiveLevel = node.getType() == NodeType.SKILL && !provenByProjectIds.isEmpty()
                    ? "PROVEN"
                    : node.getSkillLevel() == null ? null : node.getSkillLevel().name();
            return new NodeResponse(node.getId(), node.getExternalKey(), node.getType(), node.getTitle(), node.getDescription(),
                    node.getDomain(), node.getImportance(), node.getDepth(), node.getStatus(), node.getProvenance(),
                    node.isProofOfWork(), node.getSkillLevel(), node.isMonetizable(), node.getLearnOrOutsource(),
                    node.isWouldDoForFree(), effectiveLevel, List.copyOf(provenByProjectIds),
                    node.getOccurredYear() == null ? 2026 : node.getOccurredYear(),
                    new Layout(node.getLayoutX(), node.getLayoutY()), node.getCreatedAt(), node.getUpdatedAt(), node.getVersion());
        }
    }

    public record EdgeResponse(
            UUID id, UUID source, UUID target, RelationshipType type, BigDecimal strength,
            Provenance provenance, OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
    ) {
        public static EdgeResponse from(GraphEdge edge) {
            return new EdgeResponse(edge.getId(), edge.getSource().getId(), edge.getTarget().getId(), edge.getRelationshipType(),
                    edge.getStrength(), edge.getProvenance(), edge.getCreatedAt(), edge.getUpdatedAt(), edge.getVersion());
        }
    }

    public record ProjectSummary(UUID id, UUID graphNodeId, String title, String description, String status,
                                 LocalDate startedAt, LocalDate completedAt, String repositoryUrl, String liveUrl,
                                 List<UUID> conceptNodeIds, OffsetDateTime createdAt, OffsetDateTime updatedAt, long version) {}
    public record ProofSummary(UUID id, UUID projectId, String title, String type, String description, String url) {}
    public record IntersectionSummary(UUID id, String definitionKey, String title, String decision, List<UUID> contributingNodeIds, UUID resultingProjectId) {}
    public record UserSummary(UUID id, String subject, String displayName, boolean onboardingComplete, String defaultDomain) {}
    public record GraphMetadata(int nodeCount, int edgeCount, OffsetDateTime generatedAt, String source) {}
    public record GraphResponse(UserSummary user, List<NodeResponse> nodes, List<EdgeResponse> edges,
                                List<ProjectSummary> projects, List<ProofSummary> proofs,
                                List<IntersectionSummary> intersections, GraphMetadata metadata) {}

    public record CreateNodeRequest(
            @NotNull NodeType type,
            @NotBlank @Size(max = 180) String title,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 60) @Pattern(regexp = "engineering|ai|business|economics|ideas|culture|world|health|communication") String domain,
            @Min(1) @Max(10) short importance,
            @Min(1) @Max(10) short depth,
            @NotNull NodeStatus status,
            SkillLevel level,
            boolean monetizable,
            LearnOrOutsource learnOrOutsource,
            boolean wouldDoForFree
    ) {}

    public record UpdateNodeRequest(
            @NotNull NodeType type,
            @NotBlank @Size(max = 180) String title,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 60) @Pattern(regexp = "engineering|ai|business|economics|ideas|culture|world|health|communication") String domain,
            @Min(1) @Max(10) short importance,
            @Min(1) @Max(10) short depth,
            @NotNull NodeStatus status,
            SkillLevel level,
            boolean monetizable,
            LearnOrOutsource learnOrOutsource,
            boolean wouldDoForFree,
            @PositiveOrZero long version
    ) {}

    public record CreateEdgeRequest(
            @NotNull UUID source,
            @NotNull UUID target,
            @NotNull RelationshipType type,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") BigDecimal strength
    ) {}

    public record UpdateEdgeRequest(
            @NotNull RelationshipType type,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") BigDecimal strength,
            @PositiveOrZero long version
    ) {}

    public record SearchResponse(List<NodeResponse> nodes, Map<String, Object> metadata) {}
}
