package com.skillstack.publication;

import com.skillstack.common.ApiException;
import com.skillstack.graph.domain.GraphNode;
import com.skillstack.graph.domain.NodeType;
import com.skillstack.graph.persistence.GraphEdgeRepository;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.intelligence.IntelligenceDtos;
import com.skillstack.intelligence.RecommendationResultRepository;
import com.skillstack.project.domain.Project;
import com.skillstack.project.domain.ProofOfWork;
import com.skillstack.project.persistence.ProjectRepository;
import com.skillstack.project.persistence.ProofOfWorkRepository;
import com.skillstack.stack.SkillStackRules;
import com.skillstack.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublicationService {
    private static final Set<String> RESERVED_SLUGS = Set.of("admin", "api", "case-study", "demo", "login", "public", "share");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PublicProfileRepository profiles;
    private final PublicProfileShareRepository shares;
    private final TimelineEventRepository timeline;
    private final GraphNodeRepository nodes;
    private final GraphEdgeRepository edges;
    private final ProjectRepository projects;
    private final ProofOfWorkRepository proofs;
    private final RecommendationResultRepository recommendations;
    private final UserService users;
    private final TimelineService timelineService;
    private final ObjectMapper objectMapper;

    public PublicationService(PublicProfileRepository profiles, PublicProfileShareRepository shares,
                              TimelineEventRepository timeline, GraphNodeRepository nodes,
                              GraphEdgeRepository edges, ProjectRepository projects,
                              ProofOfWorkRepository proofs, RecommendationResultRepository recommendations,
                              UserService users, TimelineService timelineService, ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.shares = shares;
        this.timeline = timeline;
        this.nodes = nodes;
        this.edges = edges;
        this.projects = projects;
        this.proofs = proofs;
        this.recommendations = recommendations;
        this.users = users;
        this.timelineService = timelineService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PublicationDtos.OwnerResponse owner(String subject) {
        var profile = profiles.findByOwnerSubject(subject)
                .orElseGet(() -> profiles.save(new PublicProfile(users.require(subject), randomSlug())));
        return ownerResponse(subject, profile);
    }

    @Transactional
    public PublicationDtos.OwnerResponse update(String subject, PublicationDtos.UpdateRequest request) {
        var profile = profiles.findByOwnerSubject(subject)
                .orElseGet(() -> profiles.save(new PublicProfile(users.require(subject), randomSlug())));
        if (profile.getVersion() != request.version()) {
            throw ApiException.conflict("STALE_PUBLIC_PROFILE", "Publication settings changed in another session");
        }
        var slug = normalizeSlug(request.slug());
        if (RESERVED_SLUGS.contains(slug)) {
            throw ApiException.badRequest("RESERVED_PUBLIC_SLUG", "Choose a different public profile address");
        }
        if (profiles.existsBySlugAndOwnerSubjectNot(slug, subject)) {
            throw ApiException.conflict("PUBLIC_SLUG_TAKEN", "That public profile address is already in use");
        }
        requireDistinct(request.selectedSkillIds(), "skills");
        requireDistinct(request.selectedStrategyIds(), "strategies");
        requireDistinct(request.selectedProjectIds(), "projects");
        requireDistinct(request.selectedProofIds(), "proof entries");
        requireDistinct(request.selectedMilestoneIds(), "timeline entries");
        validateSelections(subject, request);
        profile.configure(slug, request.theme(), request.showDisplayName(), request.showSentence(),
                request.showSkillLevels(), request.showIntersections(), request.showNextSkill(),
                request.showProjects(), request.showProof(), request.showMilestones(), request.showRarity(),
                write(request.selectedSkillIds()), write(request.selectedStrategyIds()),
                write(request.selectedProjectIds()), write(request.selectedProofIds()),
                write(request.selectedMilestoneIds()));
        try {
            profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException duplicate) {
            throw ApiException.conflict("PUBLIC_SLUG_TAKEN", "That public profile address is already in use");
        }
        return ownerResponse(subject, profile);
    }

    @Transactional
    public PublicationDtos.OwnerResponse publish(String subject) {
        var profile = requireOwner(subject);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var view = buildView(subject, profile, now);
        if (view.skills().isEmpty()) {
            throw ApiException.badRequest("PUBLIC_SKILL_REQUIRED", "Select at least one skill before publishing");
        }
        profile.publish(write(view), now);
        profiles.saveAndFlush(profile);
        return ownerResponse(subject, profile);
    }

    @Transactional
    public PublicationDtos.OwnerResponse unpublish(String subject) {
        var profile = requireOwner(subject);
        profile.unpublish(OffsetDateTime.now(ZoneOffset.UTC));
        profiles.saveAndFlush(profile);
        return ownerResponse(subject, profile);
    }

    @Transactional
    public void invalidatePublished(String subject) {
        profiles.findByOwnerSubject(subject).filter(profile -> profile.getStatus() == PublicationStatus.PUBLISHED)
                .ifPresent(profile -> profile.unpublish(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional(readOnly = true)
    public PublicationDtos.PublicView publicBySlug(String slug) {
        var profile = profiles.findBySlugAndStatus(normalizeSlug(slug), PublicationStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("public profile"));
        return readView(profile.getPublicSnapshot());
    }

    @Transactional(readOnly = true)
    public PublicationDtos.PublicView publicByShareToken(String token) {
        var share = shares.findByTokenHashAndRevokedAtIsNull(hashToken(token))
                .orElseThrow(() -> ApiException.notFound("public share"));
        if (share.getProfile().getStatus() != PublicationStatus.PUBLISHED) {
            throw ApiException.notFound("public share");
        }
        return readView(share.getProfile().getPublicSnapshot());
    }

    @Transactional
    public PublicationDtos.ShareCreated createShare(String subject, PublicationDtos.CreateShareRequest request) {
        var profile = requireOwner(subject);
        if (profile.getStatus() != PublicationStatus.PUBLISHED) {
            throw ApiException.badRequest("PROFILE_NOT_PUBLISHED", "Publish the profile before creating a share link");
        }
        var token = randomToken();
        var label = request == null ? null : request.label();
        var share = shares.saveAndFlush(new PublicProfileShare(profile, hashToken(token), token.substring(token.length() - 6), label));
        return new PublicationDtos.ShareCreated(share.getId(), token, "/share/" + token,
                share.getLabel(), share.getCreatedAt());
    }

    @Transactional
    public void revokeShare(String subject, UUID id) {
        var share = shares.findByIdAndProfileOwnerSubject(id, subject)
                .orElseThrow(() -> ApiException.notFound("public share"));
        if (share.getRevokedAt() == null) share.revoke(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public PublicationDtos.OwnerResponse addTimeline(String subject, PublicationDtos.CreateTimelineRequest request) {
        timelineService.addManual(subject, request);
        return ownerResponse(subject, requireOwner(subject));
    }

    @Transactional
    public PublicationDtos.OwnerResponse deleteTimeline(String subject, UUID id) {
        var profile = requireOwner(subject);
        var selected = readIds(profile.getSelectedMilestoneIds());
        if (selected.contains(id)) {
            throw ApiException.badRequest("TIMELINE_EVENT_PUBLISHED", "Remove this event from publication settings before deleting it");
        }
        timelineService.deleteManual(subject, id);
        return ownerResponse(subject, profile);
    }

    private PublicationDtos.OwnerResponse ownerResponse(String subject, PublicProfile profile) {
        var events = timeline.findAllByOwnerSubjectOrderByCreatedAtDesc(subject);
        return new PublicationDtos.OwnerResponse(config(profile), buildView(subject, profile, profile.getPublishedAt()),
                events.stream().map(this::timelineResponse).toList(),
                shares.findAllByProfileOwnerSubjectOrderByCreatedAtDesc(subject).stream().map(this::shareSummary).toList(),
                "/public/" + profile.getSlug());
    }

    private PublicationDtos.PublicView buildView(String subject, PublicProfile profile, OffsetDateTime publishedAt) {
        var selectedSkillIds = readIds(profile.getSelectedSkillIds());
        var selectedSkills = selectedSkillIds.stream().map(id -> requireNode(subject, id, NodeType.SKILL)).toList();
        var allProjects = projects.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var projectNodeIds = allProjects.stream().map(project -> project.getGraphNode().getId()).collect(Collectors.toSet());
        var allEdges = edges.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var provenSkillIds = allEdges.stream()
                .filter(edge -> projectNodeIds.contains(edge.getSource().getId()) || projectNodeIds.contains(edge.getTarget().getId()))
                .map(edge -> projectNodeIds.contains(edge.getSource().getId()) ? edge.getTarget().getId() : edge.getSource().getId())
                .collect(Collectors.toSet());
        var skillViews = selectedSkills.stream().map(skill -> {
            var proven = provenSkillIds.contains(skill.getId());
            var level = profile.isShowSkillLevels()
                    ? (proven ? "PROVEN" : skill.getSkillLevel() == null ? null : skill.getSkillLevel().name())
                    : null;
            return new PublicationDtos.Skill(skill.getTitle(), level, profile.isShowSkillLevels() && proven);
        }).toList();

        var sentence = profile.isShowSentence() ? SkillStackRules.sentence(selectedSkills) : null;
        var strongCount = (int) selectedSkills.stream()
                .filter(skill -> SkillStackRules.isStrong(skill, provenSkillIds.contains(skill.getId()))).count();
        var rarity = profile.isShowRarity() ? SkillStackRules.rarity(strongCount) : null;
        var rarityExplanation = profile.isShowRarity() ? "Illustrative only. " + SkillStackRules.RARITY_CAVEAT : null;

        var latest = latestRecommendation(subject);
        var strategyIds = new LinkedHashSet<>(readStrings(profile.getSelectedStrategyIds()));
        var intersections = !profile.isShowIntersections() || latest.isEmpty() ? List.<PublicationDtos.Intersection>of()
                : latest.get().strategies().stream().filter(item -> strategyIds.contains(item.id())).map(item ->
                new PublicationDtos.Intersection(item.title(), item.summary(), item.tradeoff(), item.firstAction())).toList();
        var nextSkill = profile.isShowNextSkill() && latest.isPresent()
                ? new PublicationDtos.NextSkill(latest.get().nextSkill().title(), latest.get().nextSkill().whyNow(),
                latest.get().nextSkill().firstAction(), latest.get().nextSkill().evidenceOutcome()) : null;

        var selectedProjectIds = new LinkedHashSet<>(readIds(profile.getSelectedProjectIds()));
        var selectedProofIds = new LinkedHashSet<>(readIds(profile.getSelectedProofIds()));
        var allProofs = proofs.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var proofByProject = allProofs.stream().filter(proof -> selectedProofIds.contains(proof.getId()))
                .collect(Collectors.groupingBy(proof -> proof.getProject().getId()));
        var skillById = selectedSkills.stream().collect(Collectors.toMap(GraphNode::getId, Function.identity()));
        var projectViews = !profile.isShowProjects() ? List.<PublicationDtos.Project>of() : allProjects.stream()
                .filter(project -> selectedProjectIds.contains(project.getId())).map(project -> publicProject(
                        project, allEdges, skillById, proofByProject.getOrDefault(project.getId(), List.of()), profile.isShowProof())).toList();

        var selectedTimelineIds = new LinkedHashSet<>(readIds(profile.getSelectedMilestoneIds()));
        var timelineViews = !profile.isShowMilestones() ? List.<PublicationDtos.Timeline>of()
                : timeline.findAllByOwnerSubjectOrderByCreatedAtDesc(subject).stream()
                .filter(event -> selectedTimelineIds.contains(event.getId()))
                .sorted(Comparator.comparing(this::timelineSortDate).thenComparing(TimelineEvent::getCreatedAt))
                .map(this::publicTimeline).toList();

        var limitations = new ArrayList<String>();
        limitations.add("Skill levels are self-assessed unless described as project-backed inside Skill Stack.");
        limitations.add("Project-backed means linked evidence exists; it is not independent certification or a career guarantee.");
        if (profile.isShowRarity()) limitations.add("Rarity is an illustrative thought experiment, not a measured population percentile.");
        if (nextSkill != null) limitations.add("The development focus is decision support and may change as goals or evidence change.");

        return new PublicationDtos.PublicView(profile.getSlug(),
                profile.isShowDisplayName() ? profile.getOwner().getDisplayName() : null,
                profile.getTheme(), publishedAt, sentence, rarity, rarityExplanation,
                skillViews, intersections, nextSkill, projectViews, timelineViews,
                List.copyOf(limitations), "Skill Stack");
    }

    private PublicationDtos.Project publicProject(Project project, List<com.skillstack.graph.domain.GraphEdge> allEdges,
                                                   Map<UUID, GraphNode> selectedSkills, List<ProofOfWork> selectedProofs,
                                                   boolean showProof) {
        var applied = allEdges.stream()
                .filter(edge -> edge.getSource().getId().equals(project.getGraphNode().getId())
                        || edge.getTarget().getId().equals(project.getGraphNode().getId()))
                .map(edge -> edge.getSource().getId().equals(project.getGraphNode().getId())
                        ? edge.getTarget().getId() : edge.getSource().getId())
                .map(selectedSkills::get).filter(Objects::nonNull).map(GraphNode::getTitle).distinct().toList();
        var proofViews = showProof ? selectedProofs.stream().map(proof -> new PublicationDtos.Proof(
                proof.getTitle(), proof.getType().name(), proof.getDescription(), safeUrl(proof.getUrl()))).toList() : List.<PublicationDtos.Proof>of();
        return new PublicationDtos.Project(project.getTitle(), project.getDescription(), project.getStatus().name(),
                project.getStartedAt(), project.getCompletedAt(),
                showProof ? safeUrl(project.getRepositoryUrl()) : null,
                showProof ? safeUrl(project.getLiveUrl()) : null,
                applied, proofViews);
    }

    private void validateSelections(String subject, PublicationDtos.UpdateRequest request) {
        request.selectedSkillIds().forEach(id -> requireNode(subject, id, NodeType.SKILL));
        var ownedProjects = projects.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .map(Project::getId).collect(Collectors.toSet());
        if (!ownedProjects.containsAll(request.selectedProjectIds())) throw ApiException.notFound("project");
        var ownedProofs = proofs.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var proofById = ownedProofs.stream().collect(Collectors.toMap(ProofOfWork::getId, Function.identity()));
        if (!proofById.keySet().containsAll(request.selectedProofIds())) throw ApiException.notFound("proof");
        if (request.selectedProofIds().stream().map(proofById::get).anyMatch(proof -> !request.selectedProjectIds().contains(proof.getProject().getId()))) {
            throw ApiException.badRequest("PROOF_PROJECT_REQUIRED", "Selected proof must belong to a selected project");
        }
        var ownedTimeline = timeline.findAllByOwnerSubjectOrderByCreatedAtDesc(subject).stream()
                .map(TimelineEvent::getId).collect(Collectors.toSet());
        if (!ownedTimeline.containsAll(request.selectedMilestoneIds())) throw ApiException.notFound("timeline event");
        if (!request.selectedStrategyIds().isEmpty()) {
            var strategyIds = latestRecommendation(subject).map(value -> value.strategies().stream()
                    .map(IntelligenceDtos.StrategyOption::id).collect(Collectors.toSet())).orElse(Set.of());
            if (!strategyIds.containsAll(request.selectedStrategyIds())) {
                throw ApiException.badRequest("INVALID_PUBLIC_STRATEGY", "Choose strategies from the current recommendation");
            }
        }
    }

    private GraphNode requireNode(String subject, UUID id, NodeType type) {
        var node = nodes.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound(type.name().toLowerCase()));
        if (node.getType() != type) throw ApiException.badRequest("INVALID_PUBLIC_" + type.name(), "Choose only owned " + type.name().toLowerCase() + " records");
        return node;
    }

    private Optional<IntelligenceDtos.Recommendation> latestRecommendation(String subject) {
        return recommendations.findFirstByOwnerSubjectOrderByGeneratedAtDesc(subject).map(entity -> {
            try { return objectMapper.readValue(entity.getResultJson(), IntelligenceDtos.Recommendation.class); }
            catch (Exception problem) { throw new IllegalStateException("Stored recommendation is invalid", problem); }
        });
    }

    private PublicationDtos.Config config(PublicProfile profile) {
        return new PublicationDtos.Config(profile.getSlug(), profile.getTheme(), profile.getStatus(),
                profile.isShowDisplayName(), profile.isShowSentence(), profile.isShowSkillLevels(),
                profile.isShowIntersections(), profile.isShowNextSkill(), profile.isShowProjects(),
                profile.isShowProof(), profile.isShowMilestones(), profile.isShowRarity(),
                readIds(profile.getSelectedSkillIds()), readStrings(profile.getSelectedStrategyIds()),
                readIds(profile.getSelectedProjectIds()), readIds(profile.getSelectedProofIds()),
                readIds(profile.getSelectedMilestoneIds()), profile.getPublishedAt(), profile.getUnpublishedAt(),
                profile.getVersion());
    }

    private PublicationDtos.TimelineResponse timelineResponse(TimelineEvent event) {
        return new PublicationDtos.TimelineResponse(event.getId(), event.getType(), event.getTitle(), event.getDescription(),
                event.getOccurredOn(), event.getCreatedAt(), event.getDateSource(), event.getSourceType());
    }

    private PublicationDtos.Timeline publicTimeline(TimelineEvent event) {
        return new PublicationDtos.Timeline(event.getType().name(), event.getTitle(), event.getDescription(),
                event.getOccurredOn(), event.getCreatedAt(), event.getDateSource());
    }

    private PublicationDtos.ShareSummary shareSummary(PublicProfileShare share) {
        return new PublicationDtos.ShareSummary(share.getId(), share.getLabel(), share.getTokenHint(),
                share.getCreatedAt(), share.getRevokedAt());
    }

    private java.time.LocalDate timelineSortDate(TimelineEvent event) {
        return event.getOccurredOn() == null ? event.getCreatedAt().toLocalDate() : event.getOccurredOn();
    }

    private PublicProfile requireOwner(String subject) {
        return profiles.findByOwnerSubject(subject).orElseThrow(() -> ApiException.notFound("publication settings"));
    }

    private String randomSlug() { return "stack-" + UUID.randomUUID().toString().substring(0, 12); }

    private String randomToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeSlug(String slug) {
        if (slug == null) throw ApiException.notFound("public profile");
        var normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9](?:[a-z0-9-]{1,58}[a-z0-9])?$")) {
            throw ApiException.notFound("public profile");
        }
        return normalized;
    }

    private String hashToken(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) throw ApiException.notFound("public share");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception problem) {
            throw new IllegalStateException("Unable to hash public share token", problem);
        }
    }

    private String safeUrl(String value) {
        if (value == null) return null;
        try {
            var uri = java.net.URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private <T> void requireDistinct(List<T> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            throw ApiException.badRequest("DUPLICATE_PUBLIC_SELECTION", "Choose distinct " + label);
        }
    }

    private List<UUID> readIds(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception problem) { throw new IllegalStateException("Stored public selection is invalid", problem); }
    }

    private List<String> readStrings(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception problem) { throw new IllegalStateException("Stored public selection is invalid", problem); }
    }

    private PublicationDtos.PublicView readView(String json) {
        try { return objectMapper.readValue(json, PublicationDtos.PublicView.class); }
        catch (Exception problem) { throw new IllegalStateException("Stored public snapshot is invalid", problem); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception problem) { throw new IllegalStateException("Unable to serialize public profile", problem); }
    }
}
