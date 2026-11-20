package com.skillstack.intelligence;

import com.skillstack.common.ApiException;
import com.skillstack.graph.domain.GraphNode;
import com.skillstack.graph.domain.NodeType;
import com.skillstack.graph.persistence.GraphEdgeRepository;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.profile.UserProfile;
import com.skillstack.profile.UserProfileRepository;
import com.skillstack.project.persistence.ProjectRepository;
import com.skillstack.publication.PublicationService;
import com.skillstack.publication.TimelineService;
import com.skillstack.stack.StackDtos;
import com.skillstack.stack.StackService;
import com.skillstack.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class IntelligenceService {
    public enum GenerationStage { COMPARING_COMBINATIONS, CHECKING_CATALOG, PREPARING_RECOMMENDATIONS }
    private static final String SCHEMA_VERSION = "phase3-v1";
    private static final int MAX_SKILLS = 3;
    private static final int MAX_INTERESTS = 5;
    private static final int MAX_RESOURCE_IDS = 12;

    private final GraphNodeRepository nodes;
    private final GraphEdgeRepository edges;
    private final ProjectRepository projects;
    private final UserProfileRepository profiles;
    private final RecommendationResultRepository results;
    private final AiOperationRepository operations;
    private final StackService stacks;
    private final UserService users;
    private final ObjectMapper objectMapper;
    private final AiProvider aiProvider;
    private final AiPolicy aiPolicy;
    private final AiCostGate aiCostGate;
    private final AiProviderExecutor aiExecutor;
    private final AiReservationService aiReservations;
    private final AiTelemetry aiTelemetry;
    private final TimelineService timeline;
    private final PublicationService publication;

    public IntelligenceService(GraphNodeRepository nodes, GraphEdgeRepository edges, ProjectRepository projects,
                               UserProfileRepository profiles,
                               RecommendationResultRepository results, AiOperationRepository operations,
                               StackService stacks, UserService users, ObjectMapper objectMapper,
                               AiProvider aiProvider, AiPolicy aiPolicy, AiCostGate aiCostGate,
                               AiProviderExecutor aiExecutor, AiReservationService aiReservations,
                               AiTelemetry aiTelemetry, TimelineService timeline, PublicationService publication) {
        this.nodes = nodes;
        this.edges = edges;
        this.projects = projects;
        this.profiles = profiles;
        this.results = results;
        this.operations = operations;
        this.stacks = stacks;
        this.users = users;
        this.objectMapper = objectMapper;
        this.aiProvider = aiProvider;
        this.aiPolicy = aiPolicy;
        this.aiCostGate = aiCostGate;
        this.aiExecutor = aiExecutor;
        this.aiReservations = aiReservations;
        this.aiTelemetry = aiTelemetry;
        this.timeline = timeline;
        this.publication = publication;
    }

    @Transactional
    public IntelligenceDtos.Recommendation generate(String subject, IntelligenceDtos.GenerateRequest request) {
        return generateInternal(subject, request, () -> true, stage -> {});
    }

    @Transactional
    public IntelligenceDtos.Recommendation generateForJob(String subject, IntelligenceDtos.GenerateRequest request,
                                                           BooleanSupplier publishAllowed) {
        return generateInternal(subject, request, publishAllowed, stage -> {});
    }

    @Transactional
    public IntelligenceDtos.Recommendation generateForJob(String subject, IntelligenceDtos.GenerateRequest request,
                                                           BooleanSupplier publishAllowed,
                                                           Consumer<GenerationStage> progress) {
        return generateInternal(subject, request, publishAllowed, progress);
    }

    private IntelligenceDtos.Recommendation generateInternal(String subject, IntelligenceDtos.GenerateRequest request,
                                                               BooleanSupplier publishAllowed,
                                                               Consumer<GenerationStage> progress) {
        if (!publishAllowed.getAsBoolean()) throw new GenerationSuppressedException();
        var all = nodes.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var profile = profiles.findByUserSubject(subject).orElse(null);
        var selected = selectSkills(subject, request.skillIds(), all, profile);
        var interests = all.stream().filter(node -> node.getType() == NodeType.INTEREST)
                .sorted(Comparator.comparing(GraphNode::isWouldDoForFree).reversed()
                        .thenComparingInt(GraphNode::getImportance).reversed()
                        .thenComparing(GraphNode::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_INTERESTS).toList();
        progress.accept(GenerationStage.COMPARING_COMBINATIONS);
        progress.accept(GenerationStage.CHECKING_CATALOG);
        var develop = stacks.develop(subject);
        var resourceIds = verifiedResourceIds(develop);
        var provenSkillIds = provenSkillIds(subject);
        var selectionKey = selected.stream().map(node -> node.getId().toString()).sorted().reduce((a, b) -> a + "," + b).orElse("none");
        var fingerprint = fingerprint(selected, interests, profile, resourceIds, provenSkillIds);
        var existing = results.findByOwnerSubjectAndInputFingerprintAndSelectionKey(subject, fingerprint, selectionKey);
        if (!request.refresh() && existing.isPresent()) {
            aiTelemetry.cacheHit();
            return response(existing.get(), true);
        }

        var rejected = rejectedStrategies(subject);
        var signals = selected.stream().map(node -> new RecommendationPlanner.SkillSignal(
                node.getId(), node.getTitle(), node.getDomain(), node.getSkillLevel() == null ? "LEARNING" : node.getSkillLevel().name(),
                node.getImportance(), node.isMonetizable(), provenSkillIds.contains(node.getId())
        )).toList();
        var profileSignal = profile == null ? null : new RecommendationPlanner.ProfileSignal(
                profile.getCurrentRole(), profile.getTargetRole(), profile.getSkillDirection(), profile.getTimelineYears(),
                profile.isBuildOnTheSide(), profile.getWorkPreference() == null ? null : profile.getWorkPreference().name(),
                profile.getHelpTarget() == null ? null : profile.getHelpTarget().name()
        );
        var deterministicStrategies = RecommendationPlanner.strategies(signals, profileSignal, develop.nextSkill().title(), rejected);
        if (deterministicStrategies.isEmpty()) throw ApiException.badRequest("SKILL_REQUIRED", "Add at least one skill before requesting intelligence");
        progress.accept(GenerationStage.PREPARING_RECOMMENDATIONS);

        var recommendationId = existing.map(RecommendationResult::getId).orElseGet(UUID::randomUUID);
        var generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        var primary = deterministicStrategies.stream().filter(IntelligenceDtos.StrategyOption::recommended).findFirst().orElse(deterministicStrategies.getFirst());
        var why = why(selected, interests, profile, resourceIds, primary);
        var nextSkill = nextSkill(develop, selected);
        var development = development(develop);
        var contextCharacters = boundedContextCharacters(selected, interests, profile, resourceIds, deterministicStrategies);
        var engine = IntelligenceDtos.Engine.DETERMINISTIC;
        var headline = primary.title() + ": " + develop.nextSkill().title();
        var briefSummary = primary.summary();
        UUID operationId = null;
        AiProvider.AiResponse aiResponse = null;
        String aiError = null;

        var aiRequest = new AiProvider.AiRequest(
                fingerprint, selected.stream().map(GraphNode::getId).toList(),
                selected.stream().map(this::skillFact).toList(), profileFacts(profile), resourceIds,
                deterministicStrategies, develop.nextSkill().title(), 4_000
        );
        operationId = aiReservations.reserve(subject, aiProvider, "CANONICAL_RECOMMENDATION",
                fingerprint, write(resourceIds)).orElse(null);
        if (operationId == null) aiTelemetry.notAdmitted(aiCostGate.state(subject, aiProvider));
        if (operationId != null) {
            if (!publishAllowed.getAsBoolean()) {
                aiReservations.cancel(operationId, "GENERATION_SUPPRESSED");
                throw new GenerationSuppressedException();
            }
            var concurrentResult = results.findByOwnerSubjectAndInputFingerprintAndSelectionKey(subject, fingerprint, selectionKey);
            if (!request.refresh() && concurrentResult.isPresent()) {
                aiReservations.cancel(operationId, "CACHE_FILLED_WHILE_WAITING");
                aiTelemetry.cacheHit();
                return response(concurrentResult.get(), true);
            }
            try {
                aiTelemetry.attempted();
                aiResponse = AiResponseValidator.validate(aiExecutor.analyze(aiProvider, aiRequest), aiRequest);
                aiTelemetry.succeeded(aiResponse);
                engine = IntelligenceDtos.Engine.AI_ASSISTED;
                headline = aiResponse.headline();
                briefSummary = aiResponse.summary();
                nextSkill = new IntelligenceDtos.NextSkill(nextSkill.title(), nextSkill.ownedSkillId(), aiResponse.whyNow(),
                        nextSkill.alternatives(), nextSkill.levers(), nextSkill.firstAction(), nextSkill.evidenceOutcome());
                var summaries = aiResponse.strategySummaries();
                deterministicStrategies = deterministicStrategies.stream().map(item -> new IntelligenceDtos.StrategyOption(
                        item.id(), item.strategy(), item.title(), item.recommended(), summaries.getOrDefault(item.id(), item.summary()),
                        item.contributions(), item.synergy(), item.plausibleApplications(), item.limitations(), item.missingCapability(),
                        item.tradeoff(), item.firstAction(), item.evidence()
                )).toList();
            } catch (RuntimeException problem) {
                engine = IntelligenceDtos.Engine.AI_FALLBACK;
                aiError = problem instanceof AiProviderExecutor.AiProviderFailure failure ? failure.code() : "AI_OUTPUT_REJECTED";
                aiTelemetry.failed(aiError);
            }
        }

        if (!publishAllowed.getAsBoolean()) {
            aiReservations.cancel(operationId, "GENERATION_SUPPRESSED");
            throw new GenerationSuppressedException();
        }

        var provenance = new IntelligenceDtos.Provenance(
                SCHEMA_VERSION, aiProvider.providerId(), aiProvider.modelId(), operationId, false,
                Math.min(contextCharacters, aiPolicy.maxInputCharacters()),
                selected.size() + interests.size() + resourceIds.size(), aiCostGate.state(subject, aiProvider)
        );
        var canonical = new IntelligenceDtos.Recommendation(
                recommendationId, generatedAt, fingerprint, engine, primary.id(), headline, briefSummary,
                selected.stream().map(GraphNode::getId).toList(), deterministicStrategies, nextSkill, development, why,
                request.refresh() ? IntelligenceDtos.Decision.UNREVIEWED : existing.map(RecommendationResult::getDecision).orElse(IntelligenceDtos.Decision.UNREVIEWED),
                request.refresh() ? null : existing.map(RecommendationResult::getSelectedStrategyId).orElse(null), provenance
        );
        var json = write(canonical);
        RecommendationResult entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.replace(engine, primary.strategy(), json, generatedAt);
        } else {
            entity = new RecommendationResult(recommendationId, users.ensureUser(subject, subject), fingerprint,
                    selectionKey, engine, primary.strategy(), json, generatedAt);
        }
        try {
            entity = results.saveAndFlush(entity);
        } catch (DataIntegrityViolationException duplicate) {
            entity = results.findByOwnerSubjectAndInputFingerprintAndSelectionKey(subject, fingerprint, selectionKey).orElseThrow();
            return response(entity, true);
        }
        if (operationId != null) persistOperation(operationId, entity, aiResponse, aiError);
        return response(entity, false);
    }

    @Transactional(readOnly = true)
    public IntelligenceDtos.Recommendation get(String subject, UUID id) {
        return response(require(subject, id), true);
    }

    @Transactional(readOnly = true)
    public Optional<IntelligenceDtos.Recommendation> latest(String subject) {
        return results.findFirstByOwnerSubjectOrderByGeneratedAtDesc(subject)
                .map(entity -> response(entity, true));
    }

    @Transactional
    public IntelligenceDtos.Recommendation decide(String subject, UUID id, IntelligenceDtos.DecisionRequest request) {
        var entity = require(subject, id);
        var current = read(entity.getResultJson());
        if (request.selectedStrategyId() != null && current.strategies().stream().noneMatch(item -> item.id().equals(request.selectedStrategyId()))) {
            throw ApiException.badRequest("INVALID_STRATEGY", "Choose a strategy from this recommendation");
        }
        if (request.decision() == IntelligenceDtos.Decision.MODIFIED && request.selectedStrategyId() == null) {
            throw ApiException.badRequest("STRATEGY_REQUIRED", "A modified decision must select one of the compared strategies");
        }
        entity.decide(request.decision(), request.selectedStrategyId());
        var saved = results.saveAndFlush(entity);
        if (request.decision() == IntelligenceDtos.Decision.SAVED || request.decision() == IntelligenceDtos.Decision.MODIFIED) {
            timeline.recordNextSkill(subject, saved.getId(), current.nextSkill().title());
        }
        publication.invalidatePublished(subject);
        return response(saved, true);
    }

    public IntelligenceDtos.AiStatus status(String subject) {
        var state = aiCostGate.state(subject, aiProvider);
        var spend = aiCostGate.spend();
        return new IntelligenceDtos.AiStatus(aiPolicy.enabled(), aiProvider.providerId(), aiProvider.modelId(),
                aiPolicy.monthlyBudgetUsd(), aiPolicy.totalBudgetUsd(), aiPolicy.maxCallsPerRequest(), aiPolicy.maxInputCharacters(),
                aiPolicy.maxCallsPerUserMonth(), aiPolicy.maxEstimatedCostPerCallUsd(), state,
                spend.monthlyReservedUsd().doubleValue(), spend.totalReservedUsd().doubleValue(), spend.totalEstimatedUsd().doubleValue());
    }

    private List<GraphNode> selectSkills(String subject, List<UUID> requested, List<GraphNode> all, UserProfile profile) {
        var ids = requested == null ? List.<UUID>of() : requested.stream().distinct().toList();
        if (requested != null && ids.size() != requested.size()) throw ApiException.badRequest("DUPLICATE_SKILL", "Choose distinct skills");
        if (ids.size() > MAX_SKILLS) throw ApiException.badRequest("TOO_MANY_SKILLS", "Choose at most three skills");
        if (ids.isEmpty() && profile != null) ids = readIds(profile.getFocusSkillIds()).stream().limit(MAX_SKILLS).toList();
        if (ids.isEmpty()) return all.stream().filter(node -> node.getType() == NodeType.SKILL)
                .sorted(Comparator.comparingInt(GraphNode::getImportance).reversed().thenComparing(GraphNode::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_SKILLS).toList();
        return ids.stream().map(id -> {
            var node = nodes.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("skill"));
            if (node.getType() != NodeType.SKILL) throw ApiException.badRequest("INVALID_SKILL", "Intelligence inputs must be owned skills");
            return node;
        }).toList();
    }

    private Set<IntelligenceDtos.Strategy> rejectedStrategies(String subject) {
        var rejected = EnumSet.noneOf(IntelligenceDtos.Strategy.class);
        for (var entity : results.findTop20ByOwnerSubjectOrderByGeneratedAtDesc(subject)) {
            if (entity.getDecision() == IntelligenceDtos.Decision.REJECTED || entity.getDecision() == IntelligenceDtos.Decision.HIDDEN) {
                rejected.add(entity.getPrimaryStrategy());
            }
        }
        return rejected;
    }

    private IntelligenceDtos.NextSkill nextSkill(StackDtos.Develop develop, List<GraphNode> selected) {
        var current = develop.nextSkill();
        var ownedId = selected.stream().filter(node -> node.getTitle().equalsIgnoreCase(current.title())).map(GraphNode::getId).findFirst().orElse(null);
        var anchor = selected.getFirst().getTitle();
        var alternatives = new ArrayList<IntelligenceDtos.NextSkillAlternative>();
        alternatives.add(new IntelligenceDtos.NextSkillAlternative(
                "Deepen " + anchor, "Faster to apply because it builds on an owned skill, but adds less breadth.", "Low"
        ));
        if (selected.size() > 1) alternatives.add(new IntelligenceDtos.NextSkillAlternative(
                "Evidence design", "Makes the existing combination easier to prove, but may not close the capability gap directly.", "Medium"
        ));
        var whyNow = String.join(" ", current.reasons());
        var firstAction = "Use " + current.title() + " in one week-sized exercise connected to " + anchor + ".";
        var evidenceOutcome = "An inspectable artifact plus a short note on what changed, failed, and should be tried next.";
        return new IntelligenceDtos.NextSkill(current.title(), ownedId, whyNow, alternatives.stream().limit(2).toList(),
                current.levers(), firstAction, evidenceOutcome);
    }

    private IntelligenceDtos.Development development(StackDtos.Develop develop) {
        var education = develop.education().stream().limit(6).map(StackDtos.Recommendation::id).toList();
        var networks = develop.networks().stream().limit(6).map(StackDtos.Recommendation::id).toList();
        var moves = develop.moves().stream().limit(4).map(item -> item.title() + ": " + item.reason()).toList();
        var build = develop.build().isEmpty() ? "Build a small proof-oriented project." : develop.build().getFirst().reason();
        var first = develop.build().isEmpty() ? "Define one observable deliverable." : develop.build().getFirst().title() + ": " + develop.build().getFirst().reason();
        return new IntelligenceDtos.Development(education, networks, moves, build, first);
    }

    private IntelligenceDtos.Why why(List<GraphNode> selected, List<GraphNode> interests, UserProfile profile,
                                      List<String> resourceIds, IntelligenceDtos.StrategyOption primary) {
        var evidence = new ArrayList<IntelligenceDtos.EvidenceRef>(primary.evidence());
        interests.stream().filter(GraphNode::isWouldDoForFree).limit(2).forEach(interest -> evidence.add(new IntelligenceDtos.EvidenceRef(
                "USER_FACT", "INTEREST", interest.getId().toString(), interest.getTitle(), "You marked this as something you would do for free"
        )));
        resourceIds.forEach(id -> evidence.add(new IntelligenceDtos.EvidenceRef(
                "CURATED_VERIFIED_RESOURCE", "DEVELOPMENT_ITEM", id, id, "Matched from the verified Phase 2.5 catalog"
        )));
        var fields = new ArrayList<String>();
        if (profile != null) {
            if (profile.getCurrentRole() != null) fields.add("currentRole");
            if (profile.getTargetRole() != null) fields.add("targetRole");
            if (profile.getSkillDirection() != null) fields.add("skillDirection");
            if (profile.getTimelineYears() != null) fields.add("timelineYears");
            if (profile.getWorkPreference() != null) fields.add("workPreference");
        }
        return new IntelligenceDtos.Why(evidence, fields,
                List.of("Self-assessed levels and payability are user beliefs, not external certification or demand data.",
                        "The recommendation assumes a small project is feasible within the stated direction."),
                List.of(primary.tradeoff(), "A depth path may be preferable if the proposed complement cannot be practiced soon."),
                "VERIFIED_CATALOG_ONLY",
                List.of("Selected owned skills", "Up to five owned interests", "Relevant profile fields", "Up to twelve verified catalog IDs"));
    }

    private List<String> verifiedResourceIds(StackDtos.Develop develop) {
        return java.util.stream.Stream.concat(develop.education().stream(), develop.networks().stream())
                .filter(item -> item.url() != null).map(StackDtos.Recommendation::id).distinct().limit(MAX_RESOURCE_IDS).toList();
    }

    private String fingerprint(List<GraphNode> selected, List<GraphNode> interests, UserProfile profile,
                               List<String> resourceIds, Set<UUID> provenSkillIds) {
        var parts = new ArrayList<String>();
        parts.add(SCHEMA_VERSION);
        selected.stream().sorted(Comparator.comparing(GraphNode::getId)).forEach(node -> parts.add(skillFact(node) + ":v" + node.getVersion()));
        interests.stream().sorted(Comparator.comparing(GraphNode::getId)).forEach(node -> parts.add("interest:" + node.getId() + ":" + node.getTitle() + ":" + node.isWouldDoForFree() + ":v" + node.getVersion()));
        if (profile != null) parts.add("profile:" + profile.getVersion() + ":" + profile.getCurrentRole() + ":" + profile.getTargetRole() + ":" + profile.getSkillDirection() + ":" + profile.getTimelineYears() + ":" + profile.getWorkPreference());
        parts.add("project-backed:" + provenSkillIds.stream().sorted().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));
        parts.add("catalog:" + String.join(",", resourceIds));
        return sha256(String.join("|", parts));
    }

    private Set<UUID> provenSkillIds(String subject) {
        var projectNodeIds = projects.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .map(project -> project.getGraphNode().getId()).collect(java.util.stream.Collectors.toSet());
        return edges.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .filter(edge -> projectNodeIds.contains(edge.getSource().getId()) || projectNodeIds.contains(edge.getTarget().getId()))
                .map(edge -> projectNodeIds.contains(edge.getSource().getId()) ? edge.getTarget() : edge.getSource())
                .filter(node -> node.getType() == NodeType.SKILL).map(GraphNode::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String skillFact(GraphNode node) {
        return "skill:" + node.getId() + ":" + node.getTitle() + ":" + node.getDomain() + ":" + node.getSkillLevel() + ":" + node.getImportance() + ":" + node.isMonetizable();
    }

    private List<String> profileFacts(UserProfile profile) {
        if (profile == null) return List.of();
        return List.of("currentRole=" + profile.getCurrentRole(), "targetRole=" + profile.getTargetRole(),
                "direction=" + profile.getSkillDirection(), "timelineYears=" + profile.getTimelineYears(),
                "workPreference=" + profile.getWorkPreference(), "buildOnTheSide=" + profile.isBuildOnTheSide());
    }

    private int boundedContextCharacters(List<GraphNode> selected, List<GraphNode> interests, UserProfile profile,
                                         List<String> resourceIds, List<IntelligenceDtos.StrategyOption> strategies) {
        var size = selected.stream().mapToInt(node -> skillFact(node).length()).sum()
                + interests.stream().mapToInt(node -> node.getTitle().length() + 40).sum()
                + profileFacts(profile).stream().mapToInt(String::length).sum()
                + resourceIds.stream().mapToInt(String::length).sum()
                + strategies.stream().mapToInt(item -> item.summary().length()).sum();
        return Math.min(size, aiPolicy.maxInputCharacters());
    }

    private void persistOperation(UUID id, RecommendationResult result, AiProvider.AiResponse response, String errorCode) {
        var operation = operations.findById(id).orElseThrow();
        if (response == null) operation.failed(result, errorCode == null ? "AI_PROVIDER_FAILED" : errorCode);
        else operation.succeeded(result, response);
        operations.save(operation);
    }

    private RecommendationResult require(String subject, UUID id) {
        return results.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("recommendation"));
    }

    private IntelligenceDtos.Recommendation response(RecommendationResult entity, boolean cacheHit) {
        var stored = read(entity.getResultJson());
        var p = stored.provenance();
        var provenance = new IntelligenceDtos.Provenance(p.schemaVersion(), p.provider(), p.model(), p.operationId(), cacheHit,
                p.boundedContextCharacters(), p.sourceCount(), p.costState());
        return new IntelligenceDtos.Recommendation(stored.recommendationId(), entity.getGeneratedAt(), stored.inputVersion(),
                entity.getEngine(), stored.recommendedStrategyId(), stored.headline(), stored.summary(), stored.contributingSkillIds(),
                stored.strategies(), stored.nextSkill(), stored.development(), stored.why(), entity.getDecision(), entity.getSelectedStrategyId(), provenance);
    }

    private IntelligenceDtos.Recommendation read(String json) {
        try { return objectMapper.readValue(json, IntelligenceDtos.Recommendation.class); }
        catch (Exception problem) { throw new IllegalStateException("Stored recommendation is invalid", problem); }
    }

    private List<UUID> readIds(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ignored) { return List.of(); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception problem) { throw new IllegalStateException("Unable to serialize intelligence result", problem); }
    }

    private String sha256(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception problem) {
            throw new IllegalStateException("Unable to fingerprint intelligence input", problem);
        }
    }

    public static final class GenerationSuppressedException extends RuntimeException {}
}

